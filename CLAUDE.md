# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with this repository.

---

## Project Overview

This is a **Spring Boot 4.1.0** microservice for IoT device monitoring. It ingests device metric readings, evaluates configurable alert rules, triggers and auto-closes alert instances, and delivers webhook notifications via a transactional outbox pattern.

- **Group**: `com.example`
- **Artifact**: `demo`
- **Version**: `0.0.1-SNAPSHOT`
- **Root package**: `com.example.demo`

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 25 (via Gradle toolchain) |
| Spring Boot | 4.1.0 |
| Spring Web (REST) | Managed by Spring Boot BOM |
| Spring Cloud OpenFeign | 2025.1.2 (via Spring Cloud BOM) |
| Spring Data JPA | Managed by Spring Boot BOM |
| Hibernate ORM | Managed by Spring Boot BOM |
| Flyway | Managed by Spring Boot BOM (`flyway-core` + `flyway-database-postgresql` explicitly declared) |
| MapStruct | 1.6.3 (explicitly pinned; `lombok-mapstruct-binding:0.2.0` ensures correct annotation-processor order) |
| Jackson | 3.x (`tools.jackson.*` — Boot 4.x default) |
| H2 (in-memory DB, local/test only) | Managed by Spring Boot BOM |
| PostgreSQL (default/prod) | Managed by Spring Boot BOM |
| Bean Validation (Hibernate Validator) | Managed by Spring Boot BOM |
| Gradle | 9.5.1 (via wrapper) |
| JUnit 5 (Jupiter) + Mockito | Managed by Spring Boot BOM |

---

## Build & Run Commands

```bash
# Run with Docker (postgres + app together — recommended)
docker compose up --build

# Run locally against H2 (no Docker needed)
./gradlew bootRun --args='--spring.profiles.active=local'   # Linux/macOS
gradlew.bat bootRun --args='--spring.profiles.active=local' # Windows

# Run tests
./gradlew test

# Build executable JAR (output: build/libs/demo-0.0.1-SNAPSHOT.jar)
./gradlew bootJar

# Clean build artifacts
./gradlew clean
```

---

## Project Structure

