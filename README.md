# SmartPark

SmartPark is a parking management system with a Spring Boot API and a
React/Vite web client.

## Repository layout

- `backend/` contains the Spring Boot application, Maven build, and backend
  environment template.
- `frontend/client/` contains the React/Vite application. The older standalone
  development console remains under `frontend/`.
- `database/migrations/` contains Flyway schema migrations; `database/seeds/`
  contains local and test seed data.
- `hosting/` contains the Docker, Render, and Vercel deployment configuration.

Maven packages migrations, seeds, and the built frontend into the backend
classpath/JAR, so those files remain separate in the source tree while the
application runs as one service.

## Local development

Requirements: Java 25, Node.js 20.19+, and npm. The `local-h2` Maven profile
uses an isolated in-memory H2 database and does not connect to MySQL.

Start the backend in one terminal:

```sh
cd backend
SMARTPARK_JWT_SECRET="$(openssl rand -hex 32)" ./mvnw -Plocal-h2 test-compile spring-boot:run
```

Start the frontend in another:

```sh
cd frontend/client
npm ci
npm run dev
```

Open `http://127.0.0.1:5173`; Vite proxies `/api` requests to the backend on
port `8080`. To use MySQL instead of H2, configure the values in
`backend/.env.example` in the backend process environment. Do not commit real
credentials.

The VS Code **Start SmartPark Backend (seeded H2)** task prompts for an admin
password and provisions `admin@smartpark01`. The password is BCrypt-hashed
before it is saved; it is not stored in the repository. The task explicitly
resets the password for an existing account. The H2 task uses an in-memory
database, so its account does not survive a restart. For persistent logins,
configure MySQL variables from `backend/.env.example` in the process environment
and use **Start SmartPark Backend (MySQL; bootstrap admin)** once. It stores
only the BCrypt hash in MySQL. Then use **Start SmartPark Backend (MySQL)**
without bootstrap settings. Keep credentials in a secret store, not in source
files.

## Build and hosting

Build and run the combined application JAR:

```sh
cd frontend/client
npm ci
npm run build
cd ../../backend
./mvnw package
java -jar target/smartpark-0.0.1-SNAPSHOT.jar
```

The portal and API are then served from `http://localhost:8080`. The Docker
build context is the repository root:

```sh
docker build -f hosting/Dockerfile -t smartpark .
```

The Render blueprint is `hosting/render.yaml`. For Vercel, use
`frontend/client/` as the project root; Vercel requires `vercel.json` and
`.vercelignore` in that project root for Git-based deployments. The equivalent
SPA rewrite configuration is also kept in `hosting/vercel.json` for direct CLI
deployments using `--local-config ../../hosting/vercel.json`.

For a free hosted preview, TiDB Cloud Starter can be used through its
MySQL-compatible protocol; it is not a MySQL server. Create an empty cluster
and configure Render's `SMARTPARK_DB_URL`, `SMARTPARK_DB_USERNAME`, and
`SMARTPARK_DB_PASSWORD` from TiDB's connection details. Keep the provider's TLS
settings in the JDBC URL. The API limits new database connection attempts to
10 seconds so unreachable or misconfigured endpoints fail with a visible
startup error. Flyway applies the schema migrations at API startup. Do not put
database credentials in this repository.

Set Render's `SMARTPARK_CORS_ALLOWED_ORIGINS` to the exact Vercel preview
origin, and set Vercel's Preview `VITE_API_BASE_URL` to the Render API origin.
Set `SMARTPARK_RESET_URL` to the Vercel origin plus `/forgot-password`. To
provision the initial admin, provide `SMARTPARK_ADMIN_BOOTSTRAP_EMAIL` and
`SMARTPARK_ADMIN_BOOTSTRAP_PASSWORD` as Render secrets for startup; the
password is stored as a BCrypt hash. To reset an existing admin password,
temporarily set `SMARTPARK_ADMIN_BOOTSTRAP_RESET_PASSWORD=true` and set it back
to `false` after the service starts successfully. The free Render service may
sleep when idle, and uploaded KYC documents use local storage, so they are not
durable across service replacement or restart.

### Email verification and password-reset mail

