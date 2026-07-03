# StrataResolve

A multi-property building maintenance and accountability platform that replaces informal communication channels (WhatsApp, phone calls, verbal complaints) with a structured system for reporting, tracking, and resolving maintenance issues in apartment buildings, condominiums, and managed properties.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Docker Compose (Recommended)](#docker-compose-recommended)
  - [Manual Setup](#manual-setup)
- [Environment Variables](#environment-variables)
- [API Documentation](#api-documentation)
- [Testing](#testing)
- [Database Migrations](#database-migrations)

## Features

- Multi-property management with full data isolation between properties
- Structured ticket workflow with configurable status transitions
- SLA policy configuration and automated breach detection
- Role-based access control (Platform Admin, Property Manager, Committee Member, Resident, Technician, Vendor)
- Vendor management with work order tracking
- Email notifications via transactional outbox pattern
- Immutable audit trail for all significant actions
- Reports with CSV export (ageing, SLA compliance, vendor performance)
- Duplicate ticket detection and submission rate limiting
- File attachments with type/size validation

## Tech Stack

### Backend

| Technology | Purpose |
|---|---|
| Java 21 | Language runtime |
| Spring Boot 3.4 | Application framework |
| Spring Security | Authentication and authorization |
| Spring Data JPA | Data access layer |
| PostgreSQL | Primary database |
| Flyway | Database migration management |
| JJWT | JWT token generation and validation |
| Lombok | Boilerplate reduction |
| jqwik | Property-based testing |
| Testcontainers | Integration testing with real database |

### Frontend

| Technology | Purpose |
|---|---|
| Vue 3 | UI framework |
| TypeScript | Type-safe JavaScript |
| Vite | Build tool and dev server |
| PrimeVue 4 | Component library |
| Tailwind CSS 4 | Utility-first CSS |
| Pinia | State management |
| TanStack Query | Server state caching and mutations |
| VeeValidate + Zod | Form validation |
| Chart.js | Report visualizations |
| Axios | HTTP client |

## Architecture

StrataResolve is built as a **modular monolith** — organized by business domain into independent modules that share a single deployment unit and database.

```
┌─────────────────────────────────────────────────────────────┐
│                     Vue 3 SPA (Frontend)                     │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTP + JWT
┌──────────────────────────────┼──────────────────────────────┐
│            Spring Boot Application (Backend)                 │
│                                                             │
│  ┌─────────┐ ┌────────┐ ┌──────┐ ┌────────┐ ┌──────────┐  │
│  │  User   │ │Property│ │Ticket│ │  SLA   │ │  Vendor  │  │
│  │ Module  │ │ Module │ │Module│ │ Module │ │  Module  │  │
│  └─────────┘ └────────┘ └──────┘ └────────┘ └──────────┘  │
│  ┌─────────────┐ ┌──────────┐ ┌───────────────────────┐    │
│  │Notification │ │  Audit   │ │      Reporting        │    │
│  │   Module    │ │  Module  │ │       Module          │    │
│  └─────────────┘ └──────────┘ └───────────────────────┘    │
│                                                             │
│  ┌─────────────────── Shared Infrastructure ─────────────┐  │
│  │ TenantContext │ EventBus │ FileStorage │ ErrorHandler │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │    PostgreSQL DB     │
                    └─────────────────────┘
```

### Key Design Decisions

- **Shared-schema multi-tenancy**: All data lives in a single PostgreSQL schema with `property_id` columns. A Hibernate filter ensures data isolation per request.
- **Status workflow engine**: Ticket state transitions are validated against a configurable adjacency map at the domain level.
- **Notification outbox pattern**: Notifications are written to a database table within the same transaction as the triggering event, then processed asynchronously for delivery reliability.
- **JWT with refresh token rotation**: Short-lived access tokens paired with rotatable refresh tokens for session management.
- **Sequential reference numbers**: Generated using PostgreSQL `SELECT ... FOR UPDATE` to guarantee gap-free, globally unique ticket identifiers (SR-YYYY-NNNNNN).

## Project Structure

```
StrataResolve/
├── src/main/java/com/strataresolve/
│   ├── StrataResolveApplication.java        # Application entry point
│   ├── user/                                # User registration, auth, memberships
│   │   ├── controller/                      #   REST endpoints (AuthController)
│   │   ├── domain/                          #   Entities (User, Membership, RefreshToken)
│   │   ├── dto/                             #   Request/Response DTOs
│   │   ├── repository/                      #   Spring Data JPA repositories
│   │   └── service/                         #   Business logic (AuthService, MembershipService)
│   ├── property/                            # Property, Block, Unit management
│   │   ├── controller/                      #   CRUD endpoints
│   │   ├── domain/                          #   Entities (Property, Block, Unit)
│   │   ├── dto/
│   │   ├── repository/
│   │   └── service/
│   ├── ticket/                              # Core ticket lifecycle
│   │   ├── controller/                      #   Ticket, Assignment, Comment, Attachment, Duplicate
│   │   ├── domain/                          #   Entities (Ticket, StatusHistory, Comment, etc.)
│   │   ├── dto/
│   │   ├── policy/                          #   StatusWorkflowEngine, transition rules
│   │   ├── repository/
│   │   ├── service/                         #   TicketService, ReferenceNumberGenerator
│   │   └── config/
│   ├── sla/                                 # SLA policy and breach monitoring
│   │   ├── controller/                      #   SLA policy CRUD
│   │   ├── domain/                          #   SlaPolicy entity
│   │   ├── dto/
│   │   ├── repository/
│   │   └── service/                         #   SlaCalculator, SlaMonitorScheduler
│   ├── vendor/                              # Vendor and work order management
│   │   ├── controller/                      #   Vendor CRUD, WorkOrder lifecycle
│   │   ├── domain/                          #   Vendor, WorkOrder entities
│   │   ├── dto/
│   │   ├── repository/
│   │   └── service/
│   ├── notification/                        # Email notifications via outbox
│   │   ├── domain/                          #   Notification entity
│   │   ├── repository/
│   │   └── service/                         #   OutboxService, Processor, EmailSender
│   ├── audit/                               # Immutable audit trail
│   │   ├── controller/                      #   Audit query endpoint
│   │   ├── domain/                          #   AuditEvent entity
│   │   ├── dto/
│   │   ├── repository/
│   │   └── service/                         #   AuditService, AuditEventListener
│   ├── reporting/                           # Reports and CSV export
│   │   ├── controller/                      #   Report endpoints
│   │   ├── dto/
│   │   └── service/                         #   Ageing, SLA, VendorPerformance, CSV
│   └── shared/                              # Cross-cutting infrastructure
│       ├── config/                          #   Security, CORS, rate limiting config
│       ├── event/                           #   Domain event bus and event types
│       ├── exception/                       #   Typed exceptions, GlobalExceptionHandler
│       ├── filestorage/                     #   FileStorageService interface + local impl
│       └── tenant/                          #   TenantContextFilter, TenantContext
├── src/main/resources/
│   ├── application.yml                      # Shared configuration
│   ├── application-dev.yml                  # Development profile
│   ├── application-test.yml                 # Test profile
│   ├── application-prod.yml                 # Production profile
│   └── db/migration/                        # Flyway SQL migrations
│       ├── V1__core_schema.sql
│       ├── V2__refresh_tokens.sql
│       ├── V3__duplicate_detection.sql
│       └── V4__vendor_membership.sql
├── src/test/java/com/strataresolve/         # Tests mirror module structure
│   ├── IntegrationTestBase.java             # Testcontainers base class
│   └── {module}/                            # Unit + property-based tests per module
├── frontend/                                # Vue 3 SPA
│   ├── src/
│   │   ├── views/                           # Page components (auth, dashboard, tickets, admin)
│   │   ├── components/                      # Reusable UI components
│   │   ├── composables/                     # Vue composables (form validation, etc.)
│   │   ├── stores/                          # Pinia stores (auth, property)
│   │   ├── services/                        # API client and interceptors
│   │   ├── router/                          # Vue Router with role-based guards
│   │   ├── types/                           # TypeScript interfaces
│   │   ├── validation/                      # Zod schemas mirroring backend validation
│   │   └── plugins/                         # TanStack Query client setup
│   ├── package.json
│   └── tsconfig.json
├── build.gradle.kts                         # Gradle build configuration
├── settings.gradle.kts
└── docker-compose.yml                       # Local development services
```

## Getting Started

### Prerequisites

- **Java 21** (JDK)
- **Node.js 20+** and npm
- **PostgreSQL 15+** (or Docker)
- **Docker** and **Docker Compose** (for containerized setup)

### Docker Compose (Recommended)

The fastest way to get the full stack running locally:

```bash
# Clone the repository
git clone https://github.com/your-org/StrataResolve.git
cd StrataResolve

# Start all services (backend, frontend, PostgreSQL)
docker compose up -d

# View logs
docker compose logs -f
```

Services will be available at:
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **PostgreSQL**: localhost:5432

To stop all services:

```bash
docker compose down
```

### Manual Setup

#### 1. Database

Start PostgreSQL and create the database:

```bash
# Using Docker for just the database
docker run -d \
  --name strataresolve-db \
  -e POSTGRES_DB=strataresolve \
  -e POSTGRES_USER=strataresolve \
  -e POSTGRES_PASSWORD=strataresolve \
  -p 5432:5432 \
  postgres:15

# Or create manually on an existing PostgreSQL instance
createdb strataresolve
```

#### 2. Backend

```bash
# From the project root
./gradlew bootRun

# The backend starts on http://localhost:8080
# Flyway migrations run automatically on startup
```

#### 3. Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start the dev server
npm run dev

# The frontend starts on http://localhost:5173
```

#### 4. Build for Production

```bash
# Backend JAR
./gradlew build

# Frontend static assets
cd frontend
npm run build
# Output in frontend/dist/
```

## Environment Variables

### Backend

| Variable | Description | Default |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `strataresolve` |
| `DB_USERNAME` | Database username | `strataresolve` |
| `DB_PASSWORD` | Database password | `strataresolve` |
| `JWT_SECRET` | Secret key for JWT signing (min 32 bytes) | *(required in prod)* |
| `MAIL_HOST` | SMTP server host | `localhost` |
| `MAIL_PORT` | SMTP server port | `1025` |
| `MAIL_USERNAME` | SMTP username | *(empty)* |
| `MAIL_PASSWORD` | SMTP password | *(empty)* |
| `MAIL_SMTP_AUTH` | Enable SMTP authentication | `false` |
| `MAIL_SMTP_STARTTLS` | Enable STARTTLS | `false` |
| `FILE_STORAGE_PATH` | Local file storage directory | `./uploads` |
| `FILE_MAX_SIZE_BYTES` | Maximum upload file size | `10485760` (10MB) |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins (comma-separated) | `http://localhost:5173` |
| `NOTIFICATION_FROM_ADDRESS` | Email sender address | `noreply@strataresolve.com` |

### Application Configuration (application.yml)

Additional settings configurable via `application.yml`:

| Property | Description | Default |
|---|---|---|
| `app.jwt.access-token-expiration-ms` | Access token TTL | `900000` (15 min) |
| `app.jwt.refresh-token-expiration-ms` | Refresh token TTL | `604800000` (7 days) |
| `app.ticket.reopen-window-hours` | Hours after closure a ticket can be reopened | `72` |
| `app.ticket.rate-limit.max-submissions-per-period` | Max ticket submissions per period | `10` |
| `app.ticket.rate-limit.period-minutes` | Rate limit time window | `60` |
| `app.ticket.duplicate-detection.time-window-hours` | Window to check for duplicates | `48` |
| `app.ticket.duplicate-detection.similarity-threshold` | Similarity score threshold | `0.6` |
| `app.notification.max-retry-attempts` | Max email delivery retries | `5` |
| `app.notification.poll-interval-ms` | Outbox poll frequency | `30000` |
| `app.sla.monitor.poll-interval-ms` | SLA breach check frequency | `60000` |

### Spring Profiles

| Profile | Usage |
|---|---|
| `dev` | Local development with debug logging and SQL output |
| `test` | Automated tests with Testcontainers |
| `prod` | Production with connection pooling and minimal logging |

## API Documentation

All endpoints are prefixed with `/api`. Authentication uses Bearer JWT tokens in the `Authorization` header. Multi-tenant endpoints require an `X-Property-Id` header or property ID in the path.

### Authentication

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/api/auth/register` | Register a new user | Public |
| POST | `/api/auth/login` | Login and receive tokens | Public |
| POST | `/api/auth/refresh` | Refresh access token | Public |
| POST | `/api/auth/logout` | Logout and invalidate tokens | Authenticated |

### Properties

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/properties` | Create a property | Platform Admin |
| GET | `/api/properties` | List all properties | Platform Admin |
| GET | `/api/properties/{id}` | Get property details | Authenticated |
| PUT | `/api/properties/{id}` | Update a property | Platform Admin |
| PATCH | `/api/properties/{id}/deactivate` | Deactivate a property | Platform Admin |

### Blocks and Units

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/properties/{propertyId}/blocks` | Create a block | Property Manager |
| GET | `/api/properties/{propertyId}/blocks` | List blocks | Authenticated |
| PUT | `/api/properties/{propertyId}/blocks/{id}` | Update a block | Property Manager |
| POST | `/api/properties/{propertyId}/blocks/{blockId}/units` | Create a unit | Property Manager |
| GET | `/api/properties/{propertyId}/blocks/{blockId}/units` | List units | Authenticated |
| PUT | `/api/properties/{propertyId}/units/{id}` | Update a unit | Property Manager |

### Tickets

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/tickets` | Submit a new ticket | Resident |
| GET | `/api/tickets` | List all tickets in property | Authenticated |
| GET | `/api/tickets/my-tickets` | List resident's own tickets | Resident |
| GET | `/api/tickets/{id}` | Get ticket details | Authenticated |
| GET | `/api/tickets/reference/{ref}` | Get ticket by reference number | Authenticated |
| PATCH | `/api/tickets/{id}/status` | Transition ticket status | Role-dependent |
| POST | `/api/tickets/{id}/reopen` | Reopen a closed ticket | Resident |
| PATCH | `/api/tickets/{id}/category` | Change category | Property Manager |
| PATCH | `/api/tickets/{id}/priority` | Change priority | Property Manager |

### Comments

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/tickets/{ticketId}/comments` | Add a comment | Authenticated |
| GET | `/api/tickets/{ticketId}/comments` | List comments | Authenticated |

### Attachments

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/tickets/{ticketId}/attachments` | Upload a file | Authenticated |
| GET | `/api/tickets/{ticketId}/attachments` | List attachments | Authenticated |
| GET | `/api/attachments/{id}/download` | Download a file | Authenticated |

### Assignments

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/assignments` | Create an assignment | Property Manager |
| GET | `/api/assignments/ticket/{ticketId}` | List assignments for ticket | Authenticated |
| GET | `/api/assignments/assignee/{userId}` | List assignments for user | Authenticated |

### SLA Policies

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/properties/{propertyId}/sla-policies` | Create an SLA policy | Property Manager |
| GET | `/api/properties/{propertyId}/sla-policies` | List SLA policies | Property Manager |
| PUT | `/api/properties/{propertyId}/sla-policies/{id}` | Update an SLA policy | Property Manager |
| DELETE | `/api/properties/{propertyId}/sla-policies/{id}` | Delete an SLA policy | Property Manager |

### Vendors and Work Orders

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| POST | `/api/properties/{propertyId}/vendors` | Register a vendor | Property Manager |
| GET | `/api/properties/{propertyId}/vendors` | List vendors | Property Manager |
| PUT | `/api/properties/{propertyId}/vendors/{id}` | Update a vendor | Property Manager |
| POST | `/api/properties/{propertyId}/work-orders` | Create a work order | Property Manager |
| GET | `/api/properties/{propertyId}/work-orders` | List work orders | Property Manager |
| GET | `/api/properties/{propertyId}/work-orders/my-work-orders` | List vendor's own work orders | Vendor |
| POST | `/api/properties/{propertyId}/work-orders/{id}/accept` | Accept a work order | Vendor |
| POST | `/api/properties/{propertyId}/work-orders/{id}/complete` | Complete a work order | Vendor |
| POST | `/api/properties/{propertyId}/work-orders/{id}/evidence` | Upload evidence | Vendor |

### Duplicate Detection

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| GET | `/api/tickets/duplicates` | List flagged duplicates | Property Manager |
| POST | `/api/tickets/duplicates/link` | Link duplicate tickets | Property Manager |
| GET | `/api/tickets/duplicates/{ticketId}/links` | Get duplicate links | Property Manager, Committee |

### Audit Trail

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| GET | `/api/properties/{propertyId}/audit-trail` | Query audit events | Property Manager, Committee, Admin |

Query parameters: `eventType`, `actingUserId`, `targetEntityType`, `targetEntityId`, `from`, `to`

### Reports

| Method | Endpoint | Description | Roles |
|---|---|---|---|
| GET | `/api/properties/{propertyId}/reports/ageing` | Ticket ageing report | Property Manager, Committee |
| GET | `/api/properties/{propertyId}/reports/sla` | SLA compliance report | Property Manager, Committee |
| GET | `/api/properties/{propertyId}/reports/vendor-performance` | Vendor performance report | Property Manager, Committee |
| GET | `/api/properties/{propertyId}/reports/ageing/csv` | Ageing report CSV export | Property Manager, Committee |
| GET | `/api/properties/{propertyId}/reports/sla/csv` | SLA report CSV export | Property Manager, Committee |
| GET | `/api/properties/{propertyId}/reports/vendor-performance/csv` | Vendor report CSV export | Property Manager, Committee |

### Error Response Format

All API errors follow a consistent JSON structure:

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable error description",
  "code": "VALIDATION_FAILED",
  "details": [
    { "field": "email", "message": "must be a valid email address" }
  ]
}
```

## Testing

### Run Unit and Property-Based Tests

```bash
./gradlew test
```

This runs both JUnit 5 unit tests and jqwik property-based tests.

### Run Integration Tests

Integration tests use Testcontainers and require Docker:

```bash
./gradlew integrationTest
```

### Frontend Tests

```bash
cd frontend
npm run build   # Type-checking via vue-tsc
```

### Test Organization

- **Property-based tests (jqwik)**: Verify universal correctness properties across random inputs (e.g., status transitions, SLA calculations, reference number generation)
- **Unit tests**: Verify specific scenarios for services, controllers, and domain logic
- **Integration tests**: End-to-end workflows against a real PostgreSQL instance via Testcontainers

## Database Migrations

Flyway manages all schema changes. Migrations run automatically on application startup.

| Migration | Description |
|---|---|
| `V1__core_schema.sql` | All entity tables, indexes, constraints, enumerations, and reference number sequence |
| `V2__refresh_tokens.sql` | Refresh token storage for JWT rotation |
| `V3__duplicate_detection.sql` | Duplicate ticket detection support tables |
| `V4__vendor_membership.sql` | Vendor membership associations |

To add a new migration, create a file following the naming convention:
```
src/main/resources/db/migration/V{N}__{description}.sql
```

## License

This project is proprietary software. All rights reserved.
