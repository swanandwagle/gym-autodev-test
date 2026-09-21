# Fitness Class Booking System

Backend-only REST API for a gym fitness class booking system built with Java 21 and Spring Boot 3.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for local PostgreSQL and integration tests)

## Running Locally

1. Start PostgreSQL with Docker Compose:
   ```bash
   docker compose up -d
   ```

2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

3. Verify the application is healthy:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
   Expected response: `{"status":"UP",...}`

4. Stop the database:
   ```bash
   docker compose down
   ```

## Running the Test Suite

The tests require Docker to be running (Testcontainers starts a PostgreSQL container automatically).

Run all tests:
```bash
./mvnw test
```

Run a specific test class:
```bash
./mvnw test -Dtest=ArchitectureTest
```

Run tests matching a pattern:
```bash
./mvnw test -Dtest="*ClockConfig*"
```

### Test categories

| Test class | What it covers |
|---|---|
| `ArchitectureTest` | ArchUnit rules: layer dependencies, `@Transactional` placement, repository conventions |
| `ClockConfigTest` | Clock bean is injectable and replaceable with `Clock.fixed` |
| `StudioTimeZoneTest` | `studio.timezone` config property is bound |
| `PageResponseSerializationTest` | Page envelope JSON shape |
| `OpenInViewTest` | `spring.jpa.open-in-view=false` — lazy load outside transaction throws |
| `PostgresConnectivityTest` | Testcontainers PostgreSQL starts; Flyway V1 migration runs |

## Configuration

Key properties in `src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `studio.timezone` | `UTC` | IANA timezone for recurrence and report date-bucketing |
| `spring.jpa.open-in-view` | `false` | Prevents silent lazy-loading outside transactions |

## Project Structure

```
src/main/java/com/studio/booking/
├── shared/
│   ├── time/           # ClockConfig, StudioTimeZone
│   └── web/            # PageResponse
├── member/             # api / application / domain / infrastructure
├── membership/
├── catalog/
├── booking/
├── reporting/
└── jobs/
```