The backend sends registration and owner-verification codes, password-reset
messages, and owner-decision notifications through a configurable mail
provider. Both customer and parking-owner accounts are created only after the
registration email code is verified; owners then submit their identity document
for administrator review. Render Free services block outbound SMTP ports 25, 465, and 587, so
use Brevo's HTTPS API for the Render deployment. Create a Brevo API key and
verify the sender address in Brevo, then set `SMARTPARK_MAIL_PROVIDER=brevo`,
`SMARTPARK_BREVO_API_KEY` (as a secret), and `SMARTPARK_MAIL_FROM` to the
verified sender address in Render. `SMARTPARK_MAIL_FROM_NAME` defaults to
`SmartPark`.

For local development or a host that permits SMTP, set
`SMARTPARK_MAIL_PROVIDER=smtp`. Gmail SMTP requires 2-Step Verification and a
Google App Password. Set `SMARTPARK_MAIL_USERNAME` and `SMARTPARK_MAIL_FROM` to
the Gmail sender, `SMARTPARK_MAIL_PASSWORD` to the App Password, and keep
`SMARTPARK_MAIL_HOST=smtp.gmail.com`, `SMARTPARK_MAIL_PORT=587`,
`SMARTPARK_MAIL_AUTH=true`, and `SMARTPARK_MAIL_STARTTLS=true`. Do not use the
Gmail account's normal sign-in password or commit either provider's secret.

Verification codes expire after 10 minutes. If the provider rejects a send,
the API returns an email-delivery error instead of reporting that a code was
sent.

## Tests

```sh
cd backend
./mvnw test
cd ../frontend/client
npm test
npm run build
```
## Smart Parking Management and Reservation System

SmartPark is a web-based parking management platform designed to connect parking owners, customers, and administrators through a unified parking reservation system.

The platform provides a structured workflow for parking owner registration and verification, parking facility management, slot availability, customer reservations, payments, parking sessions, and booking management.

SmartPark supports three types of users:

- **Admin** – Manages the platform, verifies parking owners, and oversees system activities.
- **Owner** – Registers parking facilities, manages parking spaces, and handles bookings and cancellations.
- **Customer** – Registers, searches for parking, reserves parking slots, and manages bookings.

The platform provides an end-to-end parking workflow from owner registration to parking reservation, payment, vehicle entry, parking session management, and departure.

---

# Live Deployment

## Frontend

**SmartPark Web Application**

Deployment URL: Not yet confirmed.

## Backend

**SmartPark REST API**

Deployment URL: Not yet confirmed.

## Database

MySQL database.

---

# Key Features

## 1. Role-Based Authentication

SmartPark supports three different user roles.

- Admin authentication
- Owner registration and authentication
- Customer registration and authentication
- Role-based access control
- Protected application functionality
- Customer email OTP verification
- Owner document verification
- Owner application status management

## 2. Admin Dashboard

The admin dashboard provides administrative control over parking owner applications and platform operations.

Administrators can:

- View owner applications.
- Review submitted owner documents.
- Verify owner applications.
- Approve or reject owner applications.
- Manage owner approval status.
- Provide rejection reasons.
- Monitor parking-related operations.

## 3. Owner Registration and Verification

Parking owners can register their accounts and submit information for verification.

The verification workflow supports:

- Owner registration.
- Document upload.
- Automatic document verification.
- Manual verification when document verification is skipped.
- Admin approval.
- Admin rejection.
- Rejection reason tracking.
- Owner application status management.

## 4. Customer Registration

Customers can create accounts and access the parking reservation system.

Features include:

- Customer registration.
- Email OTP verification.
- Customer authentication.
- Booking access after registration and verification.

## 5. Parking Facility Management

Parking owners can manage their parking facilities and locations.

The system supports:

- Parking facility information.
- Parking locations.
- Parking spaces.
- Parking slot management.
- Slot availability management.
- Parking-related booking management.

## 6. Parking Slot Reservation

Customers can reserve available parking slots.

The reservation workflow supports:

- Parking slot selection.
- Reservation creation.
- Temporary reservation holds.
- Five-minute reservation hold period.
- Booking overlap validation.
- Prevention of conflicting reservations.
- Reservation management.

