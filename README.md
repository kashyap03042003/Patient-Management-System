# Patient Management System

A microservices-based patient management platform built with **Spring Boot 4**, **Java 17**, **gRPC**, **Kafka**, and **PostgreSQL**, fronted by a **Spring Cloud Gateway** with JWT authentication. Infrastructure is defined as code with **AWS CDK** and can be deployed locally to **LocalStack**.

## Architecture

```
                    ┌──────────────────┐
   Client ───────▶  │   API Gateway    │ :4004   (JWT validation)
                    └───┬──────────┬───┘
              /auth/**  │          │  /api/patients/**
                        ▼          ▼
              ┌──────────────┐  ┌──────────────────┐  gRPC   ┌──────────────────┐
              │ Auth Service │  │ Patient Service  │ ──────▶ │ Billing Service  │
              │    :4005     │  │      :4000       │         │ :4001 / gRPC 9001│
              └──────┬───────┘  └───┬──────────┬───┘         └──────────────────┘
                     │              │          │ Kafka topic "patient"
                 PostgreSQL     PostgreSQL     ▼
                                          ┌───────────────────┐
                                          │ Analytics Service │ :4002
                                          └───────────────────┘
```

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 4004 | Single entry point; routes requests and validates JWTs on protected routes |
| **auth-service** | 4005 | User login and token validation (JWT, Spring Security, PostgreSQL) |
| **patient-service** | 4000 | Patient CRUD (JPA/PostgreSQL); creates billing accounts over gRPC and publishes `PatientEvent`s to Kafka |
| **billing-service** | 4001 / 9001 | gRPC server exposing `BillingService.CreateBillingAccount` |
| **analytics-service** | 4002 | Kafka consumer processing patient events |
| **infrastructure** | – | AWS CDK stack (VPC, ECS Fargate, RDS, MSK, ALB) targeting LocalStack |
| **integration-tests** | – | REST Assured end-to-end tests against the gateway |

## Tech Stack

- Java 17, Spring Boot 4, Spring Web MVC, Spring Data JPA, Spring Security
- Spring Cloud Gateway (WebFlux)
- gRPC + Protocol Buffers (service-to-service calls and Kafka event payloads)
- Apache Kafka (KRaft mode)
- PostgreSQL 16 (H2 for unit tests)
- JJWT, springdoc-openapi (Swagger)
- Docker / Docker Compose, AWS CDK, LocalStack
- JUnit 5, REST Assured

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+ and Maven (or use the bundled `mvnw` wrappers)

### Run with Docker Compose

```bash
docker compose up --build
```

This starts all services plus their dependencies:

| Tool | URL | Purpose |
|---|---|---|
| API Gateway | http://localhost:4004 | Public API |
| Adminer | http://localhost:8080 | Database admin UI |
| AKHQ | http://localhost:8081 | Kafka UI |
| Kafka (external) | localhost:9094 | Broker for local clients |

## API

All requests go through the gateway at `http://localhost:4004`.

### Authentication

```http
POST /auth/login
Content-Type: application/json

{ "email": "testuser@test.com", "password": "password123" }
```

Returns `{ "token": "<JWT>" }`. A test user is seeded by `auth-service/src/main/resources/data.sql`.

`GET /auth/validate` with `Authorization: Bearer <token>` checks a token.

### Patients (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/patients` | List patients |
| POST | `/api/patients` | Create a patient (also creates a billing account and emits a Kafka event) |
| PUT | `/api/patients/{id}` | Update a patient |
| DELETE | `/api/patients/{id}` | Delete a patient |

### API Docs

- Patient service OpenAPI: http://localhost:4004/api-docs/patients
- Auth service OpenAPI: http://localhost:4004/api-docs/auth

Sample HTTP requests live in [`api-requests/`](api-requests) and gRPC requests in [`grpc-requests/`](grpc-requests).

## gRPC & Events

**Billing** (`billing_service.proto`):

```proto
service BillingService {
  rpc CreateBillingAccount (BillingRequest) returns (BillingResponse);
}
```

**Patient events** (`patient_event.proto`) are published to the Kafka topic `patient` and consumed by the analytics service:

```proto
message PatientEvent {
  string patientId = 1;
  string name = 2;
  string email = 3;
  string event_type = 4;
}
```

## Testing

Unit tests per service:

```bash
cd patient-service && ./mvnw test
```

Integration tests (with the stack running):

```bash
cd integration-tests && mvn test
```

## Infrastructure (AWS CDK + LocalStack)

The `infrastructure` module defines a CDK stack (`LocalStack.java`) that provisions a VPC, ECS Fargate services for each microservice, RDS PostgreSQL databases, an MSK Kafka cluster, and an Application Load Balancer for the gateway. Synthesizing it produces the CloudFormation template in `cdk.out/localstack.template.json`, which can be deployed to LocalStack.

```bash
cd infrastructure && mvn compile exec:java
```

## Project Structure

```
.
├── analytics-service/    # Kafka consumer
├── api-gateway/          # Spring Cloud Gateway + JWT filter
├── auth-service/         # Authentication & JWT
├── billing-service/      # gRPC billing server
├── patient-service/      # Patient REST API
├── infrastructure/       # AWS CDK stack
├── integration-tests/    # End-to-end tests
├── api-requests/         # Sample HTTP requests
├── grpc-requests/        # Sample gRPC requests
└── compose.yaml          # Local Docker Compose setup
```

> **Note:** Credentials and the JWT secret in `compose.yaml` are for local development only. Use proper secret management in any real deployment.