```
.
├── Dockerfile                              # Multi-stage build: Gradle → slim JRE image
├── docker-compose.yml                      # postgres:17-alpine + app services
├── .env                                    # Local docker-compose defaults (gitignored)
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── DemoApplication.java                    # Spring Boot entry point (@SpringBootApplication)
    │   │   ├── config/
    │   │   │   ├── JsonAttributeConverter.java         # JPA converter: Map<String,String> ↔ JSON TEXT
    │   │   │   ├── SchedulingConfig.java               # @EnableScheduling
    │   │   │   └── WebhookClientConfig.java            # @Bean AlertNotificationClient (Feign) bound to alarm.webhook.url
    │   │   ├── client/
    │   │   │   └── AlertNotificationClient.java        # Feign interface: POST AlertNotificationPayload to webhook URL
    │   │   ├── controller/
    │   │   │   ├── DeviceController.java               # CRUD /api/devices + activate/deactivate
    │   │   │   ├── MetricController.java               # POST /api/metrics → 204 No Content
    │   │   │   ├── AlertController.java                # GET /api/alerts (paged), GET /api/alerts/{id}
    │   │   │   ├── AlertRuleController.java            # CRUD /api/alert-rules + enable/disable
    │   │   │   └── GlobalExceptionHandler.java         # @RestControllerAdvice: 404 / 409 / 400 error mapping
    │   │   ├── service/
    │   │   │   ├── DeviceService.java                  # Device lifecycle: register, list (paged), get, update, activate, deactivate; findOrThrow / findActiveOrThrow (public)
    │   │   │   ├── MetricService.java                  # Ingest metric, evaluate rules, open/close alerts, write outbox (uses OutboxEventFactory)
    │   │   │   ├── AlertService.java                   # Alert queries: listAll (paged), getById — both @Transactional(readOnly=true)
    │   │   │   ├── AlertRuleService.java               # Alert rule lifecycle: create, list, get, update, enable, disable, delete
    │                   │   │   ├── OutboxScheduler.java                # @Scheduled: partition-based ordered delivery via FOR UPDATE SKIP LOCKED; fail-fast per partition
    │   │   │   └── OutboxEventFactory.java             # @Component factory: builds OutboxEvent instances (injected into MetricService)
    │   │   ├── repository/
    │   │   │   ├── DeviceRepository.java               # JpaRepository<Device, Long>
    │   │   │   ├── MetricRepository.java               # JpaRepository<Metric, Long>
    │   │   │   ├── AlertRepository.java                # + findByRuleIdInAndStatusWithDevice (JOIN FETCH), findAllWithDeviceAndRule (paged JOIN FETCH), findByIdWithDeviceAndRule (JOIN FETCH)
    │   │   │   ├── AlertRuleRepository.java            # + findByDeviceIdAndMetricNameAndEnabledTrue
    │                   │   │   └── OutboxEventRepository.java          # + claimPartitionSentinels (@Lock PESSIMISTIC_WRITE + SKIP LOCKED), findAllPendingByPartition
    │   │   ├── exception/
    │   │   │   └── DeviceUnactivatedException.java     # RuntimeException → HTTP 409 Conflict (inactive device rejects metric ingestion)
    │   │   ├── model/
    │   │   │   ├── Device.java                         # @Entity: id, name, type, location, extraAttributes, status
    │   │   │   ├── Metric.java                         # @Entity: id, device, metricName, value, timestamp
    │   │   │   ├── Alert.java                          # @Entity: id, device, rule, metricName (denorm), triggerValue, severity, status, openedAt, closedAt
    │   │   │   ├── AlertRule.java                      # @Entity: id, device, metricName, operator, threshold, severity, enabled
    │                   │   │   ├── OutboxEvent.java                    # @Entity: fully denormalized alert snapshot (no FK to Alert) + deviceId (partition key) + OutboxStatus (PENDING/SENT/FAILED) + delivery metadata
    │   │   │   ├── AlertStatus.java                    # Enum: OPEN, CLOSED
    │   │   │   ├── AlertSeverity.java                  # Enum: LOW, MEDIUM, HIGH, CRITICAL
    │   │   │   ├── AlertOperator.java                  # Enum: GREATER_THAN, LESS_THAN, EQUALS
    │   │   │   ├── DeviceStatus.java                   # Enum: ACTIVE, INACTIVE
    │   │   │   └── DeviceType.java                     # Enum: SENSOR, GATEWAY, CONTROLLER
    │   │   ├── mapper/
    │   │   │   ├── DeviceMapper.java                   # MapStruct: CreateDeviceRequest/UpdateDeviceRequest ↔ Device, Device → DeviceResponse
    │   │   │   └── AlertRuleMapper.java                # MapStruct: CreateAlertRuleRequest → AlertRule, AlertRule → AlertRuleResponse (device.id + device.name flattened), partial UpdateAlertRuleRequest patch
    │   │   └── dto/
    │   │       ├── CreateDeviceRequest.java            # record: name, type, location, extraAttributes (validated)
    │   │       ├── UpdateDeviceRequest.java            # record: nullable name, type, location, extraAttributes (patch)
    │   │       ├── DeviceResponse.java                 # record: id, name, type, location, extraAttributes, status
    │   │       ├── DevicePageRequest.java              # @Data class: page, size, sortBy, sortDir + toPageable()
    │   │       ├── AlertPageRequest.java               # @Data class: same structure as DevicePageRequest; defaults sortBy=openedAt, sortDir=desc
    │   │       ├── PagedResponse.java                  # generic record: content, page, size, totalElements, totalPages
    │   │       ├── CreateMetricRequest.java            # record: deviceId, metricName, value, timestamp — all @NotNull (validated)
    │   │       ├── CreateAlertRuleRequest.java         # record: deviceId, metricName, operator, threshold, severity, enabled (validated)
    │   │       ├── UpdateAlertRuleRequest.java         # record: nullable fields for partial alert rule update
    │   │       ├── AlertRuleResponse.java              # record: rule fields + deviceId + deviceName (pure data carrier — no static factory)
    │   │       ├── AlertInstanceResponse.java          # record: alert fields + device name; static from(Alert)
    │   │       └── AlertNotificationPayload.java       # record: webhook POST body (deviceName, metricName, triggerValue, severity, alertStatus, alertOpenedAt, alertClosedAt); static from(OutboxEvent)
    │   └── resources/
    │       ├── application.properties                  # Default/prod config: PostgreSQL via env vars, Flyway enabled, ddl-auto=none
    │       ├── application-local.properties            # Local dev overrides: H2, Flyway disabled, create-drop DDL, SQL logging
    │       └── db/
    │           └── migration/
    │               └── V1__init_schema.sql             # Initial DDL for all 5 tables (PostgreSQL)
    └── test/
        ├── java/com/example/demo/
        │   ├── DemoApplicationTests.java               # Context-load smoke test
        │   ├── controller/
        │   │   ├── DeviceControllerTest.java           # @SpringBootTest + MockMvc: CRUD + pagination + validation + activate/deactivate
        │   │   ├── MetricControllerTest.java           # @SpringBootTest + MockMvc: ingest, alert open/close, validation
        │   │   ├── AlertControllerTest.java            # @SpringBootTest + MockMvc: list (paginated), get, 404
        │   │   └── AlertRuleControllerTest.java        # @SpringBootTest + MockMvc: full CRUD + enable/disable + validation
        │   ├── mapper/
        │   │   ├── AlertRuleMapperTest.java            # Mappers.getMapper unit tests: toRule, toResponse, updateRule (partial patch)
        │   │   └── DeviceMapperTest.java               # Mappers.getMapper unit tests: toDevice, toResponse, updateDevice, null extraAttributes default
        │   └── service/
        │       ├── DeviceServiceTest.java              # Mockito unit tests: register, list (paged), get, update, deactivate, findActiveOrThrow
        │       ├── MetricServiceTest.java              # Mockito unit tests: all operators, alert lifecycle, inactive device, timestamp forwarding
        │       ├── AlertRuleServiceTest.java           # Mockito unit tests: create, list, get, update, enable, disable, delete, 404 paths
        │       └── OutboxSchedulerTest.java            # Mockito unit tests: retry logic, max attempts, partition ordering, fail-fast per partition, cross-partition independence, cluster-safety (SKIP LOCKED)
        └── resources/
            └── application.properties                  # Test overrides: H2 (testdb), Flyway disabled, create-drop DDL
```

