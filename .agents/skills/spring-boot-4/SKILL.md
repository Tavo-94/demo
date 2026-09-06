---
name: spring-boot-4
description: >
  Master orchestrator skill for Spring Boot 4 development. Use this as the entry point for 
  all Spring Boot 4 tasks. It provides a directory of 21 highly-specialized sub-skills 
  that must be consulted based on the specific feature being developed.
---

# Spring Boot 4 Master Orchestrator

This is the master directory for Spring Boot 4 development. **DO NOT** attempt to guess the conventions for specific features. Instead, identify the relevant sub-skills below and read their `SKILL.md` files before generating code.

## Sub-Skill Directory

### 🏗️ Architecture & Core Patterns
- **`layered-architecture`**: Strict separation of Controller, Service, and Repository layers. DTOs and mappers.
- **`rest-api-conventions`**: Standard `ApiResponse` envelopes, HTTP status mapping, pagination, native API versioning.
- **`problem-details-rfc9457`**: RFC 9457 standard error responses.
- **`transactional-patterns`**: `@Transactional` propagation, read-only optimizations, Saga patterns.
- **`webflux-reactive-patterns`**: Project Reactor, WebFlux controllers, and R2DBC persistence.

### 💾 Data & Persistence
- **`spring-data-jpa`**: Hibernate 7, entities, repositories, relationships, N+1 prevention.
- **`spring-data-redis`**: Caching, session storage, and rate limiting.
- **`multi-tenancy`**: Tenant routing and data isolation.

### 🔒 Security
- **`spring-security-jwt`**: First-party JWT issuance and validation, method security.
- **`oauth2-resource-server`**: Third-party JWT validation (Keycloak, Auth0, etc.).

### 🌐 Integration & Gateway
- **`openapi-first`**: OpenAPI spec and code generation.
- **`spring-cloud-gateway`**: Gateway routes, edge auth, and resilience.
- **`spring-ai-integration`**: LLMs, RAG, embeddings, and chat clients.
- **`mcp-server`**: Model Context Protocol integrations.

### ⚙️ Background & Reliability
- **`spring-batch`**: ETL pipelines, chunk processing, scheduled imports.
- **`resilience-retry`**: Retries, backoff, and concurrency limits using Framework 7 `@Retryable`.
- **`production-observability`**: Actuator, Micrometer, OTLP, and health probes.

### 🛠️ Project Structure & Maintenance
- **`testing-pyramid`**: Unit, slice, and integration tests using Testcontainers and Mockito.
- **`multi-module-maven`**: Maven reactor structure and dependency management.
- **`null-safety`**: JSpecify nullability constraints.
- **`spring-boot-migration`**: Upgrading from Boot 3.x to Boot 4.
