# inventory-service

**Inventory Service with Circuit Breaker** — a Spring Boot inventory system whose one outbound call to a third-party supplier is wrapped end-to-end in Resilience4j (CircuitBreaker + Retry + RateLimiter + ThreadPoolBulkhead + TimeLimiter), so a flaky or slow supplier degrades gracefully instead of taking the service down.

`Java 25` · `Spring Boot 4.0.6` · `Resilience4j 2.4.0` · `MySQL 8` · `Maven (multi-module)` · `Docker Compose` · `WireMock` · `JUnit 5 / Mockito`

**Learning Track:** `springboot-resilience4j-demo` (Project 12 of 17)
**Real-World Service Name:** `inventory-service`

---

## 1. Project Overview

### The problem

Any service that owns its own data (inventory, in this case) but *also* needs a real-time answer from an external system (a supplier's stock feed, a payment gateway, a pricing engine, a fraud check) has a structural weak point: the moment that external system is slow or down, the calling service is at risk of becoming slow or down too. A single blocked HTTP call, multiplied across every request thread, is how one flaky dependency takes an entire platform outage.

This project builds that exact shape on purpose. `inventory-service` owns inventory records in MySQL. When a client asks "is this SKU actually available right now?", it doesn't just look at its own database — it also calls out to `external-mock-service`, a stand-in for a real supplier, to get a live stock number. That one method call, `SupplierClient.checkSupplierStock`, is the entire point of the project.

### Why Resilience4j

Resilience4j provides five composable, independently-configurable modules that between them cover the failure modes a synchronous outbound call can hit:

- **CircuitBreaker** — once a dependency is clearly unhealthy, stop calling it. Fail fast instead of queueing up doomed requests.
- **Retry** — some failures are transient (a dropped connection, a timeout) and worth one more attempt; others (a clean HTTP 500) mean the server already made a decision and retrying just adds load.
- **RateLimiter** — protect the downstream dependency (and its rate limits) from being hammered, including by your own retries.
- **Bulkhead** (thread-pool flavor) — isolate the risky call onto its own bounded thread pool so a hung supplier can't starve the servlet container of threads needed to serve everything else.
- **TimeLimiter** — put a hard ceiling on how long any single call is allowed to take, independent of what the HTTP client's own timeout is doing.

Used individually these are useful; stacked together on one method (as they are on `SupplierClient.checkSupplierStock`), they represent the standard "resilient outbound call" pattern used throughout distributed systems.

### Where this pattern shows up in real companies

- **Netflix** — origin of Hystrix (Resilience4j's spiritual predecessor); every microservice call between the hundreds of services behind a single "play" request is circuit-broken and bulkheaded.
- **E-commerce checkout flows** (Amazon-style) — inventory/pricing/fraud-check calls during checkout are wrapped in circuit breakers so a single slow fraud-check vendor doesn't stall every checkout in the fleet.
- **Payment gateways** — Stripe/Adyen-style integrations retry only on network-level failures (never blindly retry a "card declined" or "duplicate charge" response), exactly like this project's Retry vs. CircuitBreaker split.
- **Banking core systems** — calls from a teller-facing service to a mainframe core banking system are bulkheaded onto dedicated thread pools so a slow mainframe can't take down the web tier.

## 2. Architecture

### High-Level Design (HLD)

```
                         ┌─────────────────────────┐
                         │        Client           │
                         │ (curl / Postman / UI)   │
                         └────────────┬────────────┘
                                      │ HTTP :8080
                                      ▼
                    ┌──────────────────────────────────┐
                    │        inventory-service          │
                    │  ┌────────────────────────────┐  │
                    │  │  InventoryController        │  │
                    │  │  (CRUD + /availability)      │  │
                    │  └───────────┬────────────────┘  │
                    │              │                    │
                    │   ┌──────────┴──────────┐         │
                    │   ▼                     ▼         │
                    │ InventoryService   AvailabilityService
                    │   │                     │          │
                    │   ▼                     ▼          │
                    │  MySQL            SupplierClient    │
                    │ (inventory_items)  (Resilience4j    │
                    │                     wrapped call)   │
                    └──────────────────────┬───────────────┘
                                           │ HTTP GET /api/supplier/stock/{sku}
                                           ▼
                    ┌──────────────────────────────────┐
                    │      external-mock-service         │
                    │  SupplierController                │
                    │  SupplierSimulatorService           │
                    │  (chaos-injected latency/failures,  │
                    │   runtime-mutable via /chaos)       │
                    └──────────────────────────────────┘
```

### Low-Level Design (LLD) — the resilient call path

```
InventoryController.getAvailability(sku)
        │
        ▼
AvailabilityService.checkAvailability(sku)
        │
        ├── InventoryService.getBySku(sku)   ──► MySQL (local, always fast)
        │
        └── SupplierClient.checkSupplierStock(sku)   [CompletableFuture]
                 │
                 │  annotation stack (declared once, applied together):
                 │   @CircuitBreaker(name="supplierService", fallbackMethod="fallbackCheckStock")
                 │   @RateLimiter(name="supplierService")
                 │   @Retry(name="supplierService")
                 │   @Bulkhead(name="supplierService", type=THREADPOOL)
                 │   @TimeLimiter(name="supplierService")
                 │
                 ▼
        RestTemplate.getForObject(supplierBaseUrl + "/api/supplier/stock/{sku}")
                 │
        ┌────────┴─────────┐
        ▼                  ▼
   200 OK (LIVE)      failure / timeout / breaker OPEN
        │                  │
        ▼                  ▼
 SupplierStockResponseDto  fallbackCheckStock(sku, throwable)
 (source=LIVE,             → SupplierStockResponseDto
  degraded=false)             (source=FALLBACK, degraded=true)
```

Aspect ordering matters here: Resilience4j applies these decorators from the outside in as `CircuitBreaker → RateLimiter → Retry → Bulkhead → TimeLimiter` around the actual call, meaning the CircuitBreaker sees the *final* outcome after Retry has already exhausted its attempts — one supplier outage burns through retries before it's ever counted as a single circuit-breaker failure event.

### Folder structure

```
inventory-service-parent/                 (reactor root, packaging=pom)
├── pom.xml                               parent POM: Java 25, Spring Boot 4.0.6 BOM,
│                                         Resilience4j 2.4.0 BOM, declares the two modules
├── docker-compose.yml                    mysql + external-mock-service + inventory-service
├── .gitignore
├── inventory-service/                    the resilient caller (this is the "real" service)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/medha/inventoryservice/
│       ├── config/                       RestTemplateConfig, ResilienceEventLoggingConfig
│       ├── controller/                   InventoryController
│       ├── domain/                       InventoryItem (JPA entity)
│       ├── dto/                          request/response records + DTOs
│       ├── exception/                    ResourceNotFoundException, DuplicateSkuException, GlobalExceptionHandler
│       ├── mapper/                       InventoryMapper
│       ├── repository/                  InventoryItemRepository
│       └── service/                      InventoryService, AvailabilityService, SupplierClient
└── external-mock-service/                the deliberately flaky "supplier"
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/com/medha/externalmockservice/
        ├── config/                       ChaosProperties
        ├── controller/                   SupplierController
        ├── dto/                          Chaos*, PriceResponse, StockResponse
        ├── exception/                    SupplierUnavailableException, GlobalExceptionHandler
        └── service/                      SupplierSimulatorService
```

### Request flow — `GET /api/inventory/{sku}/availability`

1. `InventoryController` receives the request and delegates to `AvailabilityService`.
2. `AvailabilityService` first loads the local record via `InventoryService.getBySku` (throws `ResourceNotFoundException` → HTTP 404 if the SKU doesn't exist locally — this happens *before* any supplier call, so a bad SKU never wastes a resilience-guarded network hop).
3. It then calls `SupplierClient.checkSupplierStock(sku)`, which returns a `CompletableFuture<SupplierStockResponseDto>` guarded by all five Resilience4j modules.
4. `AvailabilityService` blocks on that future with an outer `get(3, SECONDS)` — a second, defense-in-depth timeout independent of the TimeLimiter's own 2s budget.
5. Whatever comes back (`LIVE` data or a `FALLBACK` produced by `fallbackCheckStock`), the response is always HTTP 200 — a degraded read (local data + a `degraded: true` flag) beats a failed request.

### Database design

Single table, deliberately: this project's teaching point is the resilience wiring around the external call, not JPA relationships.

```
inventory_items
├── id                 BIGINT       PK, auto-increment
├── sku                VARCHAR(64)  UNIQUE, NOT NULL
├── product_name       VARCHAR(255) NOT NULL
├── quantity_on_hand   INT          NOT NULL
├── reorder_threshold  INT          NOT NULL
├── unit_price         DECIMAL(10,2) NOT NULL
├── last_restocked_at  TIMESTAMP
├── created_at         TIMESTAMP    NOT NULL, set on insert
└── updated_at         TIMESTAMP    NOT NULL, updated on every write
```

`ddl-auto: update` lets Hibernate manage the schema for this learning project; a production system would use Flyway/Liquibase migrations instead.

## 3. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Java 25 | Required by parent POM (`java.version` / `maven.compiler.release`) |
| Framework | Spring Boot 4.0.6 | Web, data, validation, actuator, AspectJ starters |
| Resilience | Resilience4j 2.4.0 (`resilience4j-spring-boot4`) | CircuitBreaker, Retry, RateLimiter, ThreadPoolBulkhead, TimeLimiter in one aggregator starter. `resilience4j-spring-boot3` refuses to start on a Spring Boot 4 classpath as of 2.4.0 — `resilience4j-spring-boot4` is the required replacement |
| AOP support | `spring-boot-starter-aspectj` | Required for Resilience4j's method-level annotations to be woven in (renamed from `spring-boot-starter-aop` in Spring Boot 4.0) |
| Persistence | Spring Data JPA + Hibernate 7 | `InventoryItem` CRUD |
| Database | MySQL 8.0 (`mysql-connector-j`) | inventory-service's system of record |
| Test DB | H2 (in-memory, `MODE=MySQL`) | `@DataJpaTest` without a real MySQL instance |
| HTTP client | `RestTemplate` (via `RestTemplateBuilder`, now in `spring-boot-starter-restclient`) | Outbound call to the supplier |
| Metrics | Micrometer + `micrometer-registry-prometheus` | Exposes Resilience4j metrics (state, call counts) at `/actuator/prometheus` |
| Build | Maven multi-module reactor | Shared parent POM + BOM imports across two modules |
| Containerization | Docker (multi-stage builds, JDK 25 base images) + Docker Compose | Local, one-command environment |
| Testing | JUnit 5, Mockito, `@WebMvcTest` (`spring-boot-starter-webmvc-test`), `@DataJpaTest` (`spring-boot-starter-data-jpa-test`), WireMock (`wiremock-standalone`) | Unit, slice, and true HTTP-level resilience integration tests |

> **Migrated from Spring Boot 3.3.2 / Java 21 → Spring Boot 4.0.6 / Java 25.** Beyond the
> version bumps, this required: swapping `resilience4j-spring-boot3` for
> `resilience4j-spring-boot4` (with an explicit version, since the `resilience4j-bom:2.4.0`
> doesn't yet manage that artifact's version); renaming `spring-boot-starter-aop` to
> `spring-boot-starter-aspectj`; adding the newly-split `spring-boot-starter-restclient`,
> `spring-boot-starter-webmvc-test` and `spring-boot-starter-data-jpa-test` starters (no
> longer pulled in transitively by `spring-boot-starter-web`/`spring-boot-starter-test`);
> updating `RestTemplateBuilder`'s package (`org.springframework.boot.restclient`) and its
> renamed `connectTimeout`/`readTimeout` builder methods; and replacing the removed
> `@MockBean`/`org.springframework.boot.test.mock.mockito` with
> `@MockitoBean`/`org.springframework.test.context.bean.override.mockito` in both modules'
> `@WebMvcTest` classes. Resilience4j's annotations (`@CircuitBreaker`, `@Retry`,
> `@RateLimiter`, `@Bulkhead`, `@TimeLimiter`) and `resilience4j.*` YAML properties were
> unaffected.

## 4. Configuration Explained

### `inventory-service/src/main/resources/application.yml`

**Server / datasource**
| Property | Value | Why |
|---|---|---|
| `server.port` | `8080` | inventory-service's HTTP port |
| `spring.datasource.url` | `jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:inventory_db}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC` | Every piece is env-var overridable (`${VAR:default}`) so the same jar runs unmodified locally and in docker-compose |
| `spring.datasource.username` / `password` | `${DB_USERNAME:inventory_user}` / `${DB_PASSWORD:inventory_pass}` | Matches the `mysql` service's env vars in `docker-compose.yml` |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-creates/updates the schema for this learning project |
| `spring.jpa.open-in-view` | `false` | Avoids the open-session-in-view anti-pattern; forces data access to happen inside the `@Transactional` service layer |

**Supplier connection**
| Property | Value | Why |
|---|---|---|
| `supplier.base-url` | `${SUPPLIER_BASE_URL:http://localhost:8081}` | Resolves to the `external-mock-service` container name inside docker-compose |
| `supplier.connect-timeout-ms` | `${SUPPLIER_CONNECT_TIMEOUT_MS:1000}` | Bounds how long establishing the TCP connection can take, layered *underneath* Resilience4j's own TimeLimiter |
| `supplier.read-timeout-ms` | `${SUPPLIER_READ_TIMEOUT_MS:2000}` | Bounds a single HTTP attempt's read time; the TimeLimiter separately bounds the whole async call including retries |

**`resilience4j.circuitbreaker.instances.supplierService`**
| Property | Value | Why |
|---|---|---|
| `registerHealthIndicator` | `true` | Surfaces breaker state on `/actuator/health` |
| `slidingWindowType` | `COUNT_BASED` | Evaluates the failure rate over the last N *calls*, not a time window — simpler to reason about and to demo |
| `slidingWindowSize` | `10` | Window of the last 10 calls |
| `minimumNumberOfCalls` | `5` | Breaker won't make an OPEN/CLOSED decision until at least 5 calls have been recorded, avoiding false trips on a cold start |
| `failureRateThreshold` | `50` (%) | Opens once half the calls in the window fail |
| `slowCallRateThreshold` / `slowCallDurationThreshold` | `80` / `2s` | Calls slower than 2s also count toward tripping the breaker, not just outright failures |
| `waitDurationInOpenState` | `10s` | How long the breaker stays OPEN before probing again — short on purpose, for a fast local demo (production would tune this much higher) |
| `permittedNumberOfCallsInHalfOpenState` | `3` | Number of trial calls allowed through while HALF_OPEN before deciding CLOSED vs. OPEN again |
| `automaticTransitionFromOpenToHalfOpenStateEnabled` | `true` | Breaker self-transitions to HALF_OPEN on the timer instead of waiting for the next incoming call |
| `recordExceptions` | `ResourceAccessException`, `HttpServerErrorException`, `TimeoutException`, `SupplierClientException` | Only these count as CircuitBreaker failures — a 4xx from the client's own bad input never should |

**`resilience4j.retry.instances.supplierService`**
| Property | Value | Why |
|---|---|---|
| `maxAttempts` | `3` | Original call + 2 retries |
| `waitDuration` / `enableExponentialBackoff` / `exponentialBackoffMultiplier` | `300ms` / `true` / `2` | 300ms → 600ms → 1200ms backoff between attempts |
| `retryExceptions` | `ResourceAccessException`, `TimeoutException` | **Only transient/network-shaped failures are retried.** A clean HTTP 500 is *not* in this list — see the teaching note below |
| `ignoreExceptions` | `ResourceNotFoundException` | Never retry a "this doesn't exist" business exception |

> **Teaching distinction:** a `ResourceAccessException` (connection refused/reset, DNS failure) or `TimeoutException` means the request may never have reached the server — safe to retry. A plain 5xx from `HttpServerErrorException` means the server *did* receive the request and already made a decision — retrying blindly could pile more load onto an already-struggling supplier, so it's recorded straight to the CircuitBreaker instead of being retried.

**`resilience4j.ratelimiter.instances.supplierService`**
| Property | Value | Why |
|---|---|---|
| `limitForPeriod` | `20` | At most 20 calls allowed per period |
| `limitRefreshPeriod` | `1s` | → effectively 20 calls/sec |
| `timeoutDuration` | `0s` | A call that can't get a permit immediately fails immediately rather than queueing |

**`resilience4j.thread-pool-bulkhead.instances.supplierService`**
| Property | Value | Why |
|---|---|---|
| `coreThreadPoolSize` / `maxThreadPoolSize` | `5` / `10` | Dedicated pool for supplier calls, sized small on purpose so the isolation is visible |
| `queueCapacity` | `20` | Bounded queue — beyond this, calls are rejected rather than piling up unboundedly |
| `keepAliveDuration` | `20ms` | How long idle threads above the core size linger before being reclaimed |

**`resilience4j.timelimiter.instances.supplierService`**
| Property | Value | Why |
|---|---|---|
| `timeoutDuration` | `2s` | Hard ceiling on the whole `CompletableFuture`, independent of the RestTemplate read timeout |
| `cancelRunningFuture` | `true` | Actively cancels/interrupts the in-flight call once the timeout fires, instead of letting it run to completion in the background |

**Actuator / metrics**
| Property | Value | Why |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,circuitbreakers,circuitbreakerevents,retries,ratelimiters,bulkheads,metrics,prometheus` | Exposes every Resilience4j-specific actuator endpoint for demo/observability |
| `management.health.circuitbreakers.enabled` | `true` | Breaker state rolls up into overall `/actuator/health` |
| `logging.level.io.github.resilience4j` | `DEBUG` | Verbose Resilience4j internals while learning; would be dialed back in production |

### `inventory-service/src/test/resources/application-test.yml`

A **tighter** copy of the production config, used only under the `test` profile, so integration tests trip the breaker/timeouts quickly instead of waiting on production-sized durations:
- H2 in-memory datasource (`MODE=MySQL`, `ddl-auto: create-drop`)
- `supplier.base-url` overridden at test time via `@DynamicPropertySource` to WireMock's dynamic port
- `slidingWindowSize`/`minimumNumberOfCalls` dropped to `4`, `waitDurationInOpenState` to `2s`
- `retry.maxAttempts` dropped to `2`, `waitDuration` to `50ms`
- `timelimiter.timeoutDuration` dropped to `500ms`

### `external-mock-service/src/main/resources/application.yml`

| Property | Value | Why |
|---|---|---|
| `server.port` | `8081` | external-mock-service's HTTP port |
| `mock.chaos.failure-rate` | `${MOCK_CHAOS_FAILURE_RATE:0.3}` | Probability (0.0–1.0) any call fails with a simulated 503 |
| `mock.chaos.min-delay-ms` / `max-delay-ms` | `${MOCK_CHAOS_MIN_DELAY_MS:50}` / `${MOCK_CHAOS_MAX_DELAY_MS:800}` | Artificial latency range injected into every response |
| `mock.chaos.force-failure` | `${MOCK_CHAOS_FORCE_FAILURE:false}` | When `true`, every call fails regardless of `failure-rate` — a manual "kill switch" for demos |
| `management.endpoints.web.exposure.include` | `health,info` | Minimal actuator surface; this service isn't the one being taught |

These four `mock.chaos.*` values are also mutable **at runtime** through `POST /api/supplier/chaos` (backed by `ChaosProperties`, a `@ConfigurationProperties`-bound `@Component`), which is what lets a demo trip inventory-service's circuit breaker on demand without restarting anything.

## 5. Project Structure Explained

| Path | Purpose |
|---|---|
| `pom.xml` (root) | Parent POM, `packaging=pom`. Pins Java 25, imports the Spring Boot and Resilience4j BOMs, declares the two reactor modules. Nothing here is deployable by itself. |
| `docker-compose.yml` | Wires `mysql` + `external-mock-service` + `inventory-service` together with health-check-gated `depends_on` so inventory-service doesn't start racing against a MySQL that isn't ready yet. |
| `.gitignore` | Standard Maven/IDE ignores (`target/`, `.idea/`, etc.) |
| `inventory-service/` | The service under test/study — owns data, makes the resilience-guarded outbound call. |
| `inventory-service/pom.xml` | Module POM: web, restclient, data-jpa, validation, actuator, AspectJ (required for Resilience4j annotations to be woven in), MySQL driver, `resilience4j-spring-boot4`, Micrometer Prometheus registry, and test-scoped H2 + `spring-boot-starter-webmvc-test`/`spring-boot-starter-data-jpa-test` + `wiremock-standalone`. |
| `inventory-service/Dockerfile` | Multi-stage build: Maven+JDK25 image builds the jar (`-pl inventory-service -am`, reactor-aware), then copies just the jar into a slim `eclipse-temurin:25-jre-alpine` runtime image running as a non-root `spring` user. |
| `.../config/RestTemplateConfig.java` | Builds the shared `RestTemplate` bean with explicit connect/read timeouts, layered underneath the Resilience4j TimeLimiter. |
| `.../config/ResilienceEventLoggingConfig.java` | Registers a `RegistryEventConsumer<CircuitBreaker>` bean; Resilience4j's starter auto-detects it and wires a listener onto every CircuitBreaker instance that logs CLOSED/OPEN/HALF_OPEN transitions. |
| `.../controller/InventoryController.java` | REST surface: CRUD on inventory items plus the `/availability` endpoint that triggers the supplier call. |
| `.../domain/InventoryItem.java` | The single JPA entity: `sku` (unique), `quantityOnHand`, `reorderThreshold`, `unitPrice`, `lastRestockedAt`, plus `createdAt`/`updatedAt` lifecycle timestamps. |
| `.../dto/*` | Request/response records — `InventoryItemRequest/Response`, `StockAdjustmentRequest`, `AvailabilityResponse`, `SupplierStockPayload` (raw wire shape from the supplier) vs. `SupplierStockResponseDto` (adds local `source`/`degraded`/`message` metadata), `ErrorResponse`. |
| `.../exception/*` | `ResourceNotFoundException`, `DuplicateSkuException`, and a `GlobalExceptionHandler` mapping each to the right HTTP status with a uniform `ErrorResponse` body. |
| `.../mapper/InventoryMapper.java` | Pure functions between `InventoryItem` entity and its request/response DTOs — including the derived `belowReorderThreshold` flag. |
| `.../repository/InventoryItemRepository.java` | Spring Data JPA repository: `findBySku`, `existsBySku`. |
| `.../service/InventoryService.java` | CRUD + stock-adjustment business logic; `@Transactional` at the class level, read-only on query methods. |
| `.../service/AvailabilityService.java` | Combines local data with a live supplier check; catches any exception from the guarded call as a last line of defense and always returns 200. |
| `.../service/SupplierClient.java` | **The centerpiece.** One method, `checkSupplierStock`, carrying all five Resilience4j annotations plus its `fallbackCheckStock` method. |
| `.../service/SupplierClientException.java` | Wraps unexpected supplier responses (e.g. empty body) so they participate in the CircuitBreaker's `recordExceptions` list like any other failure. |
| `external-mock-service/` | Stateless chaos-injection simulator standing in for a real third-party supplier. |
| `external-mock-service/pom.xml` | Module POM: web, validation, actuator, test starter — deliberately minimal, no database. |
| `external-mock-service/Dockerfile` | Same multi-stage pattern as inventory-service's, targeting port 8081. |
| `.../config/ChaosProperties.java` | `@ConfigurationProperties(prefix="mock.chaos")` bean holding `failureRate`, `minDelayMs`, `maxDelayMs`, `forceFailure` — mutable at runtime by the controller. |
| `.../controller/SupplierController.java` | `GET /api/supplier/stock/{sku}`, `GET /api/supplier/price/{sku}`, and `GET`/`POST /api/supplier/chaos` to read/mutate the chaos config. |
| `.../dto/*` | `StockResponse`, `PriceResponse`, `ChaosConfigRequest` (partial-update payload, every field optional), `ChaosConfigResponse`. |
| `.../exception/*` | `SupplierUnavailableException` (simulated outage) + `GlobalExceptionHandler` mapping it to HTTP 503. |
| `.../service/SupplierSimulatorService.java` | Generates deterministic-enough fake stock/price data, injects latency (`Thread.sleep`) and randomized/forced failures based on `ChaosProperties`. |

## 6. Getting Started

### Prerequisites

- Docker and Docker Compose
- (For local, non-Docker development) JDK 25 and Maven 3.9+

### Run everything with Docker Compose

```bash
# from the repository root
docker compose up --build
```

This builds both service images (using the multi-stage Dockerfiles) and starts, in order:
1. `mysql` — waits until `mysqladmin ping` succeeds (health-checked)
2. `external-mock-service` — starts once MySQL's healthcheck passes the compose dependency gate
3. `inventory-service` — starts only after both `mysql` is healthy and `external-mock-service` has started; connects to MySQL via `DB_HOST=mysql` and to the supplier via `SUPPLIER_BASE_URL=http://external-mock-service:8081`

Once it's up:
- inventory-service: `http://localhost:8080`
- external-mock-service: `http://localhost:8081`
- MySQL: `localhost:3306` (`inventory_db` / `inventory_user` / `inventory_pass`)

Stop everything with:
```bash
docker compose down          # stop containers, keep the mysql_data volume
docker compose down -v       # stop and wipe the MySQL volume too
```

### Run locally without Docker

```bash
# start only MySQL via compose, or point at your own instance
docker compose up mysql

# terminal 1 — the supplier
mvn -pl external-mock-service -am spring-boot:run

# terminal 2 — the resilient caller
mvn -pl inventory-service -am spring-boot:run
```

### Demo: trip the circuit breaker on purpose

```bash
# force every supplier call to fail
curl -X POST http://localhost:8081/api/supplier/chaos \
  -H "Content-Type: application/json" \
  -d '{"forceFailure": true}'

# hit availability a handful of times — watch inventory-service logs for
# "CircuitBreaker 'supplierService' changed state: CLOSED -> OPEN"
for i in $(seq 1 8); do curl -s http://localhost:8080/api/inventory/SKU-1/availability; echo; done

# check breaker state directly
curl http://localhost:8080/actuator/circuitbreakers

# turn chaos back off
curl -X POST http://localhost:8081/api/supplier/chaos \
  -H "Content-Type: application/json" \
  -d '{"forceFailure": false}'
```

## 7. API Documentation

### inventory-service — `http://localhost:8080`

| Method | Path | Description |
|---|---|---|
| GET | `/api/inventory` | List all inventory items |
| GET | `/api/inventory/{sku}` | Get one item by SKU (404 if missing) |
| POST | `/api/inventory` | Create an item (409 if SKU already exists) |
| PUT | `/api/inventory/{sku}` | Update an existing item |
| DELETE | `/api/inventory/{sku}` | Delete an item |
| POST | `/api/inventory/{sku}/adjust` | Adjust `quantityOnHand` by a signed delta |
| GET | `/api/inventory/{sku}/availability` | Local inventory + live (or fallback) supplier stock check |

**`POST /api/inventory`**
```json
// Request
{
  "sku": "SKU-1001",
  "productName": "Widget",
  "quantityOnHand": 200,
  "reorderThreshold": 25,
  "unitPrice": 9.99
}

// Response  201 Created
{
  "id": 1,
  "sku": "SKU-1001",
  "productName": "Widget",
  "quantityOnHand": 200,
  "reorderThreshold": 25,
  "unitPrice": 9.99,
  "belowReorderThreshold": false,
  "lastRestockedAt": "2026-07-22T10:00:00Z",
  "createdAt": "2026-07-22T10:00:00Z",
  "updatedAt": "2026-07-22T10:00:00Z"
}
```

**`POST /api/inventory/{sku}/adjust`**
```json
// Request
{ "delta": -15, "reason": "sold" }

// Response  200 OK
{ "id": 1, "sku": "SKU-1001", "quantityOnHand": 185, "...": "..." }
```

**`GET /api/inventory/SKU-1001/availability`** (supplier healthy)
```json
{
  "inventoryItem": { "sku": "SKU-1001", "quantityOnHand": 185, "belowReorderThreshold": false, "...": "..." },
  "supplierStock": {
    "sku": "SKU-1001",
    "availableUnits": 62,
    "leadTimeDays": 3,
    "source": "LIVE",
    "degraded": false,
    "message": "Live data from supplier"
  },
  "checkedAt": "2026-07-22T10:05:00Z"
}
```

**`GET /api/inventory/SKU-1001/availability`** (supplier down / breaker OPEN)
```json
{
  "inventoryItem": { "sku": "SKU-1001", "quantityOnHand": 185, "...": "..." },
  "supplierStock": {
    "sku": "SKU-1001",
    "availableUnits": 0,
    "leadTimeDays": -1,
    "source": "FALLBACK",
    "degraded": true,
    "message": "Supplier unavailable (CallNotPermittedException), showing local inventory only"
  },
  "checkedAt": "2026-07-22T10:05:03Z"
}
```

**Error shape** (e.g. `GET` on a missing SKU → 404)
```json
{
  "timestamp": "2026-07-22T10:05:03Z",
  "status": 404,
  "error": "Not Found",
  "message": "Inventory item not found for sku: SKU-9999",
  "path": "/api/inventory/SKU-9999",
  "fieldErrors": null
}
```

### external-mock-service — `http://localhost:8081`

| Method | Path | Description |
|---|---|---|
| GET | `/api/supplier/stock/{sku}` | Simulated stock lookup (subject to chaos config) |
| GET | `/api/supplier/price/{sku}` | Simulated price lookup (subject to chaos config) |
| GET | `/api/supplier/chaos` | Read current chaos configuration |
| POST | `/api/supplier/chaos` | Partially update chaos configuration (any subset of fields) |

```json
// POST /api/supplier/chaos
{ "failureRate": 0.8, "minDelayMs": 100, "maxDelayMs": 3000 }

// 200 OK
{ "failureRate": 0.8, "minDelayMs": 100, "maxDelayMs": 3000, "forceFailure": false }
```

### Actuator (inventory-service)

| Path | What it shows |
|---|---|
| `/actuator/health` | Overall health, including circuit breaker health indicator |
| `/actuator/circuitbreakers` | Current state (`CLOSED`/`OPEN`/`HALF_OPEN`) of the `supplierService` breaker |
| `/actuator/circuitbreakerevents` | Recent breaker events (calls, transitions) |
| `/actuator/retries`, `/actuator/ratelimiters`, `/actuator/bulkheads` | State of the other Resilience4j instances |
| `/actuator/metrics`, `/actuator/prometheus` | Micrometer metrics, including `resilience4j.*` series |

## 8. Testing

```bash
# run every test in the reactor
mvn test

# just one module
mvn -pl inventory-service -am test
mvn -pl external-mock-service -am test
```

| Test class | Type | What it proves |
|---|---|---|
| `InventoryServiceTest` | JUnit5 + Mockito unit test | Business rules in isolation: duplicate-SKU rejection, not-found handling, quantity adjustment math, negative-quantity guard — repository fully mocked |
| `InventoryItemRepositoryTest` | `@DataJpaTest` (H2) | `findBySku` / `existsBySku` actually hit a real (in-memory) database correctly |
| `InventoryControllerTest` | `@WebMvcTest` | HTTP layer: validation errors → 400, not-found → 404, created → 201, and that a degraded availability response still serializes correctly |
| `SupplierControllerTest` (external-mock-service) | `@WebMvcTest` | Stock endpoint happy path, 503 on simulated outage, 400 on out-of-range chaos config, valid chaos update applied |
| `SupplierSimulatorServiceTest` (external-mock-service) | Plain unit test | Boundary behavior of the failure-rate check: `0.0` never fails, `1.0` always fails, `forceFailure` always fails regardless of rate |
| **`SupplierClientIntegrationTest`** | `@SpringBootTest` + WireMock (`wiremock-standalone`) | **The centerpiece test.** Drives real HTTP responses (200, 500, 3s delay) at a real WireMock server standing in for the supplier, and asserts on the actual `SupplierClient` behavior end-to-end: live data comes back correctly, a repeated 500 produces a `FALLBACK` response, a slow response trips the TimeLimiter into a fallback, and — driving 10 failing calls — the `CircuitBreakerRegistry` reports the `supplierService` breaker's state as `OPEN` |

`SupplierClientIntegrationTest` runs under the `test` Spring profile, which loads `application-test.yml` — a tightened resilience config (see section 6) so the breaker/timeouts trip within the test's timeout budget instead of the production-sized 10s/2s windows.

> Verified locally on JDK 25.0.3 / Maven 3.9.16: `mvn test` passes all 25 tests across both
> modules (17 in inventory-service, 8 in external-mock-service) with no extra flags needed.

## 9. Docker

### `inventory-service/Dockerfile` and `external-mock-service/Dockerfile`

Both follow the identical two-stage pattern:

1. **Build stage** (`maven:3.9.16-eclipse-temurin-25`) — copies the root `pom.xml` and both modules' `pom.xml` files first (so `dependency:go-offline` can populate the local repo as its own cached Docker layer before any source changes invalidate it), then copies that module's `src/` and runs `mvn -pl <module> -am package -DskipTests` — the `-am` flag makes Maven also build the parent reactor context each module needs.
2. **Runtime stage** (`eclipse-temurin:25-jre-alpine`) — a minimal JRE-only image. Creates and switches to a non-root `spring` user before copying in just the built jar and setting the `ENTRYPOINT`. Nothing from the Maven build toolchain ships in the final image.

### `docker-compose.yml`

| Service | Image/build | Key config |
|---|---|---|
| `mysql` | `mysql:8.0` | Creates `inventory_db` / `inventory_user` on first boot via env vars; persists data in the `mysql_data` named volume; healthcheck via `mysqladmin ping` (10s interval, 10 retries) |
| `external-mock-service` | built from `external-mock-service/Dockerfile` | Chaos knobs (`MOCK_CHAOS_*`) injected as env vars matching `application.yml`'s `${VAR:default}` placeholders; exposed on `8081` |
| `inventory-service` | built from `inventory-service/Dockerfile` | `depends_on: mysql (condition: service_healthy)` and `external-mock-service (condition: service_started)` — it will not start until MySQL has actually passed its healthcheck, avoiding the classic "app started before DB was ready" race; `SUPPLIER_BASE_URL` points at the compose-internal DNS name of the other container |

## 10. Interview Preparation

**Q: Why does `checkSupplierStock` return `CompletableFuture` instead of the DTO directly?**
Both `@Bulkhead(type = THREADPOOL)` and `@TimeLimiter` operate on asynchronous execution — the thread-pool bulkhead needs to actually dispatch the call onto its own executor, and the TimeLimiter needs a future it can time out on. A synchronous return type is incompatible with either annotation.

**Q: What order do the Resilience4j annotations execute in, and why does it matter?**
Resilience4j applies decorators from the outside in: CircuitBreaker → RateLimiter → Retry → Bulkhead → TimeLimiter, wrapping the raw call. In practice this means Retry re-attempts the *inner* call multiple times before the CircuitBreaker ever records a single failure/success for the whole invocation — so a "3 retries then give up" scenario counts as exactly one CircuitBreaker data point, not three. Get the annotation order right (or configure explicitly if your version requires it) or a supplier outage will trip the breaker far slower or faster than intended.

**Q: Why is a plain HTTP 500 *not* retried, while a `ResourceAccessException`/timeout is?**
A 500 means the request reached the server and the server made a decision — retrying it blindly adds load to an already-failing dependency and can make an outage worse (retry storms). A `ResourceAccessException` (connection refused/reset) or timeout means the request may never have been processed at all, so a retry is safe and often succeeds. This project encodes that distinction directly in `retryExceptions` vs. `recordExceptions`.

**Q: What's the difference between the RestTemplate's read timeout and the Resilience4j TimeLimiter?**
The RestTemplate timeout (`supplier.read-timeout-ms`, 2000ms here) bounds a *single* HTTP attempt. The TimeLimiter (`timelimiter.instances.supplierService.timeoutDuration`, also 2s here) bounds the *entire* guarded call, including all retry attempts — so with `maxAttempts=3` and backoff, the sum of attempts could exceed a single read-timeout, and the TimeLimiter is the outer backstop that guarantees the caller never waits indefinitely.

**Q: Why bulkhead with a dedicated thread pool instead of relying on the servlet container's own thread pool?**
If supplier calls ran on the same threads serving all other requests, a hung supplier would eventually consume every request-handling thread in the container (thread starvation) — even requests that have nothing to do with the supplier would start failing. A dedicated, bounded pool (5–10 threads here) contains the blast radius to just the supplier-calling code path.

**Q: What happens to a request when the CircuitBreaker is OPEN?**
The call to the guarded method never reaches `RestTemplate` at all — Resilience4j throws a `CallNotPermittedException` immediately and routes straight to `fallbackCheckStock`, which is by design near-instant (no network round trip, no timeout to wait out). This is the "fail fast" benefit of the breaker.

**Q: Why `minimumNumberOfCalls` separate from `slidingWindowSize`?**
Without it, a single failed call out of one recorded call would be a 100% failure rate and could trip the breaker on a cold start with almost no signal. `minimumNumberOfCalls: 5` means the breaker waits for at least 5 data points in the window before it will make an OPEN/CLOSED decision.

**Common mistakes with Resilience4j:**
- Configuring Retry to retry *every* exception, including 4xx/5xx business errors — causing retry storms against an already-struggling dependency.
- Forgetting `spring-boot-starter-aspectj` (renamed from `spring-boot-starter-aop` in Spring Boot 4.0) — the `@CircuitBreaker`/`@Retry`/etc. annotations are silently ignored without AOP proxying enabled.
- Using the semaphore-based `@Bulkhead` when the intent was thread isolation — semaphore bulkheads limit *concurrency* but still run on the caller's thread, so a hung call still occupies a request thread. `type = THREADPOOL` is required for true isolation.
- Mismatched fallback method signature — Resilience4j resolves the fallback by matching parameter types plus a trailing `Throwable`; a subtle mismatch fails silently at startup or throws at runtime.
- Setting `waitDurationInOpenState` too short for production (this project uses 10s deliberately for fast local demos) — in production, a value that's too short means retrying a dependency that's still down, defeating the purpose of the breaker.
- Not testing the resilience configuration at all — unit tests that mock the HTTP client directly never exercise the real annotation stack; this project's `SupplierClientIntegrationTest` against real WireMock HTTP responses is what actually proves the wiring works.

**Production considerations:**
- Tune `slidingWindowSize`, `waitDurationInOpenState`, and rate limits based on real traffic volume and the downstream SLA — this project's values (10s open-wait, 2s slow-call threshold) are chosen for a demonstrable local demo, not production scale.
- Emit and alert on CircuitBreaker state transitions (this project logs them via `ResilienceEventLoggingConfig`) — an OPEN breaker in production is an incident-worthy signal, not just a log line.
- Consider a distributed rate limiter (e.g. Redis-backed) if running multiple instances of inventory-service, since Resilience4j's in-process `RateLimiter` only limits calls from a single JVM.
- Decide deliberately what a fallback *means* to your business — here it's "show local data, flag as degraded," which is fine for a read; a write-side fallback (e.g. "reserve stock") needs a very different, often async, compensating strategy.

**Performance notes:**
- The thread-pool bulkhead adds a context switch (dispatch onto its own executor) compared to a semaphore bulkhead, but that's the cost of true isolation — acceptable for an I/O-bound external call.
- `slidingWindowType: COUNT_BASED` is O(1) per call to update; a `TIME_BASED` window has slightly higher bookkeeping overhead but reacts better to bursty traffic patterns.
- Keep the Retry backoff bounded (exponential with a low base, as here — 300ms/600ms/1200ms) so total added latency on a genuinely failing call stays predictable and doesn't itself become a source of timeouts elsewhere in the call chain.

## License

MIT — see [LICENSE](LICENSE).
