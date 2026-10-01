# Small Business Inventory & Sales System

CPS714 course project. Team: Maria Habib, Walker Egsgard, Gab Talavera.

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
| `GET /api/products` | Admin, Staff | Sprint 1 |
| `POST/PUT/DELETE /api/products/{id}` | Admin | Sprint 1 |
| `POST /api/sales` | Admin, Staff | Sprint 2 |

Role rules for new endpoints go in `SecurityConfig.java`.

## Sprint 1 ownership

| Area | Owner |
|---|---|
| Project setup, database, login, roles config | Gab |
| Product pages: `products.html`, `product-form.html` (stories 2-3) | Maria |
| `Product` entity + `ProductController` endpoints, tests (stories 2-4) | Walker |

## Workflow

- Pull before you start: `git pull`
- Work on a branch: `git checkout -b feature/product-form`
- Commit small, push, open a pull request into `main`
