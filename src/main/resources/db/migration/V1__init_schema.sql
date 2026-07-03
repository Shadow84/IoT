-- =============================================================================
-- V1__init_schema.sql
-- Initial schema for the IoT device monitoring service.
--
-- Notes:
--   - All enum columns stored as VARCHAR (EnumType.STRING in JPA entities).
--   - extra_attributes stored as TEXT (PostgreSQL has no CLOB type; the
--     JsonAttributeConverter reads/writes plain String, so TEXT is correct).
--   - Indexes are added on all FK columns and on outbox_event.status which
--     is polled on every OutboxScheduler tick.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- device
-- -----------------------------------------------------------------------------
CREATE TABLE device (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    type             VARCHAR(50)  NOT NULL,   -- DeviceType: SENSOR | GATEWAY | CONTROLLER
    location         VARCHAR(255) NOT NULL,
    extra_attributes TEXT         NOT NULL,   -- JSON map<String,String> via JsonAttributeConverter
    status           VARCHAR(50)  NOT NULL    -- DeviceStatus: ACTIVE | INACTIVE
);

-- -----------------------------------------------------------------------------
-- metric
-- -----------------------------------------------------------------------------
CREATE TABLE metric (
    id           BIGSERIAL        PRIMARY KEY,
    device_id    BIGINT           NOT NULL REFERENCES device (id),
    metric_name  VARCHAR(255)     NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    timestamp    TIMESTAMP        NOT NULL
);

CREATE INDEX idx_metric_device_id ON metric (device_id);

-- -----------------------------------------------------------------------------
-- alert_rule
-- -----------------------------------------------------------------------------
CREATE TABLE alert_rule (
    id          BIGSERIAL        PRIMARY KEY,
    device_id   BIGINT           NOT NULL REFERENCES device (id),
    metric_name VARCHAR(255)     NOT NULL,
    operator    VARCHAR(50)      NOT NULL,   -- AlertOperator: GREATER_THAN | LESS_THAN | EQUALS
    threshold   DOUBLE PRECISION NOT NULL,
    severity    VARCHAR(50)      NOT NULL,   -- AlertSeverity: LOW | MEDIUM | HIGH | CRITICAL
    enabled     BOOLEAN          NOT NULL
);

CREATE INDEX idx_alert_rule_device_id ON alert_rule (device_id);

-- -----------------------------------------------------------------------------
-- alert
-- -----------------------------------------------------------------------------
CREATE TABLE alert (
    id            BIGSERIAL        PRIMARY KEY,
    device_id     BIGINT           NOT NULL REFERENCES device (id),
    rule_id       BIGINT           NOT NULL REFERENCES alert_rule (id),
    metric_name   VARCHAR(255)     NOT NULL,
    trigger_value DOUBLE PRECISION NOT NULL,
    severity      VARCHAR(50)      NOT NULL,   -- AlertSeverity
    opened_at     TIMESTAMP        NOT NULL,
    closed_at     TIMESTAMP,
    status        VARCHAR(50)      NOT NULL    -- AlertStatus: OPEN | CLOSED
);

CREATE INDEX idx_alert_device_id ON alert (device_id);
CREATE INDEX idx_alert_rule_id   ON alert (rule_id);
CREATE INDEX idx_alert_status    ON alert (status);

-- -----------------------------------------------------------------------------
-- outbox_event  (fully denormalized — no FK to alert)
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_event (
    id              BIGSERIAL        PRIMARY KEY,
    device_name     VARCHAR(255)     NOT NULL,
    metric_name     VARCHAR(255)     NOT NULL,
    trigger_value   DOUBLE PRECISION NOT NULL,
    threshold       DOUBLE PRECISION NOT NULL,
    severity        VARCHAR(50)      NOT NULL,   -- AlertSeverity
    alert_status    VARCHAR(50)      NOT NULL,   -- AlertStatus
    alert_opened_at TIMESTAMP        NOT NULL,
    alert_closed_at TIMESTAMP,
    status          VARCHAR(50)      NOT NULL,   -- OutboxStatus: PENDING | SENT | FAILED
    attempt_count   INT              NOT NULL,
    created_at      TIMESTAMP        NOT NULL,
    last_attempt_at TIMESTAMP
);

-- OutboxScheduler polls PENDING rows with FOR UPDATE SKIP LOCKED on every tick
CREATE INDEX idx_outbox_event_status ON outbox_event (status);
