# Boltséged.hu

Initial production-oriented foundation for a small DHL Express shipping and billing application.

## Stack

- `backend`: Java 21, Spring Boot 3, Spring Security, JPA, PostgreSQL, Flyway
- `frontend`: Vue 3, Vite, TypeScript, Pinia, Vue Router
- `infra`: Docker Compose, PostgreSQL, Caddy

## Run locally

1. Copy `.env.example` to `.env` and set `JWT_SECRET` to a long random value.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start the API: `cd backend; ./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`). The wrapper bootstraps Maven 3.9.9 when needed.
4. Start the frontend: `cd frontend; npm install; npm run dev`.

The API listens on `http://localhost:8080`, and the Vite app on `http://localhost:5173`.
Flyway runs automatically at backend startup. The first administrator is provisioned from `ADMIN_EMAIL` and `ADMIN_PASSWORD` when the database is empty.

## Docker

`docker compose up --build` starts PostgreSQL, backend, production-built frontend and Caddy. Copy `.env.example` to `.env` first for real configuration. Caddy serves `boltseged.hu` and proxies `api.boltseged.hu` to the backend; DNS for both domains must point at the host and ports 80/443 must be reachable.

## Tests

`cd backend; ./mvnw test` (Windows: `mvnw.cmd test`)

## Configuration

See `.env.example`. DHL calls are intentionally behind `ShippingProvider`; until official MyDHL credentials and the applicable account-specific schema are configured, the adapter fails clearly rather than fabricating DHL responses.

Required production variables include PostgreSQL credentials, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `DHL_API_USERNAME`, `DHL_API_PASSWORD`, `DHL_ACCOUNT_NUMBER`, `DHL_TEST_MODE`, and `LABEL_STORAGE_PATH`. `DHL_API_BASE_URL` is optional; leave it blank to select the official test or production URL from `DHL_TEST_MODE`. `CORS_ALLOWED_ORIGINS` may be added when the deployment requires explicit cross-origin configuration.

## API outline

- `POST /api/auth/login`
- `GET /api/me`
- Customer: `GET /api/shipments`, `POST /api/shipments/quote`, `POST /api/shipments`, `GET /api/shipments/{id}`, `GET /api/shipments/{id}/labels/{labelId}`
- Admin: `GET /api/admin/accounts`, `POST /api/admin/accounts`, `PATCH /api/admin/accounts/{id}`, `GET /api/admin/billing`, `POST /api/admin/billing/invoice`

Shipment creation accepts an idempotency key in `Idempotency-Key`; the key is unique per account and repeated requests return the original shipment. DHL REST uses the official `GET /rates` operation for one package, `POST /rates` for multipiece quotes, and `POST /shipments` for shipment/label creation. Set `DHL_TEST_MODE=true` for `https://express.api.dhl.com/mydhlapi/test`, or false for production.
