# food-delivery-api

A Spring Boot food delivery platform API scaffold: restaurants, menus, customers, orders, and delivery partners on PostgreSQL. This is deliberately a thin MVP — the intent is to layer more complex stories (concurrency, cancellations, reassignment, inventory, etc.) on top of it.

## Run

Uses your local Postgres.app instance (not Docker — port 5432 was already taken by it). One-time setup, only needed once per machine:

```
psql -h localhost -p 5432 -U "$(whoami)" -d postgres -c "CREATE ROLE food_delivery WITH LOGIN PASSWORD 'food_delivery';"
psql -h localhost -p 5432 -U "$(whoami)" -d postgres -c "CREATE DATABASE food_delivery OWNER food_delivery;"
```

Then run the app:

```
mvn spring-boot:run
```

Listens on `http://localhost:4004`.

## Domain model

- **Restaurant** — name, cuisine, address, `open` flag
- **MenuItem** — belongs to a restaurant; name, price, `available` flag
- **Customer** — name, email, phone, address
- **DeliveryPartner** — name, phone, `available` flag
- **Order** — belongs to a customer and restaurant, optionally a delivery partner; status, total, line items
- **OrderItem** — menu item + quantity + price snapshotted at order time

Order status values: `PLACED`, `CONFIRMED`, `PREPARING`, `OUT_FOR_DELIVERY`, `DELIVERED`, `CANCELLED`.

## Endpoints

### Restaurants
- `POST /api/restaurants` — `{ "name", "cuisine", "address" }`
- `GET /api/restaurants` — list all
- `GET /api/restaurants/{id}` — get one
- `POST /api/restaurants/{id}/menu-items` — `{ "name", "price" }`
- `GET /api/restaurants/{id}/menu-items` — list menu for a restaurant

### Customers
- `POST /api/customers` — `{ "name", "email", "phone", "address" }`
- `GET /api/customers` — list all
- `GET /api/customers/{id}` — get one

### Delivery partners
- `POST /api/delivery-partners` — `{ "name", "phone" }`
- `GET /api/delivery-partners` — list all
- `GET /api/delivery-partners/{id}` — get one

### Orders
- `POST /api/orders` — `{ "customerId", "restaurantId", "items": [{ "menuItemId", "quantity" }] }`; computes total from live menu prices, rejects orders for closed restaurants, unavailable items, or items from another restaurant
- `GET /api/orders/{id}` — get one
- `GET /api/orders?customerId=...` or `?restaurantId=...` — list by customer or restaurant
- `PATCH /api/orders/{id}/status` — `{ "status": "CONFIRMED" }` — sets order status directly, no transition validation
- `PATCH /api/orders/{id}/delivery-partner` — `{ "deliveryPartnerId" }` — assigns a partner, no availability check

## What's intentionally missing

These are left as follow-on stories rather than baked into the scaffold:

- No validation of status transitions (e.g. `DELIVERED` → `PLACED` is currently allowed)
- No concurrency guard against double-booking the same delivery partner or racing status updates on the same order
- No inventory/stock counts, only an `available` boolean on menu items
- No automatic partner reassignment if a partner cancels
- No payment, refund, or cancellation-window logic
- No auth — all endpoints are open