The system uses reservation holds to reduce the possibility of multiple customers booking the same parking slot simultaneously.

## 7. Booking Management

SmartPark manages the complete booking lifecycle.

Supported booking operations include:

- Create parking bookings.
- Manage reservations.
- Handle multiple-vehicle bookings.
- Support group bookings.
- Validate overlapping reservations.
- Manage booking status.
- Handle booking cancellation.

## 8. Parking Session Management

The application manages parking sessions separately from the initial reservation.

The session workflow supports:

- Parking entry.
- Active parking sessions.
- Parking duration tracking.
- Overdue parking sessions.
- Exit management.
- Parking completion.
- Slot retention until vehicle departure.

## 9. Payment Management

SmartPark includes parking payment management.

Payment-related functionality includes:

- Booking payment management.
- Payment status tracking.
- Payment reconciliation.
- Handling delayed payment confirmation.
- Parking fee calculation.
- Late parking fee calculation.

**Late Fee:** ₹20 per hour for overdue parking.

## 10. Cancellation and Refund Management

The platform supports booking cancellation and refund-related operations.

- Owner cancellation handling.
- Booking cancellation.
- Refund processing.
- Refund status management.
- Payment and refund reconciliation.

## 11. Collision Prevention

SmartPark includes reservation validation to prevent booking conflicts.

The system checks booking overlaps and reservation availability to avoid allocating the same parking slot to multiple vehicles for overlapping periods.

## 12. Parking Locations and Group Bookings

The application supports parking locations and multiple-vehicle booking requirements.

- Parking location management.
- Group bookings.
- Multiple-vehicle reservations.
- Booking validation.
- Reservation coordination.

## 13. Owner Approval Dashboard

The owner approval workflow allows administrators to manage owner verification.

Owner application statuses include:

- PENDING
- VERIFYING
- AUTO_APPROVED
- MANUALLY_APPROVED
- REJECTED

Rejected applications can maintain a rejection reason for administrative tracking.

## 14. Dashboard and Operational Management

SmartPark provides role-specific application functionality for administrators, owners, and customers.

The application supports the parking management workflow, including owner verification, parking facilities, reservations, sessions, and payment operations.

---

# Technology Stack

## Frontend

- React
- Vite
- JavaScript
- HTML
- CSS

## Backend

- Java
- Spring Boot
- Maven
- Spring Security

## Database

- MySQL

## Build and Deployment

- Docker
- Maven Wrapper
- npm
- Git
- GitHub
- Render (deployment target)

---

# System Architecture

SmartPark follows a frontend-backend architecture in which the React frontend communicates with the Java Spring Boot backend through REST APIs.

```text
                 ┌──────────────────────┐
                 │     SmartPark UI     │
                 │     React + Vite     │
                 └──────────┬───────────┘
                            │
                            │ HTTP / REST API
                            ▼
                 ┌──────────────────────┐
                 │    Spring Boot       │
                 │      Backend         │
                 └──────────┬───────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
          ▼                 ▼                 ▼
   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
   │    Admin    │   │    Owner    │   │  Customer   │
   │ Management  │   │ Management  │   │ Management  │
   └─────────────┘   └─────────────┘   └─────────────┘
          │                 │                 │
          └─────────────────┼─────────────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │   Booking and        │
                 │   Reservation        │
                 │   Management         │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │   Payment and        │
                 │   Parking Sessions   │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │       MySQL          │
                 │      Database        │
                 └──────────────────────┘
```

---

# Application Workflow

```text
             User Registration / Login
                       ↓
                 Role Selection
                       ↓
          ┌────────────┼────────────┐
          ↓            ↓            ↓
        Admin        Owner       Customer
          ↓            ↓            ↓
     Admin Panel   Registration   Email OTP
                       ↓            ↓
                 Document       Customer Login
                 Verification       ↓
                       ↓        Parking Search
                 Admin Approval      ↓
                       ↓         Slot Selection
                 Owner Dashboard     ↓
                       ↓         Reservation
                Parking Facility     ↓
                  Management     Payment
                       ↓            ↓
                  Slot Setup     Booking
                       └──────┬─────┘
                              ↓
                       Parking Session
                              ↓
                       Vehicle Entry
                              ↓
                       Parking Duration
                              ↓
                       Vehicle Departure
                              ↓
                       Payment Settlement
                              ↓
                       Booking Completion
```