---

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/devices` | Register a new device → 201 Created |
| `GET` | `/api/devices` | List devices (paginated) → 200 OK |
| `GET` | `/api/devices/{id}` | Get device by ID → 200 OK / 404 |
| `PUT` | `/api/devices/{id}` | Partial update device → 200 OK / 404 |
| `PUT` | `/api/devices/{id}/activate` | Activate device → 204 No Content / 404 |
| `PUT` | `/api/devices/{id}/deactivate` | Deactivate device → 204 No Content / 404 |
| `POST` | `/api/metrics` | Ingest a metric reading → 204 No Content |
| `GET` | `/api/alerts` | List all alerts (paginated, newest first) → 200 OK |
| `GET` | `/api/alerts/{id}` | Get alert by ID → 200 OK / 404 |
| `POST` | `/api/alert-rules` | Create an alert rule → 201 Created |
| `GET` | `/api/alert-rules` | List all alert rules → 200 OK |
| `GET` | `/api/alert-rules/{id}` | Get alert rule by ID → 200 OK / 404 |
| `PUT` | `/api/alert-rules/{id}` | Partial update alert rule → 200 OK / 404 |
| `PUT` | `/api/alert-rules/{id}/enable` | Enable alert rule → 204 No Content / 404 |
| `PUT` | `/api/alert-rules/{id}/disable` | Disable alert rule → 204 No Content / 404 |
| `DELETE` | `/api/alert-rules/{id}` | Delete alert rule → 204 No Content / 404 |

### GET `/api/alerts` — pagination query params

| Param | Default | Constraints |
|---|---|---|
| `page` | `0` | `>= 0` |
| `size` | `20` | `>= 1`, `<= 100` |
| `sortBy` | `openedAt` | any entity field name |
| `sortDir` | `desc` | `asc` or `desc` |

### GET `/api/devices` — pagination query params

| Param | Default | Constraints |
|---|---|---|
| `page` | `0` | `>= 0` |
| `size` | `20` | `>= 1`, `<= 100` |
| `sortBy` | `id` | any entity field name |
| `sortDir` | `asc` | `asc` or `desc` |

---

## Configuration

### Spring Profiles

| Profile | Activation | Database | DDL strategy | Flyway | H2 Console | SQL logging |
|---|---|---|---|---|---|---|
| default (prod) | no `SPRING_PROFILES_ACTIVE` set | PostgreSQL via env vars | `none` | enabled | disabled | disabled |
| `local` | `--spring.profiles.active=local` | H2 in-memory (`jdbc:h2:mem:demodb`) | `create-drop` | disabled | enabled at `/h2-console` | enabled |
| test | loaded automatically from `src/test/resources/` | H2 in-memory (`jdbc:h2:mem:testdb`) | `create-drop` | disabled | disabled | disabled |

### Default / prod (`application.properties`)

```properties
spring.application.name=demo

spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=none

spring.datasource.url=${DATASOURCE_URL}
spring.datasource.username=${DATASOURCE_USERNAME}
spring.datasource.password=${DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

alarm.webhook.url=${ALARM_WEBHOOK_URL:http://localhost:9090/webhook/alert}
```

There is no `application-prod.properties` — the base `application.properties` IS the production config. No profile name is required; it activates by default.

### Environment variables (prod / Docker)

| Variable | Description |
|---|---|
| `DATASOURCE_URL` | Full JDBC URL, e.g. `jdbc:postgresql://postgres:5432/demodb` |
| `DATASOURCE_USERNAME` | Database username |
| `DATASOURCE_PASSWORD` | Database password |
| `ALARM_WEBHOOK_URL` | Webhook target URL (optional; defaults to `http://localhost:9090/webhook/alert`) |
| `POSTGRES_DB` | PostgreSQL database name — used by docker-compose only |
| `POSTGRES_USER` | PostgreSQL user — used by docker-compose only |
| `POSTGRES_PASSWORD` | PostgreSQL password — used by docker-compose only |

The `.env` file at the project root provides defaults for all docker-compose variables. It is gitignored.

### Key properties

- `alarm.webhook.url` — target URL for `POST` alarm notifications. Webhook failure is non-fatal: the event stays `PENDING` and is retried; after 3 failed attempts the event is marked `FAILED`.
- `spring.jpa.open-in-view=false` — OSIV is explicitly disabled. The `EntityManager` is released at the end of each `@Transactional` boundary. All lazy associations must be initialized within the service transaction (use `JOIN FETCH` queries).
- `spring.jpa.hibernate.ddl-auto=none` — Hibernate does not touch the schema in prod. Flyway owns all DDL.

---

## Database & Migrations

Schema is managed by **Flyway**. Migrations live in `src/main/resources/db/migration/` and follow standard `V{n}__{description}.sql` naming.

| File | Description |
|---|---|
| `V1__init_schema.sql` | Creates all 5 tables: `device`, `metric`, `alert_rule`, `alert`, `outbox_event` with indexes. `outbox_event` includes `device_id` (plain `BIGINT`, no FK — partition key) and a composite index `idx_outbox_partition (device_id, metric_name, status, id)` |

Flyway runs automatically on startup in the default/prod profile. It is disabled for `local` and `test` profiles — those use Hibernate `create-drop` instead.

**Important PostgreSQL-specific mapping:** `Device.extraAttributes` is annotated `columnDefinition = "CLOB"` in the JPA entity (H2-compatible syntax). The Flyway migration uses `TEXT` instead (the correct PostgreSQL equivalent). The `JsonAttributeConverter` works identically with both since it reads/writes plain `String`.

When adding a new migration: create `V2__your_description.sql` in the same directory. **Never modify an already-applied migration file.**

---

## Core Domain Logic

### Metric ingestion (`MetricService`)
1. `POST /api/metrics` is received with `{ deviceId, metricName, value, timestamp }` — all fields are required.
2. If the device has `status = INACTIVE`, the request is rejected with 409.
3. The metric is persisted.
4. All enabled `AlertRule` records matching `deviceId` + `metricName` are evaluated.
5. For each matching rule:
   - If the threshold condition is met and no `OPEN` alert exists → a new `Alert` (status `OPEN`) and a corresponding `OutboxEvent` (status `PENDING`) are created.
   - If the threshold condition is **not** met and an `OPEN` alert exists → the alert is closed (status `CLOSED`) and another `OutboxEvent` is written.
6. `204 No Content` is returned regardless of alert outcome.

### Webhook delivery (`OutboxScheduler`)
- Runs every 10 seconds.
- Calls `claimPartitionSentinels(PENDING)` — locks the oldest `PENDING` row per `(deviceId, metricName)` partition using `FOR UPDATE SKIP LOCKED`. Each competing scheduler node receives a disjoint set of partitions; no two nodes can process the same partition concurrently.
- For each claimed partition, loads all `PENDING` events in `id ASC` order and delivers them sequentially.
- **Fail-fast per partition**: if delivery of event N fails, events N+1… in the same partition are skipped for this tick — preserving ordering for the next retry.
- A failure in one partition never blocks delivery in other partitions.
- On success → status set to `SENT`.
- On failure → `attemptCount` incremented. If `attemptCount < MAX_ATTEMPTS (3)` → stays `PENDING`; otherwise → status set to `FAILED`.

---

## Docker

### `Dockerfile` (multi-stage)
- **Stage 1** (`gradle:9.5-jdk25`): copies source, pre-fetches dependencies, runs `./gradlew bootJar`.
- **Stage 2** (`eclipse-temurin:25-jre-alpine`): copies the JAR, runs as a non-root `spring` user, exposes port 8080.

### `docker-compose.yml`
- `postgres` service: `postgres:17-alpine`, named volume `postgres-data`, healthcheck via `pg_isready`.
- `app` service: built from `Dockerfile`, `depends_on: postgres: condition: service_healthy`. Flyway migrations run automatically at startup before the app accepts traffic.

### `.env` defaults
```
POSTGRES_DB=demodb
POSTGRES_USER=demo
POSTGRES_PASSWORD=demo_secret
ALARM_WEBHOOK_URL=http://host.docker.internal:9090/webhook/alert
```
Edit `.env` as needed. Never commit real secrets.

---

## Architecture Notes

- **Build system**: Gradle with Groovy DSL (`build.gradle`). No Maven (`pom.xml` absent).
- **Spring Boot 4.x** requires Jakarta EE 10 namespaces (`jakarta.*`, not `javax.*`).
- **Jackson 3.x**: Boot 4.x ships `tools.jackson.core:jackson-databind`. Use `tools.jackson.databind.ObjectMapper`, **not** `com.fasterxml.jackson.databind.ObjectMapper`.
- **Feign client is wired manually** — `AlertNotificationClient` is built in `WebhookClientConfig` using `Feign.builder()` with `SpringEncoder`/`SpringDecoder` backed by `FeignHttpMessageConverters`. There is no `@EnableFeignClients` or `@FeignClient` annotation. The webhook URL is injected via `@Value("${alarm.webhook.url}")` in the config class only.
- **`@WebMvcTest` and `@AutoConfigureMockMvc` do not exist** in Boot 4.x `spring-boot-test-autoconfigure`. Controller tests use `@SpringBootTest(webEnvironment = MOCK)` + `MockMvcBuilders.webAppContextSetup(...)`.
- **`@Value` fields are not injected** when a Spring `@Service` is instantiated directly in a unit test. Use `ReflectionTestUtils.setField(...)` to inject threshold values in service unit tests.
- **Java 25** is configured via Gradle toolchain — ensure JDK 25 is installed locally.
- **Test configuration**: `src/test/resources/application.properties` overrides the datasource to H2 and disables Flyway for all tests. No `@ActiveProfiles` annotation is needed on test classes.
- **MapStruct mappers own all DTO↔Entity conversions** — never write manual field-by-field mapping or `if (field != null)` null-guard blocks in service classes. Two mappers are in use:
  - `DeviceMapper` — `toDevice`, `toResponse`, `updateDevice` (partial patch). `@AfterMapping defaultExtraAttributes` is scoped to `toDevice` only (source param `CreateDeviceRequest` in signature) to avoid resetting `extraAttributes` on updates.
  - `AlertRuleMapper` — `toRule`, `toResponse`, `updateRule` (partial patch). `toResponse` uses `@Mapping(source = "device.id", target = "deviceId")` and `@Mapping(source = "device.name", target = "deviceName")` to flatten the nested association. `id` and `device` are explicitly `ignore = true` on both `toRule` and `updateRule`; the service sets `device` after the mapper call. Both patch methods use `@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)`.
- **DTOs are pure data carriers** — response records must not contain static factory methods (e.g. `from(Entity)`). All entity-to-DTO conversion is the mapper's responsibility.
- **OSIV is explicitly disabled** (`spring.jpa.open-in-view=false`). The `EntityManager` is bound to the `@Transactional` boundary only. Controllers must never access lazy associations directly — all association loading must happen inside a service method annotated with `@Transactional`. Use `JOIN FETCH` queries in repositories to load required associations eagerly within the transaction.
- **Flyway owns the schema in prod** (`ddl-auto=none`). Never change `ddl-auto` to `create`, `update`, or `validate` in the default profile. Add new migrations as `V{n}__description.sql` files.

---

## Code Style

- **`var`**: Use `var` for all local variable declarations where the type is clear from the right-hand side. Do not use explicit types for local variables. Fields, method parameters, and return types must still be explicitly typed — `var` is only valid for local variables.
- **Lombok**: Use Lombok to eliminate boilerplate. Key annotations in use:
  - **Never use `@Data` on JPA entities.** `@Data` generates `equals`/`hashCode` based on all fields including `id`, which breaks collections and violates the `equals` contract across the entity lifecycle (transient vs. managed). It also generates `toString` over all fields, which can trigger `LazyInitializationException` on lazy associations.
  - **JPA entities** use `@Getter` + `@Setter` + `@NoArgsConstructor` + `@AllArgsConstructor` + `@ToString` + `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`. Mark only `id` with `@EqualsAndHashCode.Include` so identity is based solely on the database key.
  - `@RequiredArgsConstructor` — used on Spring components (`@Service`, `@RestController`, etc.) to replace manual constructor injection boilerplate.
  - `@Builder` — used on JPA entities to enable readable object construction without manual constructors. Combine with `@NoArgsConstructor` and `@AllArgsConstructor` (required by Hibernate and Lombok's `@Builder` respectively).
  - `@Slf4j` — used on any class that needs a logger. Never declare `private static final Logger log = LoggerFactory.getLogger(...)` manually. `@Slf4j` injects a `log` field at compile time via the annotation processor.
  - `@Data` is acceptable on **non-entity** mutable classes that require setter-based binding (e.g. `@ModelAttribute` query-param POJOs like `DevicePageRequest`). Do not use it on `@Entity` classes.
- **No wildcard imports**: Never use `.*` imports (e.g. `import org.mockito.Mockito.*` or `import org.springframework.web.bind.annotation.*`). Always import each type or static member explicitly so it is clear where each symbol comes from.
- **Null checks**: Always use `Objects.nonNull(x)` and `Objects.isNull(x)` for null-condition checks. Never use `x != null` or `x == null` inline comparisons. Import `java.util.Objects` and use the static methods — or use `import static java.util.Objects.isNull` / `import static java.util.Objects.nonNull` for brevity at call sites.
- **Records**: Use Java records for DTOs and value objects (immutable data carriers with no JPA/lifecycle requirements). Apply Bean Validation annotations directly on record components. Do **not** use records for `@ModelAttribute`-bound query-param objects — Spring cannot inject individual parameters into a record's canonical constructor; use a Lombok `@Data` class with field-level defaults instead.
- **JPA entities stay as classes**: Records cannot be JPA entities — they are `final` and lack a no-arg constructor. Use Lombok-annotated classes for `@Entity` types instead.
- **Pagination**: Use `DevicePageRequest` / `AlertPageRequest` (`@Data` classes with `toPageable()`) as the single `@ModelAttribute` parameter on list endpoints. The service layer accepts `Pageable` and returns `PagedResponse<T>`.

---

## Git Info

- Branch: `master` (or check with `git branch`).
