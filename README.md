# IoT Device Alert Monitoring Service

A **Spring Boot 4.1.0** microservice for IoT device monitoring. It ingests device metric readings, evaluates configurable per-device alert rules, triggers and auto-closes alert instances, and delivers webhook notifications via a transactional outbox pattern.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Run with Docker (recommended)](#run-with-docker-recommended)
  - [Run locally against H2](#run-locally-against-h2)
  - [Build & other commands](#build--other-commands)
- [Configuration](#configuration)
  - [Default / prod (`application.properties`)](#default--prod-applicationproperties)
  - [Local profile (`application-local.properties`)](#local-profile-application-localproperties)
  - [Test profile (`src/test/resources/application.properties`)](#test-profile-srctestresourcesapplicationproperties)
  - [Environment variables](#environment-variables)
- [Database & Migrations](#database--migrations)
- [REST API](#rest-api)
  - [Devices](#devices)
  - [Metrics](#metrics)
  - [Alerts](#alerts)
  - [Alert Rules](#alert-rules)
  - [Error Responses](#error-responses)
- [Domain Model](#domain-model)
  - [Entities](#entities)
  - [Enums](#enums)
- [Core Business Logic](#core-business-logic)
  - [Metric Ingestion & Alert Evaluation](#metric-ingestion--alert-evaluation)
  - [Webhook Delivery (Outbox Scheduler)](#webhook-delivery-outbox-scheduler)
- [Architectural Patterns](#architectural-patterns)
  - [Transactional Outbox Pattern](#transactional-outbox-pattern)
  - [Partition-Based Ordered Outbox Delivery](#partition-based-ordered-outbox-delivery)
  - [Denormalized Outbox Snapshot](#denormalized-outbox-snapshot)
  - [Eager JOIN FETCH to Prevent LazyInitializationException](#eager-join-fetch-to-prevent-lazyinitializationexception)
  - [MapStruct Partial Update Pattern](#mapstruct-partial-update-pattern)
  - [Manual Feign Wiring (No `@FeignClient`)](#manual-feign-wiring-no-feignclient)
  - [Lombok Entity Convention](#lombok-entity-convention)
- [Testing](#testing)

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 25 (via Gradle toolchain) |
| Spring Boot | 4.1.0 |
| Spring Web (REST) | Managed by Spring Boot BOM |
| Spring Cloud OpenFeign | 2025.1.2 |
| Spring Data JPA | Managed by Spring Boot BOM |
| Hibernate ORM | Managed by Spring Boot BOM |
| Flyway | Managed by Spring Boot BOM |
| MapStruct | 1.6.3 |
| Lombok | Managed by Spring Boot BOM |
| Jackson | 3.x (`tools.jackson.*` — Boot 4.x default) |
| H2 (in-memory DB, local/test only) | Managed by Spring Boot BOM |
| PostgreSQL (default/prod) | Managed by Spring Boot BOM |
| Bean Validation (Hibernate Validator) | Managed by Spring Boot BOM |
| Docker / Docker Compose | any recent version |
| Gradle | 9.5.1 (via wrapper) |
| JUnit 5 (Jupiter) + Mockito | Managed by Spring Boot BOM |

---

## Architecture Overview

```
HTTP Client
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                       REST Controllers                       │
│  DeviceController  MetricController  AlertRuleController     │
│  AlertController   GlobalExceptionHandler                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                        Service Layer                         │
│  DeviceService   MetricService   AlertService                │
│  AlertRuleService   OutboxScheduler (every 10s)              │
│  OutboxEventFactory                                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      Repository Layer                        │
│  DeviceRepository  MetricRepository  AlertRepository         │
│  AlertRuleRepository  OutboxEventRepository                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
             PostgreSQL (default/prod) / H2 (local/test)
                     schema managed by Flyway

OutboxScheduler ──► AlertNotificationClient (Feign) ──► External Webhook
```

On every metric ingestion, `MetricService` evaluates all enabled `AlertRule` records for the device and metric name. When a rule threshold is crossed, an `Alert` and a corresponding `OutboxEvent` are persisted atomically. The `OutboxScheduler` polls every 10 seconds. Events are processed per `(deviceId, metricName)` partition — each partition is claimed by exactly one node using `FOR UPDATE SKIP LOCKED`, and events within the partition are delivered in `id ASC` order. A delivery failure stops processing for that partition (fail-fast) but never blocks other partitions. Each event is retried up to 3 times before being permanently marked `FAILED`.

---

## Project Structure

```
.
├── Dockerfile                              # Multi-stage build: Gradle → slim JRE image
├── docker-compose.yml                      # postgres:17 + app services
├── .env                                    # Local docker-compose defaults (gitignored)
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── DemoApplication.java                    # @SpringBootApplication entry point
    │   │   ├── config/
    │   │   │   ├── JsonAttributeConverter.java         # JPA converter: Map<String,String> ↔ JSON TEXT
    │   │   │   ├── SchedulingConfig.java               # @EnableScheduling
    │   │   │   └── WebhookClientConfig.java            # Manual Feign bean wiring; binds alarm.webhook.url
    │   │   ├── client/
    │   │   │   └── AlertNotificationClient.java        # Feign interface: POST alert payload to webhook
    │   │   ├── controller/
    │   │   │   ├── DeviceController.java               # CRUD /api/devices + activate/deactivate
    │   │   │   ├── MetricController.java               # POST /api/metrics → 204 No Content
    │   │   │   ├── AlertController.java                # GET /api/alerts, GET /api/alerts/{id}
    │   │   │   ├── AlertRuleController.java            # CRUD /api/alert-rules + enable/disable
    │   │   │   └── GlobalExceptionHandler.java         # @RestControllerAdvice: 404 / 409 / 400
    │   │   ├── service/
    │   │   │   ├── DeviceService.java                  # Device lifecycle: register, list, get, update, activate, deactivate
    │   │   │   ├── MetricService.java                  # Ingest metric, evaluate rules, open/close alerts, write outbox
    │   │   │   ├── AlertService.java                   # Alert queries: listAll (paged), getById — both @Transactional(readOnly=true)
    │   │   │   ├── AlertRuleService.java               # Alert rule lifecycle: create, list, get, update, enable, disable, delete
    │                   │   │   ├── OutboxScheduler.java                # @Scheduled: partition-based ordered delivery via FOR UPDATE SKIP LOCKED; fail-fast per partition
    │   │   │   └── OutboxEventFactory.java             # @Component factory: builds OutboxEvent instances (injected into MetricService)
    │   │   ├── repository/
    │   │   │   ├── DeviceRepository.java               # JpaRepository<Device, Long>
    │   │   │   ├── MetricRepository.java               # JpaRepository<Metric, Long>
    │   │   │   ├── AlertRepository.java                # + findByRuleIdsAndStatusOpen (JOIN FETCH), findAllPaged (JOIN FETCH), findByIdWithDeviceAndRule (JOIN FETCH)
    │   │   │   ├── AlertRuleRepository.java            # + findByDeviceIdAndMetricNameAndEnabledTrue
    │                   │   │   └── OutboxEventRepository.java          # + claimPartitionSentinels (@Lock PESSIMISTIC_WRITE + SKIP LOCKED), findAllPendingByPartition
    │   │   ├── model/
    │   │   │   ├── Device.java                         # @Entity: id, name, type, location, extraAttributes, status
    │   │   │   ├── Metric.java                         # @Entity: id, device, metricName, value, timestamp
    │   │   │   ├── Alert.java                          # @Entity: id, device, rule, triggerValue, severity, status, timestamps
    │   │   │   ├── AlertRule.java                      # @Entity: id, device, metricName, operator, threshold, severity, enabled
    │                   │   │   ├── OutboxEvent.java                    # @Entity: fully denormalized alert snapshot + deviceId (partition key) + delivery status
    │   │   │   ├── AlertStatus.java                    # Enum: OPEN, CLOSED
    │   │   │   ├── AlertSeverity.java                  # Enum: LOW, MEDIUM, HIGH, CRITICAL
    │   │   │   ├── AlertOperator.java                  # Enum: GREATER_THAN, LESS_THAN, EQUALS
    │   │   │   ├── DeviceStatus.java                   # Enum: ACTIVE, INACTIVE
    │   │   │   └── DeviceType.java                     # Enum: SENSOR, GATEWAY, CONTROLLER
    │   │   ├── mapper/
    │   │   │   ├── DeviceMapper.java                   # MapStruct: CreateDeviceRequest/UpdateDeviceRequest ↔ Device → DeviceResponse
    │   │   │   └── AlertRuleMapper.java                # MapStruct: CreateAlertRuleRequest → AlertRule, AlertRule → AlertRuleResponse, partial UpdateAlertRuleRequest patch
    │   │   ├── exception/
    │   │   │   └── DeviceUnactivatedException.java     # RuntimeException → HTTP 409 Conflict
    │   │   └── dto/
    │   │       ├── CreateDeviceRequest.java            # record (validated)
    │   │       ├── UpdateDeviceRequest.java            # record (all nullable — patch semantics)
    │   │       ├── DeviceResponse.java                 # record
    │   │       ├── DevicePageRequest.java              # @Data class: page, size, sortBy, sortDir + toPageable()
    │   │       ├── AlertPageRequest.java               # @Data class: same structure, defaults sortBy=openedAt desc
    │   │       ├── PagedResponse.java                  # generic record: content, page, size, totalElements, totalPages
    │   │       ├── CreateMetricRequest.java            # record (all @NotNull/@NotBlank)
    │   │       ├── CreateAlertRuleRequest.java         # record (validated)
    │   │       ├── UpdateAlertRuleRequest.java         # record (all nullable — patch semantics)
    │   │       ├── AlertRuleResponse.java              # record (pure data carrier — no static factory)
    │   │       ├── AlertInstanceResponse.java          # record + static from(Alert)
    │   │       └── AlertNotificationPayload.java       # record + static from(OutboxEvent) — webhook POST body
    │   └── resources/
    │       ├── application.properties                  # Default/prod config: PostgreSQL via env vars, Flyway enabled
    │       ├── application-local.properties            # Local dev overrides: H2, Flyway disabled, SQL logging
    │       └── db/
    │           └── migration/
    │               └── V1__init_schema.sql             # Initial DDL for all 5 tables; outbox_event includes device_id (partition key) + composite index
    └── test/
        ├── java/com/example/demo/
        │   ├── DemoApplicationTests.java               # Context-load smoke test
        │   ├── controller/
        │   │   ├── DeviceControllerTest.java
        │   │   ├── MetricControllerTest.java
        │   │   ├── AlertControllerTest.java
        │   │   └── AlertRuleControllerTest.java
        │   ├── mapper/
        │   │   ├── AlertRuleMapperTest.java
        │   │   └── DeviceMapperTest.java
        │   └── service/
        │       ├── DeviceServiceTest.java
        │       ├── MetricServiceTest.java
        │       ├── AlertRuleServiceTest.java
        │       └── OutboxSchedulerTest.java
        └── resources/
            └── application.properties                  # Test overrides: H2, Flyway disabled
```

---

## Getting Started

### Prerequisites

- **JDK 25** (required by the Gradle Java toolchain)
- **Gradle wrapper** is included — no separate Gradle installation needed
- **Docker & Docker Compose** (for the recommended local setup)

### Run with Docker (recommended)

The easiest way to run the full stack locally — PostgreSQL + the Spring Boot app together:

```bash
# Copy the example env file (only needed once)
cp .env .env   # defaults are already set, no edits required

# Build the image and start both services
docker compose up --build
```

The app will be available at `http://localhost:8080` once the postgres healthcheck passes and Flyway has run the migrations.

To stop and remove containers:

```bash
docker compose down          # keep the postgres volume
docker compose down -v       # also delete the postgres data volume
```

### Run locally against H2

For rapid iteration without Docker. Uses an in-memory H2 database — schema is created by Hibernate on startup and dropped on shutdown. No external infrastructure required.

```bash
# Linux / macOS
./gradlew bootRun --args='--spring.profiles.active=local'

# Windows
gradlew.bat bootRun --args='--spring.profiles.active=local'
```

**H2 Console** is available at [http://localhost:8080/h2-console](http://localhost:8080/h2-console):
- JDBC URL: `jdbc:h2:mem:demodb`
- Username: `sa`
- Password: *(empty)*

### Build & other commands

```bash
# Run all tests
./gradlew test

# Build an executable JAR → build/libs/demo-0.0.1-SNAPSHOT.jar
./gradlew bootJar

# Clean build artifacts
./gradlew clean
```

---

## Configuration

### Default / prod (`application.properties`)

This is the active configuration when no profile override is set (i.e. in Docker / production). All sensitive values are injected via environment variables.

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

### Local profile (`application-local.properties`)

Activate with `--spring.profiles.active=local`. Overrides the datasource to H2 and disables Flyway.

```properties
spring.datasource.url=jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

spring.flyway.enabled=false
```

### Test profile (`src/test/resources/application.properties`)

Loaded automatically for all tests — no `@ActiveProfiles` annotation needed. Uses a separate H2 in-memory database (`testdb`) and disables Flyway.

### Environment variables

| Variable | Description | Required |
|---|---|---|
| `DATASOURCE_URL` | Full JDBC URL, e.g. `jdbc:postgresql://localhost:5432/demodb` | Yes (prod/Docker) |
| `DATASOURCE_USERNAME` | Database username | Yes (prod/Docker) |
| `DATASOURCE_PASSWORD` | Database password | Yes (prod/Docker) |
| `ALARM_WEBHOOK_URL` | Target URL for alert webhook `POST`s. Defaults to `http://localhost:9090/webhook/alert` | No |
| `POSTGRES_DB` | PostgreSQL database name (used by `docker-compose.yml`) | Docker only |
| `POSTGRES_USER` | PostgreSQL user (used by `docker-compose.yml`) | Docker only |
| `POSTGRES_PASSWORD` | PostgreSQL password (used by `docker-compose.yml`) | Docker only |

The `.env` file at the project root provides defaults for all docker-compose variables — edit it as needed. It is gitignored and must not be committed with real secrets.

---

## Database & Migrations

Schema is managed by **Flyway**. Migrations live in `src/main/resources/db/migration/` and follow the standard `V{n}__{description}.sql` naming convention.

| File | Description |
|---|---|
| `V1__init_schema.sql` | Creates all 5 tables: `device`, `metric`, `alert_rule`, `alert`, `outbox_event`. `outbox_event` includes `device_id` (plain `BIGINT`, no FK — partition key) and a composite index `idx_outbox_partition (device_id, metric_name, status, id)` |

Flyway runs automatically on application startup (prod/Docker). It is disabled for the `local` and `test` profiles — those use Hibernate `create-drop` instead.

When adding a new migration: create `V2__your_description.sql` in the same directory. Never modify an already-applied migration file.

---

## REST API

Base URL: `http://localhost:8080`

All request bodies are `application/json`. All successful responses are `application/json` unless the status is `204 No Content`.

---

### Devices

#### `POST /api/devices` — Register a device

**Request body:**

```json
{
  "name": "Temp Sensor A1",
  "type": "SENSOR",
  "location": "Server Room 1",
  "extraAttributes": {
    "manufacturer": "Acme",
    "firmware": "2.1.0"
  }
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `name` | string | yes | not blank |
| `type` | string (enum) | yes | `SENSOR`, `GATEWAY`, `CONTROLLER` |
| `location` | string | yes | not blank |
| `extraAttributes` | object (string values) | no | any key-value pairs; defaults to `{}` |

**Response — `201 Created`:**

```json
{
  "id": 1,
  "name": "Temp Sensor A1",
  "type": "SENSOR",
  "location": "Server Room 1",
  "extraAttributes": { "manufacturer": "Acme", "firmware": "2.1.0" },
  "status": "ACTIVE"
}
```

New devices are always created with `status = ACTIVE`.

---

#### `GET /api/devices` — List devices (paginated)

**Query parameters:**

| Param | Type | Default | Constraints |
|---|---|---|---|
| `page` | integer | `0` | `>= 0` |
| `size` | integer | `20` | `>= 1`, `<= 100` |
| `sortBy` | string | `id` | any entity field name |
| `sortDir` | string | `asc` | `asc` or `desc` (case-insensitive) |

**Response — `200 OK`:**

```json
{
  "content": [ { "id": 1, "name": "...", "type": "SENSOR", "location": "...", "extraAttributes": {}, "status": "ACTIVE" } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

#### `GET /api/devices/{id}` — Get device by ID

**Response — `200 OK`:** `DeviceResponse` object
**Response — `404 Not Found`:** if device does not exist

---

#### `PUT /api/devices/{id}` — Partial update (patch semantics)

**Request body** — all fields are optional; `null` fields are ignored:

```json
{
  "name": "Updated Name",
  "location": "New Location"
}
```

**Response — `200 OK`:** updated `DeviceResponse`
**Response — `404 Not Found`:** if device does not exist

---

#### `PUT /api/devices/{id}/activate` — Activate device

**Response — `204 No Content`**
**Response — `404 Not Found`:** if device does not exist

---

#### `PUT /api/devices/{id}/deactivate` — Deactivate device

**Response — `204 No Content`**
**Response — `404 Not Found`:** if device does not exist

> Inactive devices reject metric ingestion with `409 Conflict`.

---

### Metrics

#### `POST /api/metrics` — Ingest a metric reading

**Request body:**

```json
{
  "deviceId": 1,
  "metricName": "temperature",
  "value": 82.5,
  "timestamp": "2026-07-03T14:30:00"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `deviceId` | long | yes | must reference an existing device |
| `metricName` | string | yes | not blank |
| `value` | number | yes | not null |
| `timestamp` | ISO-8601 datetime | yes | not null |

**Response — `204 No Content`** (always, regardless of alert outcome)

**Side effects:**
1. Metric is persisted.
2. All enabled `AlertRule` records for the given `deviceId` + `metricName` are evaluated.
3. If a threshold condition is crossed and no open alert exists → a new `Alert` (status `OPEN`) and `OutboxEvent` (status `PENDING`) are created.
4. If a condition clears and an open alert exists → the `Alert` is closed (status `CLOSED`) and another `OutboxEvent` is written.

**Errors:**
- `400 Bad Request` — validation failure
- `404 Not Found` — device does not exist
- `409 Conflict` — device is `INACTIVE`

---

### Alerts

#### `GET /api/alerts` — List all alerts (paginated, newest first by default)

**Query parameters:** same pagination params as `/api/devices` (default `sortBy=openedAt`, `sortDir=desc`)

**Response — `200 OK`:**

```json
{
  "content": [
    {
      "id": 1,
      "deviceId": 1,
      "deviceName": "Temp Sensor A1",
      "ruleId": 2,
      "metricName": "temperature",
      "triggerValue": 82.5,
      "severity": "HIGH",
      "openedAt": "2026-07-03T14:30:00",
      "closedAt": null,
      "status": "OPEN"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

#### `GET /api/alerts/{id}` — Get alert by ID

**Response — `200 OK`:** `AlertInstanceResponse`
**Response — `404 Not Found`:** if alert does not exist

---

### Alert Rules

#### `POST /api/alert-rules` — Create an alert rule

**Request body:**

```json
{
  "deviceId": 1,
  "metricName": "temperature",
  "operator": "GREATER_THAN",
  "threshold": 80.0,
  "severity": "HIGH",
  "enabled": true
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `deviceId` | long | yes | must reference an existing device |
| `metricName` | string | yes | not blank |
| `operator` | string (enum) | yes | `GREATER_THAN`, `LESS_THAN`, `EQUALS` |
| `threshold` | number | yes | not null |
| `severity` | string (enum) | yes | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `enabled` | boolean | yes | `true` or `false` |

**Response — `201 Created`:**

```json
{
  "id": 2,
  "deviceId": 1,
  "deviceName": "Temp Sensor A1",
  "metricName": "temperature",
  "operator": "GREATER_THAN",
  "threshold": 80.0,
  "severity": "HIGH",
  "enabled": true
}
```

---

#### `GET /api/alert-rules` — List all alert rules

**Response — `200 OK`:** array of `AlertRuleResponse`

---

#### `GET /api/alert-rules/{id}` — Get alert rule by ID

**Response — `200 OK`:** `AlertRuleResponse`
**Response — `404 Not Found`:** if rule does not exist

---

#### `PUT /api/alert-rules/{id}` — Partial update alert rule

**Request body** — all fields optional; `null` fields are ignored:

```json
{
  "threshold": 85.0,
  "severity": "CRITICAL"
}
```

**Response — `200 OK`:** updated `AlertRuleResponse`
**Response — `404 Not Found`:** if rule does not exist

---

#### `PUT /api/alert-rules/{id}/enable` — Enable alert rule

**Response — `204 No Content`**
**Response — `404 Not Found`:** if rule does not exist

---

#### `PUT /api/alert-rules/{id}/disable` — Disable alert rule

**Response — `204 No Content`**
**Response — `404 Not Found`:** if rule does not exist

---

#### `DELETE /api/alert-rules/{id}` — Delete alert rule

**Response — `204 No Content`**
**Response — `404 Not Found`:** if rule does not exist

---

### Error Responses

All errors return a consistent JSON body:

```json
{
  "status": 404,
  "message": "Entity not found"
}
```

| Exception | HTTP Status | Typical Cause |
|---|---|---|
| `EntityNotFoundException` | `404 Not Found` | Device, rule, or alert with given ID does not exist |
| `DeviceUnactivatedException` | `409 Conflict` | Metric submitted for an `INACTIVE` device |
| `MethodArgumentNotValidException` | `400 Bad Request` | Bean Validation failed; `message` lists all field errors comma-separated |

---

## Domain Model

### Entities

#### `Device`

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key (auto-generated) |
| `name` | `String` | Human-readable device name |
| `type` | `DeviceType` | Device category |
| `location` | `String` | Physical location description |
| `extraAttributes` | `Map<String,String>` | Arbitrary key-value metadata; stored as JSON TEXT |
| `status` | `DeviceStatus` | `ACTIVE` (default) or `INACTIVE` |

#### `AlertRule`

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key |
| `device` | `Device` | Associated device |
| `metricName` | `String` | Metric name to watch (e.g. `"temperature"`) |
| `operator` | `AlertOperator` | Comparison operator |
| `threshold` | `Double` | Threshold value |
| `severity` | `AlertSeverity` | Alert severity when triggered |
| `enabled` | `boolean` | Whether the rule is active (default `true`) |

#### `Alert`

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key |
| `device` | `Device` | Device that produced the metric |
| `rule` | `AlertRule` | Rule that triggered the alert |
| `metricName` | `String` | Denormalized metric name |
| `triggerValue` | `Double` | The exact metric value that tripped the rule |
| `severity` | `AlertSeverity` | Denormalized from rule at creation time |
| `openedAt` | `LocalDateTime` | Set to the triggering metric's `timestamp` |
| `closedAt` | `LocalDateTime` | Set when condition clears; `null` if still open |
| `status` | `AlertStatus` | `OPEN` or `CLOSED` |

#### `Metric`

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key |
| `device` | `Device` | Source device |
| `metricName` | `String` | Metric name (e.g. `"temperature"`, `"battery_level"`) |
| `value` | `Double` | Numeric reading |
| `timestamp` | `LocalDateTime` | Client-supplied timestamp of the reading |

#### `OutboxEvent`

Fully denormalized — no foreign key to `Alert`. All alert data is copied at write time.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary key |
| `deviceId` | `Long` | Denormalized copy of `device.id` (no FK); combined with `metricName` forms the partition key for ordered multi-node delivery |
| `deviceName` | `String` | Snapshot of device name |
| `metricName` | `String` | Snapshot |
| `triggerValue` | `Double` | Snapshot |
| `threshold` | `Double` | Snapshot of rule threshold at time of event |
| `severity` | `AlertSeverity` | Snapshot |
| `alertStatus` | `AlertStatus` | `OPEN` (alert opened) or `CLOSED` (alert closed) |
| `alertOpenedAt` | `LocalDateTime` | Snapshot |
| `alertClosedAt` | `LocalDateTime` | Snapshot; `null` for OPEN events |
| `status` | `OutboxStatus` | `PENDING`, `SENT`, or `FAILED` |
| `attemptCount` | `int` | Number of delivery attempts so far |
| `createdAt` | `LocalDateTime` | When the outbox record was written |
| `lastAttemptAt` | `LocalDateTime` | Timestamp of the most recent delivery attempt |

---

### Enums

| Enum | Values |
|---|---|
| `DeviceType` | `SENSOR`, `GATEWAY`, `CONTROLLER` |
| `DeviceStatus` | `ACTIVE`, `INACTIVE` |
| `AlertOperator` | `GREATER_THAN`, `LESS_THAN`, `EQUALS` |
| `AlertSeverity` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `AlertStatus` | `OPEN`, `CLOSED` |
| `OutboxEvent.OutboxStatus` | `PENDING`, `SENT`, `FAILED` |

---

## Core Business Logic

### Metric Ingestion & Alert Evaluation

`MetricService.ingest()` is called on every `POST /api/metrics`. The full pipeline:

1. **Device lookup** — load the device by `deviceId`; throw `EntityNotFoundException` (→ 404) if not found.
2. **Active guard** — if `device.status == INACTIVE`, throw `DeviceUnactivatedException` (→ 409). Nothing is persisted.
3. **Persist metric** — save the `Metric` entity.
4. **Load matching rules** — fetch all enabled `AlertRule` records matching `(deviceId, metricName)`. If none exist, return immediately.
5. **Load existing open alerts** — in a single `JOIN FETCH` query, load all `OPEN` alerts for the matched rule IDs, indexed into a `Map<ruleId, Alert>`.
6. **Evaluate each rule:**
   - `GREATER_THAN`: condition = `value > threshold`
   - `LESS_THAN`: condition = `value < threshold`
   - `EQUALS`: condition = `Double.compare(value, threshold) == 0`
7. **Open alert** — if condition is **met** and no open alert exists for the rule:
   - Create `Alert` with `status = OPEN`, `openedAt = metric.timestamp`.
   - Create `OutboxEvent` with `status = PENDING`, `attemptCount = 0`, `alertStatus = OPEN`.
8. **Close alert** — if condition is **not met** and an open alert exists for the rule:
   - Set `alert.status = CLOSED`, `alert.closedAt = metric.timestamp`.
   - Create `OutboxEvent` with `status = PENDING`, `alertStatus = CLOSED`.
9. **Idempotency** — if condition is met and an open alert already exists, no duplicate is created.

### Webhook Delivery (Outbox Scheduler)

`OutboxScheduler.processPendingEvents()` runs every **10 seconds** in a `@Transactional` context:

1. Call `claimPartitionSentinels(PENDING)` — locks the **oldest `PENDING` row per `(deviceId, metricName)` partition** with `FOR UPDATE SKIP LOCKED`. Competing nodes skip already-locked partitions, so each partition is owned by at most one node per tick.
2. For each claimed partition, load **all `PENDING` events** for that `(deviceId, metricName)` in `id ASC` order.
3. Deliver events sequentially within the partition:
   - Increment `attemptCount`, set `lastAttemptAt = now()`.
   - POST `AlertNotificationPayload` (built from the event snapshot) to `alarm.webhook.url` via Feign.
   - **Success** → set `status = SENT`, continue to the next event in the partition.
   - **Failure** → if `attemptCount < 3` → leave `status = PENDING`; if `attemptCount >= 3` → set `status = FAILED`. Either way, **stop processing this partition** (fail-fast) — later events in the partition are not touched until the next tick.
4. A failure in one partition never blocks delivery in other partitions — they proceed independently.

This guarantees that for a given `(deviceId, metricName)` pair, an `OPEN` event is **always delivered before the corresponding `CLOSED` event**, even when multiple scheduler nodes run concurrently.

The webhook payload (`AlertNotificationPayload`) contains:

```json
{
  "deviceName": "Temp Sensor A1",
  "metricName": "temperature",
  "triggerValue": 82.5,
  "threshold": 80.0,
  "severity": "HIGH",
  "alertStatus": "OPEN",
  "alertOpenedAt": "2026-07-03T14:30:00",
  "alertClosedAt": null
}
```

Webhook delivery failure is **non-fatal** — alerts are persisted regardless of webhook outcome.

---

## Architectural Patterns

### Transactional Outbox Pattern
`OutboxEvent` records are written in the **same transaction** as the `Alert` save inside `MetricService`. The `OutboxScheduler` polls independently every 10 seconds. This guarantees that if the JVM crashes after saving the alert but before the webhook fires, the event is not lost — it stays `PENDING` and will be picked up on the next scheduler tick.

### Partition-Based Ordered Outbox Delivery
Each `(deviceId, metricName)` pair is a **partition**. Within a partition, events must be delivered in `id ASC` insertion order — ensuring that an `OPEN` notification always reaches the webhook consumer before the corresponding `CLOSED` notification.

The scheduler uses a two-step strategy per tick:
1. **Claim sentinels** (`claimPartitionSentinels`) — locks the single oldest `PENDING` row per partition with `FOR UPDATE SKIP LOCKED`. A competing node that tries to lock the same sentinel row gets nothing back for that partition — it is effectively excluded from processing it this tick.
2. **Drain partition** (`findAllPendingByPartition`) — loads all `PENDING` events for the claimed partition in `id ASC` order and delivers them sequentially. If any event fails, the rest of the partition is skipped (fail-fast) — guaranteeing ordering is maintained for the next retry.

This means multiple scheduler nodes can process **different partitions in parallel** (full horizontal scalability) while still guaranteeing **strict in-order delivery within each partition**.

### Denormalized Outbox Snapshot
`OutboxEvent` holds no foreign key to `Alert`. All fields required for the webhook payload are copied at write time. This means the scheduler delivers each event with **zero additional DB lookups**, and the payload remains accurate even if the `Alert` record is later modified.

### Eager JOIN FETCH to Prevent LazyInitializationException
OSIV (`spring.jpa.open-in-view`) is explicitly set to `false`. The `EntityManager` is bound strictly to the `@Transactional` boundary. Controllers never access lazy associations — all loading must occur inside a service method. `AlertRepository` uses `JOIN FETCH` JPQL queries to eagerly load the `device` and `rule` associations in a single query within `AlertService`'s `@Transactional(readOnly=true)` methods.

### MapStruct Partial Update Pattern
Both `DeviceMapper.updateDevice()` and `AlertRuleMapper.updateRule()` use `@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)`. Null fields in the patch request are silently skipped, enabling true PATCH semantics without explicit null checks in the service layer. `id` and entity associations (`device`) are explicitly `ignore = true` on all write methods. Response DTOs are pure data carriers — all `Entity → DTO` conversions are the mapper's responsibility; static `from(Entity)` factory methods are not used.

### Manual Feign Wiring (No `@FeignClient`)
`WebhookClientConfig` constructs `AlertNotificationClient` programmatically with `Feign.builder()` using Spring's `SpringEncoder`/`SpringDecoder`. There is no `@EnableFeignClients` or `@FeignClient` annotation. The webhook URL is bound once at startup via `@Value("${alarm.webhook.url}")`.

### Lombok Entity Convention
All `@Entity` classes use `@Getter + @Setter + @NoArgsConstructor + @AllArgsConstructor + @Builder + @ToString + @EqualsAndHashCode(onlyExplicitlyIncluded = true)`, with only `id` marked `@EqualsAndHashCode.Include`. `@Data` is **never** used on entities to avoid broken `equals`/`hashCode` and potential `LazyInitializationException` in `@ToString`.

---

## Testing

Tests are under `src/test/java/com/example/demo/`. The test `application.properties` in `src/test/resources/` switches the datasource to H2 and disables Flyway automatically — no `@ActiveProfiles` annotation needed on test classes.

```bash
./gradlew test
```

| Test class | Type | Coverage |
|---|---|---|
| `DemoApplicationTests` | Smoke test | Spring context loads without errors |
| `DeviceControllerTest` | `@SpringBootTest` + MockMvc | CRUD, pagination, validation, activate/deactivate |
| `MetricControllerTest` | `@SpringBootTest` + MockMvc | Ingest, alert open/close lifecycle, inactive device, validation |
| `AlertControllerTest` | `@SpringBootTest` + MockMvc | List (paginated), get by ID, 404 handling |
| `AlertRuleControllerTest` | `@SpringBootTest` + MockMvc | Full CRUD, enable/disable, validation |
| `AlertRuleMapperTest` | Mappers.getMapper unit test | toRule, toResponse (nested device flattening), updateRule partial patch |
| `DeviceMapperTest` | Mappers.getMapper unit test | toDevice, toResponse, updateDevice partial patch, null extraAttributes default |
| `DeviceServiceTest` | Mockito unit test | Register, list (paged), get, update, activate, deactivate, findActiveOrThrow |
| `AlertRuleServiceTest` | Mockito unit test | Create, list, get, update, enable, disable, delete, all 404 paths |
| `MetricServiceTest` | Mockito unit test | All operators, alert open/close, duplicate guard, inactive device, timestamp forwarding |
| `OutboxSchedulerTest` | Mockito unit test | Retry logic, max attempts (FAILED at 3), cluster-safety (SKIP LOCKED), partition ordering (id ASC), fail-fast per partition, cross-partition independence |

> **Boot 4.x note:** `@WebMvcTest` and `@AutoConfigureMockMvc` do not exist. Controller tests use `@SpringBootTest(webEnvironment = MOCK)` + `MockMvcBuilders.webAppContextSetup(...)`.
