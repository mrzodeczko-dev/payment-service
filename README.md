# 🚀 Payment Service - Hexagonal Payment Processing Platform

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![codecov](https://codecov.io/gh/CoderNoOne/payment-service/branch/master/graph/badge.svg)](https://codecov.io/gh/CoderNoOne/payment-service)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 📖 Overview
Payment Service is a production-ready, modular payment processing backend built to handle secure online payment initiation, webhook notification handling, and reliable event publishing via the TPay payment gateway.

This repository serves as a showcase of modern backend engineering practices (as of 2026), demonstrating clean Hexagonal Architecture (Ports & Adapters), the Transactional Outbox Pattern, and a containerized development environment ready for seamless deployment.

## 🏗️ Architecture

The system follows a **Hexagonal Architecture (Ports & Adapters)** approach, strictly separating domain logic from infrastructure concerns. The Transactional Outbox Pattern guarantees reliable event delivery without distributed transactions.

```mermaid
graph TD
    Client([Client App / Postman]) --> PC[PaymentController]
    PC --> PUC[PaymentUseCase Port]
    PUC --> PS[PaymentService - Domain Logic]

    PS --- PR[PaymentRepository Port]
    PS --- PGP[PaymentGatewayPort]
    PS --- NP[NotificationPort]

    PR --> PRA[PaymentRepositoryAdapter]
    PGP --> TPA[TPayGatewayAdapter]
    NP --> ENA[ExternalServiceNotificationAdapter]

    PRA --- DB[(MySQL 9.6)]
    TPA --- TPAY{{TPay Payment API}}
    ENA --- EXT{{External Notification Service}}

    PS --> OE[OutboxEvent]
    OE --> OER[OutboxEventRepository Port]
    OER --> OERA[OutboxEventRepositoryAdapter]
    OERA --- DB

    OP[OutboxProcessor + ShedLock] --> OER
    OP --> OES[OutboxEventSender]
```

## ✨ Technical Highlights & Engineering Decisions

* **Hexagonal Architecture (Ports & Adapters):** Strict boundary between domain, application, and infrastructure layers. Business logic is fully decoupled from frameworks, databases, and external APIs — enabling independent testability and easy adapter swapping.
* **Transactional Outbox Pattern:** Payment state changes and outbox events are persisted in a single database transaction, guaranteeing eventual consistency and eliminating the dual-write problem. The `OutboxProcessor` polls and dispatches pending events reliably.
* **Distributed Lock with ShedLock:** The `OutboxProcessor` uses ShedLock (JDBC-backed) to ensure that only one instance processes outbox events at a time — critical for horizontal scaling without duplicate event delivery.
* **Virtual Threads (Project Loom):** Spring Boot is configured with `spring.threads.virtual.enabled=true`, leveraging Java 25 virtual threads to maximize throughput on I/O-bound payment gateway calls without the overhead of platform thread pools.
* **Secure Notification Verification:** Incoming TPay webhook notifications are validated using cryptographic signature verification (`commons-codec`), protecting against spoofed payment callbacks.
* **Optimized Docker Build:** Multi-stage Dockerfile with Maven dependency caching and Spring Boot layered JAR extraction, resulting in fast rebuilds and minimal runtime image size.

## 💻 Tech Stack

| Layer              | Technology                                                   |
|--------------------|--------------------------------------------------------------|
| **Language**       | Java 25                                                      |
| **Framework**      | Spring Boot 4.0.5, Spring Data JPA, Spring WebMVC, Spring Validation |
| **Database**       | MySQL 9.6.0 (HikariCP connection pool)                      |
| **Payment Gateway**| TPay API (OAuth2 + REST)                                     |
| **Scheduling**     | ShedLock 6.0.2 (JDBC provider)                              |
| **Build Tool**     | Maven 3.9, JaCoCo 0.8.14                                    |
| **Containerization**| Docker (multi-stage build), Docker Compose                  |
| **CI/CD**          | GitHub Actions, Codecov                                      |
| **Observability**  | Spring Boot Actuator                                         |
| **Other**          | Lombok, Commons Codec, Spring RestClient                     |

## 🧪 Testing Strategy & Quality Assurance

The project employs a robust testing pyramid with clear separation between unit and integration tests:

* **Unit Tests (18 classes):** Pure domain and infrastructure logic tested in isolation — covering domain models (`Payment`, `OutboxEvent`), custom exceptions, mappers, adapters, and the `OutboxProcessor`/`OutboxEventSender` pipeline. All tests use JUnit 5 and Mockito.
* **Integration Tests (5 classes):** Full Spring context tests validating controller endpoints (`PaymentController`, `HealthCheckController`), global exception handling, and TPay gateway adapter behavior with `spring-boot-starter-webmvc-test` and `spring-boot-starter-data-jpa-test`.
* **Code Coverage Gate:** JaCoCo enforces a strict minimum of **80% instruction coverage** at the bundle level — the build fails if coverage drops below the threshold.

## 🛡️ CI/CD Pipeline

The project uses **GitHub Actions** for Continuous Integration with automated quality gates:

```yaml
# Triggered on push/PR to master
- Checkout code
- Set up Java 25 (Temurin)
- Build & run tests (mvn verify)
- Upload JaCoCo coverage report (artifact, 7-day retention)
- Upload coverage to Codecov (fail_ci_if_error: true)
```

* **Build:** Maven `verify` phase runs all unit and integration tests with JaCoCo instrumentation.
* **Coverage Reporting:** JaCoCo HTML report is uploaded as a build artifact; XML report is pushed to Codecov for trend tracking and PR annotations.
* **Quality Gate:** Both JaCoCo (80% minimum) and Codecov (`fail_ci_if_error: true`) act as hard gates — a failing coverage check blocks the pipeline.

## 📊 Observability

The service exposes health and readiness endpoints via **Spring Boot Actuator**:

* **Health Endpoint:** `GET /actuator/health` — used by Docker healthcheck (`wget -qO- http://localhost:8081/actuator/health`) and orchestrators for readiness probing.
* **Custom Health Check:** `GET /health` — application-level health check controller returning service status.
* **JVM Tuning:** Container-aware JVM settings (`-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage=75.0`, G1GC) ensure stable memory behavior under constrained Docker resources.

## 🔮 Future Roadmap (Architectural Evolution)

Planned iterations for system evolution include:

* **Liquibase:** Replacing `schema.sql` with versioned, rollback-capable database migrations for safe multi-environment deployments.
* **Testcontainers:** Ephemeral MySQL instances in integration tests via [Testcontainers](https://testcontainers.com/) — fully isolated, reproducible runs without external DB dependencies.
* **GCP Cloud Run + IAP:** Replacing ngrok with [Cloud Run](https://cloud.google.com/run) and Identity-Aware Proxy for secure, stable webhook exposure during development.
* **Event-Driven Outbox:** Transitioning from polling (`OutboxProcessor`) to a message broker (Kafka/RabbitMQ) for lower latency event dispatch.
* **OpenAPI/Swagger UI:** Interactive API documentation and contract-first development.
* **Kubernetes Manifests:** Production-grade K8s Deployments, Services, ConfigMaps, and Secrets.
* **Idempotency Keys:** API-level idempotency to prevent duplicate payment charges on retried requests.

## 🚀 Getting Started (Local Environment)

### Prerequisites
* Docker & Docker Compose v2+
* Java 25+ (if running outside containers)
* Maven 3.9+ (if running outside containers)

### 1. Environment Configuration
Create a `.env` file in the project root with the following variables:

```bash
# MySQL
PAYMENT_SERVICE_MYSQL_DB_ROOT_PASSWORD=your_root_password
PAYMENT_SERVICE_MYSQL_DB_NAME=payment_db
PAYMENT_SERVICE_MYSQL_DB_USER=payment_user
PAYMENT_SERVICE_MYSQL_DB_PASSWORD=your_password
PAYMENT_SERVICE_MYSQL_DB_PORT=3306
PAYMENT_SERVICE_MYSQL_DB_HOST=payment-mysql
PAYMENT_SERVICE_MYSQL_INNODB_BUFFER_POOL_SIZE=256M
PAYMENT_SERVICE_MYSQL_MAX_CONNECTIONS=100

# Application
PAYMENT_SERVICE_PORT=8081
PAYMENT_SERVICE_APPLICATION_NAME=payment-service

# TPay (sandbox: https://panel.sandbox.tpay.com/)
TPAY_API_URL=https://api.tpay.com
PAYMENT_SERVICE_TPAY_API_CLIENT_ID=your_client_id
PAYMENT_SERVICE_TPAY_API_CLIENT_SECRET=your_client_secret
PAYMENT_SERVICE_TPAY_API_SECURITY_CODE=your_security_code
PAYMENT_SERVICE_TPAY_APP_NOTIFICATION_URL=https://yourdomain.com/api/payments/notifications
PAYMENT_SERVICE_TPAY_APP_RETURN_SUCCESS_URL=https://yourdomain.com/payment/success
PAYMENT_SERVICE_TPAY_APP_RETURN_ERROR_URL=https://yourdomain.com/payment/error
```

> **💡 TPay Sandbox:** You can register and configure your TPay test credentials at [panel.sandbox.tpay.com](https://panel.sandbox.tpay.com/). The sandbox environment allows full payment flow testing without real transactions.

### 2. Bootstrapping the Infrastructure
Spin up the database and service with a single command:

```bash
docker-compose up -d --build
```

### 3. Verification
* Payment Service API: `http://localhost:8081`
* Health Check: `http://localhost:8081/actuator/health`
* MySQL: `localhost:3306` (via configured port)

### 4. Running Tests Locally

```bash
mvn verify
```

Coverage report will be generated at `target/site/jacoco/index.html`.

## 📂 Repository Structure

```text
.
├── .github/
│   └── workflows/
│       └── ci.yml                        # GitHub Actions CI pipeline
├── src/
│   ├── main/
│   │   ├── java/com/rzodeczko/paymentservice/
│   │   │   ├── application/
│   │   │   │   ├── port/input/           # Inbound ports (PaymentUseCase)
│   │   │   │   ├── port/output/          # Outbound ports (PaymentGatewayPort, NotificationPort)
│   │   │   │   └── service/              # Domain services (PaymentService)
│   │   │   ├── domain/
│   │   │   │   ├── exception/            # Domain exceptions
│   │   │   │   ├── model/                # Domain models (Payment, OutboxEvent)
│   │   │   │   └── repository/           # Repository interfaces
│   │   │   ├── infrastructure/
│   │   │   │   ├── configuration/        # Spring beans & TPay properties
│   │   │   │   ├── gateway/tpay/         # TPay adapter & DTOs
│   │   │   │   ├── notification/         # External notification adapter
│   │   │   │   ├── outbox/               # Outbox processor & sender
│   │   │   │   ├── persistence/          # JPA entities, mappers, adapters
│   │   │   │   ├── transaction/          # Transaction boundary
│   │   │   │   └── usecase/              # Use case implementations
│   │   │   └── presentation/
│   │   │       ├── controller/           # REST controllers
│   │   │       ├── dto/                  # Request/Response DTOs
│   │   │       └── exception/            # Global exception handler
│   │   └── resources/
│   │       ├── application.yaml          # Application configuration
│   │       └── schema.sql                # ShedLock table schema
│   └── test/                             # Unit & integration tests
├── .env                                  # Environment variables
├── docker-compose.yml                    # Local container orchestration
├── Dockerfile                            # Multi-stage Docker build
└── pom.xml                               # Maven build configuration
```

## 🤝 Contact
Designed and implemented by **Rzodeczko**.
Feel free to check out my other projects on [GitHub](https://github.com/CoderNoOne).