---

# User Roles and Responsibilities

## Admin

- Manage owner applications.
- Review owner verification documents.
- Approve or reject owners.
- Track rejection reasons.
- Oversee parking platform operations.

## Owner

- Register as a parking owner.
- Submit verification documents.
- Manage parking facilities.
- Manage parking locations and spaces.
- Handle parking bookings.
- Manage cancellation-related operations.

## Customer

- Register and verify email.
- Access the parking platform.
- Reserve parking slots.
- Make booking payments.
- Manage parking reservations.
- Use parking sessions.
- Complete parking and departure.

---

# Booking and Reservation Lifecycle

SmartPark manages reservations and parking sessions as connected but separate processes.

The reservation lifecycle includes:

```text
Reservation Request
        ↓
Availability Validation
        ↓
Temporary Reservation Hold
        ↓
Payment Processing
        ↓
Booking Confirmation
        ↓
Parking Reservation
        ↓
Vehicle Arrival
        ↓
Parking Session
        ↓
Vehicle Departure
        ↓
Final Payment Reconciliation
        ↓
Booking Completion
```

The application also handles cancellation, refunds, delayed payment confirmation, and booking overlap validation.

---

# Payment and Pricing Management

SmartPark supports payment-related booking operations.

The pricing workflow considers:

- Parking booking charges.
- Parking session duration.
- Overdue parking duration.
- Late parking fees.
- Payment reconciliation.
- Refund-related operations.

**Late Parking Fee: ₹20 per hour.**

The parking slot remains retained until the vehicle departure process is completed.

---

# Database Management

SmartPark uses MySQL as its relational database.

The project includes database migration scripts for managing changes to the database structure.

The following migration updates are present:

| Migration | Functionality |
|---|---|
| V5 | Owner rejection reason |
| V6 | Customer email registration verification |
| V7 | Reservation holds and parking sessions |
| V8 | Group bookings, locations, and payment reconciliation |

These migrations support the evolution of SmartPark's parking management functionality.

---

# Backend API

The React frontend communicates with the Java Spring Boot backend through REST APIs.

The backend manages application operations such as:

- Authentication
- User management
- Owner verification
- Parking facilities
- Parking locations
- Parking slots
- Reservations
- Bookings
- Parking sessions
- Payments
- Refunds
- Email verification

Exact API endpoint paths are not listed here because they need to be taken from the actual Spring Boot controllers.

---

# Security

SmartPark includes application-level security and authorization.

Security-related functionality includes:

- Authentication and authorization.
- Role-based access control.
- Protected backend APIs.
- Owner verification.
- Customer email verification.
- Password-related security tests.
- API security tests.
- Validation of booking operations.

Sensitive credentials and environment configuration should not be committed to GitHub.

---

# Testing

The project contains tests covering important application operations.

The test coverage includes:

- API security testing.
- Booking testing.
- Owner verification testing.
- Password testing.
- Pricing testing.
- Application testing.

These tests help validate the core parking management and reservation workflows.

---

# Environment Configuration

The project contains an `.env.example` file for environment configuration.

For local development:

1. Configure the required application environment variables.
2. Configure the MySQL database connection.
3. Keep private credentials outside source control.
4. Do not commit `.env` files containing secrets.

Refer to the actual project configuration files for the exact environment variable names.

---

# Running the Application Locally

## Prerequisites

- Java 25
- Maven Wrapper
- Node.js and npm
- MySQL
- Git

## 1. Clone the Repository

```bash
git clone https://github.com/jawalkarisha-commits/SmartPark.git
```

## 2. Navigate to the Project

```bash
cd SmartPark
```

## 3. Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite development server will provide the local frontend URL.

## 4. Run the Backend

Open another terminal and navigate to the project root.

```bash
cd SmartPark
```

Run the Spring Boot application using Maven Wrapper:

```bash
./mvnw spring-boot:run
```

The backend runs on the configured application port.

---

# Building for Production

## Frontend Build

