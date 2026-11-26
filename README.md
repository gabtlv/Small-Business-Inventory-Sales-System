# Small Business Inventory & Sales System

[![CI](https://github.com/gabtlv/Small-Business-Inventory-Sales-System/actions/workflows/ci.yml/badge.svg)](https://github.com/gabtlv/Small-Business-Inventory-Sales-System/actions/workflows/ci.yml)

CPS714 course project. Team: Maria Habib, Walker Egsgard, Gab Talavera, Jahangir Khan.

A web app for small retail businesses to manage inventory, record sales and view reports.
Two roles: **Admin** (business owner) and **Staff** (cashier).

## Tech stack

| Layer | Tech |
|---|---|
| Frontend | HTML, CSS, JavaScript (`fetch` calls to the API) |
| Backend | Java 21, Spring Boot (REST API) |
| Auth | Spring Security (session login, BCrypt passwords, ADMIN/STAFF roles) |
| Database | MySQL (`src/main/resources/schema.sql`) |
| Tests | JUnit + MockMvc, in-memory H2 database |
| CI | GitHub Actions runs `./mvnw test` on every push to `main` and every pull request |
| Docker | `Dockerfile` + `docker-compose.yml` (app + MySQL 8) |
| Deployment | Google Kubernetes Engine (Autopilot), Artifact Registry, auto-deploy from GitHub Actions (see [DEPLOY.md](DEPLOY.md)) |

## Run it with Docker (no Java or MySQL install needed)

1. **Install** [Docker Desktop](https://www.docker.com/products/docker-desktop/) and start it.
2. From the project root:
   ```
   docker compose up --build
   ```
   This starts two containers: MySQL 8 (with the `inventory` database created for you) and the app.
   The app waits until MySQL is ready. The first build downloads dependencies and takes a few minutes.
3. Open <http://localhost:8081> and log in with the accounts below.

| Command | What it does |
|---|---|
| `docker compose up --build` | Build the app image and start everything (add `-d` to run in the background) |
| `docker compose logs -f app` | Follow the app's logs |
| `docker compose down` | Stop everything; the database is kept |
| `docker compose down -v` | Stop everything and delete the database (fresh start next time) |

The Docker database is reachable from MySQL Workbench at `127.0.0.1`, port **3307** (user `inventory`,
password `inventory-dev-password`), so it doesn't clash with a MySQL you already run on 3306.
To change the passwords, create a `.env` file next to `docker-compose.yml` (it's git-ignored):
```
MYSQL_ROOT_PASSWORD=your-root-password
MYSQL_PASSWORD=your-app-password
```

Not using Docker? Nothing changes: follow **Run it locally** below. `application-local.properties`
works exactly as before, and Docker never copies it into an image (see `.dockerignore`).

## Deploy to Google Cloud (Kubernetes)

The app runs on Google Kubernetes Engine for the final presentation: the app and MySQL as pods,
behind a load balancer with a public IP. Pushes to `main` that pass CI are deployed automatically.
Step-by-step setup, costs, and how to tear it down: **[DEPLOY.md](DEPLOY.md)**.

The deployed site does **not** use the dev passwords below: its Admin/Staff passwords are set
with Kubernetes secrets (`APP_SEED_ADMIN_PASSWORD` / `APP_SEED_STAFF_PASSWORD`).

## Run it locally

1. **Install** Java 21 (e.g. Temurin) and MySQL 8.
2. **Create the database** (once), in MySQL Workbench or the MySQL shell:
   ```sql
   CREATE DATABASE inventory;
   ```
3. **Set your MySQL password**: create `application-local.properties` in the project root
   (it's git-ignored, so each person has their own):
   ```properties
   spring.datasource.username=root
   spring.datasource.password=YOUR_MYSQL_PASSWORD
   ```
4. **Start the app** from the project root:
   ```
   .\mvnw spring-boot:run        (Windows)
   ./mvnw spring-boot:run        (Mac/Linux)
   ```
   Tables are created automatically from `schema.sql`.
5. Open <http://localhost:8081> and log in:

   | Username | Password | Role |
   |---|---|---|
   | `admin` | `admin123` | Admin |
   | `staff` | `staff123` | Staff |

   (Demo accounts are created by `DataSeeder.java`; dev only.)

## Run the tests

```
.\mvnw test
```

Tests use an in-memory H2 database, so MySQL doesn't need to be running.

The same command runs on GitHub Actions (`.github/workflows/ci.yml`) for every push to `main` and
every pull request, whatever branch it targets. The badge at the top shows the result for `main`,
and each pull request shows a pass/fail check. If a run fails, its test reports are attached to the
run as `surefire-reports`.

## Project structure

```
src/main/java/com/team/inventory/
  InventoryApplication.java   app entry point
  config/SecurityConfig.java  login + role rules (who can access what)
  config/DataSeeder.java      creates the demo admin/staff accounts
  controller/                 REST endpoints (/api/...)
  service/                    business logic that needs a transaction (e.g. SaleService)
  dto/                        request/response shapes for the API
  model/                      database entities (User, Product, ...)
  repository/                 database access
src/main/resources/
  schema.sql                  all database tables
  application.properties      app + database settings
  static/                     HTML, CSS, JS pages
src/test/                     JUnit tests
```

## API

| Endpoint | Who | Status |
|---|---|---|
| `POST /login`, `POST /logout` | anyone | done |
| `GET /api/me` | logged in | done |
| `GET /api/products?q=&categoryId=` | Admin, Staff | done (search by name/SKU, filter by category) |
| `GET /api/products/{id}` | Admin, Staff | done |
| `POST /api/products`, `PUT/DELETE /api/products/{id}` | Admin | done (delete refused if the product has sales) |
| `GET /api/products/low-stock` | Admin | done (quantity at or below each product's threshold) |
| `GET /api/categories` | Admin, Staff | done |
| `POST /api/categories`, `DELETE /api/categories/{id}` | Admin | done (deleting keeps products, uncategorized) |
| `GET /api/reports/sales?period=daily\|weekly\|monthly&from=&to=` | Admin | done |
| `GET /api/reports/sales/export?period=&from=&to=` | Admin | done (same report as a CSV download) |
| `GET /api/dashboard/summary` | Admin | done (last 30 days: total sales, number of sales, top products, low-stock count) |
| `GET /api/sales/products` | Admin, Staff | done (checkout product list: price + stock) |
| `POST /api/sales` | Admin, Staff | done (records sale, decrements stock; optional `Idempotency-Key` header makes retries safe) |
| `GET /api/users` | Admin | done (active Staff accounts: id + username, never passwords) |
| `POST /api/users` | Admin | done (`{username, password}`; always creates a Staff account) |
| `DELETE /api/users/{id}` | Admin | done (removes a Staff account; their sales are kept) |

Role rules for new endpoints go in `SecurityConfig.java`.

## Staff management (Sprint 3)

Admin opens **Staff** on the home page (`/staff.html`) to see Staff accounts, add one
(username + password; it's always a Staff account) or remove one. Staff can't open the page or
use `/api/users`.

Removing an account doesn't delete it, because its sales still point at it. Instead it's listed in
the `deactivated_users` table: the person can't log in, and if they're already logged in their
session ends on their next click. Their past sales stay in the reports, and the username can't be
reused. (The table is created automatically on startup; existing databases don't need changes.)

**Try it:** log in as `admin`, open **Staff**, add `jamie` / `secret1`. In a private window, log in
as `jamie` and open **Checkout**. Back as admin, remove `jamie`. Jamie's next click goes to the
login page, and logging in as `jamie` again fails. Tests: `StaffManagementTests`.

## Sprint 1 ownership

| Area | Owner |
|---|---|
| Project setup, database, login, roles config | Gab |
| Product pages: `products.html`, `product-form.html` (stories 2-3) | Maria |
| `Product` entity + `ProductController` endpoints, tests (stories 2-4) | Walker |
| Checkout page, `POST /api/sales`, automatic stock decrement (story 5) | Jahangir |

## Workflow

- Pull before you start: `git pull`
- Work on a branch: `git checkout -b feature/product-form`
- Commit small, push, open a pull request into `main`

## Sprint 3 ownership

| Area | Owner |
|---|---|
| Report export (US-10) | Gab |
| Staff account management (US-11) | Jahangir |
| Dashboard summary (US-12) | Maria |
| Testing and edits | Walker |
