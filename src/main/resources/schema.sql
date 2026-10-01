-- Small Business Inventory & Sales System: database schema
-- Runs on every startup. IF NOT EXISTS makes it safe to re-run.
-- To change a table that already exists, drop it in MySQL first (or ask the team).

-- Sprint 1: login (stories 1, 4)
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL          -- 'ADMIN' or 'STAFF'
);

-- Sprint 1-2: categories (story 8)
CREATE TABLE IF NOT EXISTS categories (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

-- Sprint 1: products (stories 2, 3)
CREATE TABLE IF NOT EXISTS products (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(150)   NOT NULL,
    sku                 VARCHAR(50)    UNIQUE,
    price               DECIMAL(10, 2) NOT NULL,
    quantity            INT            NOT NULL DEFAULT 0,
    category_id         BIGINT,
    low_stock_threshold INT            NOT NULL DEFAULT 5,   -- story 6
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Sprint 2: sales (story 5). A sale has many line items.
CREATE TABLE IF NOT EXISTS sales (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT         NOT NULL,
    total      DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS sale_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id    BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_items_sale    FOREIGN KEY (sale_id)    REFERENCES sales (id),
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
