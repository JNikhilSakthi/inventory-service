# external-mock-service (module)

A stateless stand-in for a real third-party supplier. Deliberately unreliable — its failure rate and latency are runtime-mutable so `inventory-service`'s Resilience4j CircuitBreaker can be tripped on demand during a local demo, without restarting anything.

See the [root README](../README.md) for the full project write-up. This file covers only what's specific to this module.

## What lives here

- `SupplierController` — `GET /api/supplier/stock/{sku}`, `GET /api/supplier/price/{sku}`, `GET`/`POST /api/supplier/chaos`
- `SupplierSimulatorService` — generates fake stock/price data, injects latency (`Thread.sleep`) and randomized or forced failures
- `ChaosProperties` — `@ConfigurationProperties(prefix = "mock.chaos")`, mutable at runtime by the controller: `failureRate`, `minDelayMs`, `maxDelayMs`, `forceFailure`

## Run just this module

```bash
mvn -pl external-mock-service -am spring-boot:run
```

Default port: `8081`. Config: `src/main/resources/application.yml`.

## Drive chaos manually

```bash
# force every call to fail
curl -X POST http://localhost:8081/api/supplier/chaos -H "Content-Type: application/json" -d '{"forceFailure": true}'

# read current config
curl http://localhost:8081/api/supplier/chaos

# back to normal
curl -X POST http://localhost:8081/api/supplier/chaos -H "Content-Type: application/json" -d '{"forceFailure": false, "failureRate": 0.3}'
```

## Test

```bash
mvn -pl external-mock-service -am test
```

Includes `SupplierControllerTest` (`@WebMvcTest`: happy path, 503 on simulated outage, 400 on invalid chaos config) and `SupplierSimulatorServiceTest` (boundary behavior of the failure-rate check at 0.0/1.0/forceFailure).
