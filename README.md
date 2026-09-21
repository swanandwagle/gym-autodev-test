# Fitness Class Booking System

Backend-only REST API for a gym fitness class booking system. Spring Boot 3 / Java 21 / PostgreSQL 16.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for local development and integration tests)

## Running Locally

1. Start PostgreSQL with Docker Compose:

   ```bash
   docker compose up -d
   ```

2. Build and run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

   Or on Windows:

   ```cmd
   mvnw.cmd spring-boot:run
   ```

3. Verify the application is healthy:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

   Expected response:

   ```json
   {"status":"UP"}
   ```

## Running the Test Suite

Run all tests (requires Docker for Testcontainers):

```bash
./mvnw verify
```

Run only unit tests (no Docker required):

```bash
./mvnw test -Dgroups=unit
```

Run ArchUnit architecture tests:

```bash
./mvnw test -Dtest="ArchitectureTest"
```

## Configuration

Key configuration properties in `src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `studio.timezone` | `UTC` | IANA timezone for recurrence and report date-bucketing |
| `spring.jpa.open-in-view` | `false` | Prevents lazy-load outside transactions |
| `server.port` | `8080` | HTTP port |

## Project Structure

```
com.studio.booking
├── shared/
│   ├── time/           # ClockConfig, ClockProvider, StudioTimeZone
│   └── web/            # PageResponse
├── member/             # (future)
├── membership/         # (future)
├── catalog/            # (future)
├── booking/            # (future)
├── reporting/          # (future)
└── jobs/               # (future)
```

## Architecture Rules (enforced by ArchUnit)

1. `api` layer must not depend on `infrastructure` layer
2. `@Transactional` only in `application` packages
3. `@Repository` classes must not be `@Transactional`
4. `application` layer must not depend on `api` layer

Violations break the build via `./mvnw verify`.
