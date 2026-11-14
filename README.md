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
settings in the JDBC URL. Flyway applies the schema migrations at API startup.
Do not put database credentials in this repository.

Set Render's `SMARTPARK_CORS_ALLOWED_ORIGINS` to the exact Vercel preview
origin, and set Vercel's Preview `VITE_API_BASE_URL` to the Render API origin.
Set `SMARTPARK_RESET_URL` to the Vercel origin plus `/forgot-password`. To
provision the initial admin, provide `SMARTPARK_ADMIN_BOOTSTRAP_EMAIL` and
`SMARTPARK_ADMIN_BOOTSTRAP_PASSWORD` as Render secrets for startup; the
password is stored as a BCrypt hash. The free Render service may sleep when
idle, and uploaded KYC documents use local storage, so they are not durable
across service replacement or restart.

## Tests

```sh
cd backend
./mvnw test
cd ../frontend/client
npm test
npm run build
```
