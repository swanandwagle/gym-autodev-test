# Fitness Class Booking System

Backend-only REST API for a gym fitness class booking system.
Stack: Java 21, Spring Boot 3.3, PostgreSQL 16, Flyway, Testcontainers, ArchUnit.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for local PostgreSQL and integration tests)

## Running Locally

1. Start PostgreSQL:

   ```
   docker compose up -d
   ```

2. Build and run the application:

   ```
   mvn spring-boot:run
   ```

3. Verify the service is healthy:

   ```
   curl http://localhost:8080/actuator/health
   ```

   Expected response: `{"status":"UP",...}`

## Running the Test Suite

All tests (unit + integration + ArchUnit) run with a single command.
Testcontainers starts its own PostgreSQL containers automatically — no manual Docker setup needed for tests.

```
mvn verify
```

To run only unit tests (no Testcontainers):

```
mvn test -Dgroups="unit"
```

To run a specific test class:

```
mvn test -Dtest=ArchitectureTest
```

## Project Structure

```
src/main/java/com/studio/booking/
  shared/time/     ClockConfig, StudioTimeZone
  shared/web/      PageResponse
  member/          api, application, domain, infrastructure (placeholders)
  membership/      (placeholder)
  catalog/         (placeholder)
  booking/         (placeholder)
  reporting/       (placeholder)
  jobs/            (placeholder)
```

## Architecture Rules (enforced by ArchUnit)

- `api → application → domain ← infrastructure` (no upward dependencies)
- `@Transactional` only in `application` packages
- `@RestController` only in `api` packages
- No direct cross-module class dependencies (modules communicate via IDs only)

ArchUnit violations break the build — run `mvn test -Dtest=ArchitectureTest` to check.