Navigate to the frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm ci
```

Generate the production build:

```bash
npm run build
```

## Backend Build

Navigate to the project root:

```bash
./mvnw -B -DskipTests package
```

The generated backend JAR is:

```text
target/smartpark-0.0.1-SNAPSHOT.jar
```

---

# Docker Configuration

SmartPark includes a Dockerfile for building the application.

The Docker configuration uses a multi-stage build process.

The build stages include:

1. React frontend dependency installation.
2. Frontend production build.
3. Java backend packaging using Maven Wrapper.
4. Java runtime image for application execution.

The Docker configuration uses:

| Component | Configuration |
|---|---|
| Frontend build image | Node 26 Bookworm Slim |
| Backend build image | Eclipse Temurin 25 JDK |
| Runtime image | Eclipse Temurin 25 JRE |
| Application port | 8080 |
| Backend packaging | Maven Wrapper |
| Frontend build | npm ci and npm run build |

Build the Docker image:

```bash
docker build -t smartpark .
```

Run the container:

```bash
docker run -p 8080:8080 smartpark
```

The application uses the `PORT` environment variable, with 8080 as its default port.

---

# Deployment

## Backend and Frontend

SmartPark includes a Docker-based application build configuration intended for deployment.

The Dockerfile builds the React frontend and Java Spring Boot backend.

## Deployment Platform

Render is the intended hosting platform.

Deployment status and public application URLs are not yet confirmed.

## Database

MySQL is used as the application database.

## Source Code

GitHub is used for source-code management.

---

# Git Branch Structure

The project repository is:

```text
SmartPark/
│
├── frontend/
│
├── src/
│   ├── main/
│   └── test/
│
├── .mvn/
│
├── .env.example
├── .gitignore
├── .dockerignore
├── Dockerfile
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

The main development branch is:

```text
main
```

GitHub Repository:

https://github.com/jawalkarisha-commits/SmartPark

---

# Design Principles

SmartPark follows a role-oriented parking management interface design with emphasis on:

- Simple navigation.
- Role-based functionality.
- Structured parking information.
- Clear booking workflows.
- Owner verification.
- Reservation management.
- Payment management.
- Parking session tracking.
- Consistent application behavior.

The application focuses on connecting parking owners, customers, and administrators through a unified parking management system.

---

# Project Objective

SmartPark aims to provide a unified digital platform for parking facility management and parking reservations.

The core objective is:

```text
User Registration
       ↓
Role-Based Authentication
       ↓
Owner Verification
       ↓
Parking Facility Management
       ↓
Parking Slot Management
       ↓
Slot Availability
       ↓
Parking Reservation
       ↓
Payment Processing
       ↓
Parking Session Management
       ↓
Vehicle Departure
       ↓
Payment Reconciliation
       ↓
Booking Completion
```

This workflow provides a structured approach to managing parking facilities, reservations, payments, and parking sessions.

---

# Project Status

SmartPark includes implementation work covering:

- User authentication.
- Admin, Owner, and Customer roles.
- Owner application verification.
- Owner rejection reasons.
- Customer email verification.
- Parking facility and location management.
- Parking slot reservations.
- Reservation holds.
- Booking management.
- Group and multiple-vehicle bookings.
- Parking sessions.
- Payment management.
- Payment reconciliation.
- Refund-related operations.
- Pricing and late fees.
- API security and application tests.

The repository includes a React frontend, Java Spring Boot backend, MySQL-related database migrations, and Docker build configuration.

---

# Project Links

| Resource | Link |
|---|---|
| GitHub Repository | [SmartPark](https://github.com/jawalkarisha-commits/SmartPark) |
| Frontend | Deployment URL not yet confirmed |
| Backend API | Deployment URL not yet confirmed |
| Database | MySQL |
| Deployment Configuration | https://client-4lnguojs6-jawalkarisha-4959.vercel.app/app/dashboard

---
System Architecture : file:///Users/isha/Desktop/SmartPark/docs/architecture/high-level-architecture.svg

Programming Concepts used : file:///Users/isha/Desktop/SmartPark/docs/architecture/programming-concepts.svg

Component Guide : file:///Users/isha/Desktop/SmartPark/docs/architecture/component-guide.svg

**SmartPark — Simplifying Parking Management Through a Unified Digital Platform.**
