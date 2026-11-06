# Small Business Inventory & Sales System

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
