# inventory-service (module)

The resilient caller. Owns inventory data in MySQL and exposes CRUD endpoints plus a live-availability endpoint that calls out to `external-mock-service` through a full Resilience4j stack.

See the [root README](../README.md) for the full project write-up (architecture, config reference, API docs, testing, Docker, interview prep). This file covers only what's specific to this module.

## What lives here

- `InventoryController` — CRUD on `/api/inventory` + `/api/inventory/{sku}/availability`
- `InventoryService` — transactional business logic (create/update/delete/adjust quantity)
- `AvailabilityService` — combines local data with a live supplier check
- `SupplierClient` — the one method (`checkSupplierStock`) carrying all five Resilience4j annotations (`@CircuitBreaker`, `@Retry`, `@RateLimiter`, `@Bulkhead(type=THREADPOOL)`, `@TimeLimiter`), configured under the shared instance name `supplierService` in `application.yml`
- `ResilienceEventLoggingConfig` — logs CircuitBreaker state transitions (CLOSED/OPEN/HALF_OPEN)
- `InventoryItem` — the single JPA entity (`sku`, `quantityOnHand`, `reorderThreshold`, `unitPrice`, `lastRestockedAt`)

## Run just this module

```bash
# needs a MySQL instance reachable per application.yml (or override via env vars)
docker compose up mysql external-mock-service
mvn -pl inventory-service -am spring-boot:run
```

Default port: `8080`. Config: `src/main/resources/application.yml` (env-var overridable, see root README section 6).

## Test

```bash
mvn -pl inventory-service -am test
```

Includes `InventoryServiceTest` (Mockito unit test), `InventoryItemRepositoryTest` (`@DataJpaTest`/H2), `InventoryControllerTest` (`@WebMvcTest`), and `SupplierClientIntegrationTest` — a WireMock-backed `@SpringBootTest` that drives real HTTP failures/delays/500s to prove the CircuitBreaker actually opens and the Retry/TimeLimiter/fallback chain actually fires. Uses `application-test.yml` (tighter sliding windows/timeouts) so the breaker trips fast in tests.
