# Shop Platform

One application that joins the two earlier projects:

- **Microservices** (API Gateway, Product Service, Order Service)
- **Authentication and authorization** (register, login, JWT, refresh tokens, roles)

It uses a **parent `pom.xml`** and an **API Gateway that checks the JWT** for every request (like the Softkey BLE backend). Services find each other through **fixed URLs in settings** (like the original microservice project), so there is no discovery server.

It is a small online shop. Anyone can browse products. Logged-in users can place and cancel **their own** orders. Admins manage products and move orders through their statuses.

Built with: **Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1 (Gateway, OpenFeign), Spring Security, JJWT, Spring Data JPA, PostgreSQL, Maven**.

---

# Architecture

```text
                       Client (browser / Postman / mobile)
                                     |
                                     v
                         +-----------------------+
                         |      API Gateway      |  :8080  the only public entry point
                         |  checks JWT, CORS,    |
                         |  adds X-User-* headers|
                         +-----------+-----------+
                                     |  fixed URLs (AUTH/PRODUCT/ORDER_SERVICE_URL)
         +---------------------------+---------------------------+
         |                           |                           |
         v                           v                           v
+-----------------+         +-----------------+   Feign   +-----------------+
|  Auth Service   |         |  Order Service  | --------> | Product Service |
|      :8083      |         |      :8081      | /internal |      :8082      |
| creates JWTs    |         | role + owner    |           | role checks     |
+--------+--------+         +--------+--------+           +--------+--------+
         |                           |                             |
      auth_db                     order_db                     product_db
```

| Service | Port | Responsibility |
|---|---:|---|
| API Gateway | 8080 | One entry point. Checks the JWT, routes to each service, CORS |
| Auth Service | 8083 | Users, register, login, JWT access tokens, refresh tokens, admin user management |
| Product Service | 8082 | Products and stock |
| Order Service | 8081 | Orders, order status, calls Product Service to check and change stock |

Each service owns **its own database**. No service reads another service's tables.

---

# How the pieces fit together

## 1. Fixed URLs: how services find each other

Every address is written in the settings, with a default for your own computer. You can change each one with an environment variable.

**Gateway** (`api-gateway/.../application.properties`):

```properties
spring.cloud.gateway.server.webflux.routes[2].uri=${ORDER_SERVICE_URL:http://localhost:8081}
```

**Order Service calling Product Service** (Feign):

```properties
product-service.url=${PRODUCT_SERVICE_URL:http://localhost:8082}
```

```java
@FeignClient(name = "product-service", url = "${product-service.url}")
```

In plain words: "if `ORDER_SERVICE_URL` is set, use it; otherwise use `http://localhost:8081`."

Real-world example: a friend's phone number saved in your contacts. It works every time. If they change their number, you update the contact once.

- **Good:** simple, nothing extra to run, works right after startup.
- **Limit:** one URL points to one copy of a service. To run several copies, put a load balancer in front, or use Docker/Kubernetes service names (for example `http://product-service:8082`).

## 2. Auth Service creates the ticket

When you log in, the Auth Service gives you a **JWT**: a signed ticket that says who you are.

```json
{
  "sub": "alice@example.com",
  "role": "REGULAR_USER",
  "userId": "fac82465-28bb-4905-8e82-9de9bddb58e3",
  "iat": 1790835847,
  "exp": 1790836747
}
```

## 3. The gateway checks the ticket, once

`AuthenticationFilter` in the gateway runs for **every request**:

```text
Request (cookie "access_token" or "Authorization: Bearer ...")
        |
        v
API Gateway: AuthenticationFilter
        |  1. delete any X-User-* headers the client sent
        |  2. public path? (login, register, GET products)  -> forward
        |  3. no token / bad token / expired                 -> 401, stops here
        |  4. valid token -> add the user as headers:
        |        X-User-Id:    fac82465-...
        |        X-User-Email: alice@example.com
        |        X-User-Role:  REGULAR_USER
        v
Order Service: GatewayUserFilter turns the headers into a Spring Security user
        |
        v
SecurityConfig rules (role check)          -> wrong role: 403
        |
        v
OrderService: "is this order yours?"       -> not yours: 403
        |
        v
Controller
```

Real-world example: a building with one reception desk. Reception checks your ID card once and gives you a visitor badge with your name and floor. Inside, each office just reads the badge.

Step 1 is important: if a client sends `X-User-Role: ADMIN` themselves, the gateway deletes it and writes the real role from the token.

## 4. Service-to-service: the `/internal` door

When Alice places an order, Order Service must reduce the stock. But Alice herself must not change stock.

So Product Service has two stock endpoints:

| Endpoint | Who | Reachable from outside? |
|---|---|---|
| `PATCH /api/v1/products/{id}/stock` | ADMIN only | Yes, through the gateway |
| `PATCH /internal/products/{id}/stock` | Other services (Order Service) | **No**: the gateway has no route for `/internal/**` |

Order Service calls the internal one directly with Feign. No key or token is needed, because only services inside the system can reach it.

Real-world example: a shop's back door to the stockroom. It isn't on the street side, so customers can't use it; staff inside the building can.

---

# Authorization rules

Roles come from the Auth Service: `REGULAR_USER` (everyone who registers) and `ADMIN`.

| Endpoint | Guest | REGULAR_USER | ADMIN |
|---|:---:|:---:|:---:|
| `GET /api/v1/products/**` | ✅ | ✅ | ✅ |
| `POST/PUT/DELETE /api/v1/products/**` | ❌ 401 | ❌ 403 | ✅ |
| `PATCH /api/v1/products/{id}/stock` | ❌ 401 | ❌ 403 | ✅ |
| `POST /api/v1/orders` | ❌ 401 | ✅ | ✅ |
| `GET /api/v1/orders/my` | ❌ 401 | ✅ own | ✅ own |
| `GET /api/v1/orders/{id}` | ❌ 401 | ✅ own only | ✅ any |
| `PATCH /api/v1/orders/{id}/cancel` | ❌ 401 | ✅ own only | ✅ any |
| `GET /api/v1/orders` (all orders) | ❌ 401 | ❌ 403 | ✅ |
| `PATCH /api/v1/orders/{id}/status` | ❌ 401 | ❌ 403 | ✅ |
| `GET /api/v1/admin/users` | ❌ 401 | ❌ 403 | ✅ |
| `/internal/**` | not routed (404) | not routed (404) | not routed (404) |

Three layers:

1. **Authentication** in the gateway ("do you have a valid ticket?").
2. **By role** in each service's `SecurityConfig` ("only admins may do this").
3. **By owner** in `OrderService.checkOwnerOrAdmin()` ("this order must be yours").

---

# What changed from the two old projects

| Change | Why |
|---|---|
| Parent `pom.xml` (like Softkey) | One place for Spring Boot, Spring Cloud, JJWT and Lombok versions |
| Fixed URLs with defaults (`AUTH_SERVICE_URL`, `PRODUCT_SERVICE_URL`, `ORDER_SERVICE_URL`) | Simple; no discovery server to run |
| Gateway checks the JWT and sends `X-User-*` headers | Authentication in one place, like Softkey's `AuthenticationFilter` |
| Product and Order Service read `X-User-*` headers | They keep their role and owner checks, without JWT code |
| Stock changes from Order Service use `/internal/...` | Service-to-service call without any secret key |
| All URLs start with `/api/v1/` | One consistent style |
| JWT has a `userId` claim | Order Service needs to know who the user is |
| `customerId` is taken **from the token**, not the request body | Before, anyone could order "as" customer 101 |
| New `GET /api/v1/orders/my` | Users see only their own orders |
| Cancelling an order **puts the stock back** | Before, cancelled items were lost from stock |
| Feign error decoder | Missing product gives `404` instead of `500`; Product Service down gives `503` |
| CORS in the gateway (`CorsGlobalConfig`) | Set once for all services |
| Gateway sends the real client IP (`X-Forwarded-For`) | The login rate limit is per client |
| First admin created from `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Registration only creates regular users |

---

# Project structure

```text
shop-platform/
├── pom.xml                       ← parent: versions + modules. Build all: mvn package
├── .env.example                  ← copy to .env and fill in secrets
│
├── api-gateway/                  com.gateway.gateway
│   ├── security/AuthenticationFilter   ← checks the JWT, adds X-User-* headers
│   ├── security/JwtUtil                ← reads tokens
│   └── config/CorsGlobalConfig
│
├── auth-service/                 com.auth.auth
│   ├── config/SecurityConfig, AdminAccountInitializer
│   ├── security/JwtUtil (creates tokens), JwtAuthenticationFilter
│   ├── service/AuthService, UserService
│   └── controller/AuthController, UserController, AdminController
│
├── product-service/              com.product.product
│   ├── config/SecurityConfig           ← who may do what with products
│   ├── security/GatewayUserFilter, AuthUser
│   ├── controller/ProductController, InternalProductController (/internal/**)
│   └── service, model, dto, exception, repository
│
└── order-service/                com.order.order
    ├── config/SecurityConfig           ← who may do what with orders
    ├── config/FeignConfig              ← turns Product Service errors into clear replies
    ├── security/GatewayUserFilter, AuthUser
    ├── client/ProductServiceClient     ← Feign, calls product-service at PRODUCT_SERVICE_URL
    └── controller, service (ownership checks), model, dto, exception, repository
```

---

# Running the project

## 1. Create the databases

```sql
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE order_db;
```

Hibernate creates the tables on startup.

## 2. Settings

```bash
cp .env.example .env
```

Then edit `.env`:

| Variable | Required | Used by | Default |
|---|---|---|---|
| `DB_PASSWORD` | **Yes** | auth, product, order | — |
| `JWT_SECRET` | **Yes**, at least 32 bytes, same value in both | auth, gateway | — |
| `DB_USERNAME` | No | auth, product, order | `postgres` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | No | auth | none (no admin created) |
| `COOKIE_SECURE` | No | auth | `true` (set `false` for plain-http local tests) |
| `AUTH_SERVICE_URL` | No | gateway | `http://localhost:8083` |
| `PRODUCT_SERVICE_URL` | No | gateway, order | `http://localhost:8082` |
| `ORDER_SERVICE_URL` | No | gateway | `http://localhost:8081` |
| `AUTH_DB_URL`, `PRODUCT_DB_URL`, `ORDER_DB_URL` | No | each service | `jdbc:postgresql://localhost:5432/<name>_db` |
| `CORS_ALLOWED_ORIGINS` | No | gateway | `http://localhost:3000,http://localhost:5173` |

Make a good `JWT_SECRET`:

```bash
openssl rand -base64 48
```

The auth service and the gateway refuse to start if `JWT_SECRET` is missing or too short.

## 3. Build

```bash
mvn -DskipTests clean package
```

## 4. Run (one terminal each)

Load the settings in every terminal first:

```bash
set -a; source .env; set +a
```

Then start the services, in any order:

```bash
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
```

```bash
java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar
```

```bash
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
```

```bash
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
```

Swagger for the Auth Service: http://localhost:8083/swagger-ui/index.html

---

# Try it (all through the gateway on :8080)

### Register and log in

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Password123"}'
```

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password123"}'
```

Copy `data.accessToken` from the reply. Below, `$ALICE` is Alice's token and `$ADMIN` is the admin's token (log in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`).

### Admin creates a product

```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Wireless Keyboard","description":"Bluetooth","price":15000.00,"stockQuantity":50}'
```

### Alice places an order

No `customerId` in the body: it comes from her token.

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $ALICE" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

```text
Client -> Gateway (checks Alice's JWT, adds X-User-Id)
            -> Order Service
                 | Feign: GET /api/v1/products/1, PATCH /internal/products/1/stock
                 v
               Product Service: check stock, reduce stock
                 |
                 v
               Order Service: save order with customerId = Alice's userId
```

### Alice's orders, and cancelling

```bash
curl http://localhost:8080/api/v1/orders/my -H "Authorization: Bearer $ALICE"
```

```bash
curl -X PATCH http://localhost:8080/api/v1/orders/1/cancel -H "Authorization: Bearer $ALICE"
```

### Admin confirms an order

```bash
curl -X PATCH http://localhost:8080/api/v1/orders/1/status \
  -H "Authorization: Bearer $ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMED"}'
```

Order statuses work as before: `PENDING → CONFIRMED → COMPLETED`, and `PENDING` or `CONFIRMED` can become `CANCELLED`. Cancelling returns the items to stock.

---

# Design notes and known limits

- **Only the gateway may be public.** Product and Order Service trust the `X-User-*` headers and the `/internal/**` paths. That's safe only if ports 8081, 8082 and 8083 can't be reached from outside (private network, firewall, or Docker network). If someone can call 8081 directly, they can send `X-User-Role: ADMIN` themselves.
- **Disabled users.** The Auth Service checks the database on every request, so a disabled user is blocked at once there. The gateway only checks the token, so a disabled user can still use products and orders until the token expires (max 15 minutes).
- **Shared secret (HS384).** The gateway and the Auth Service both know `JWT_SECRET`. A next step would be RS256: the Auth Service signs with a private key, and the gateway only gets the public key.
- **Order + stock is not one transaction.** Stock is reduced in Product Service, then the order is saved in Order Service. If the second step fails, the stock stays reduced. Real systems fix this with patterns like Saga or an outbox.
- **Rate limit memory.** The login rate limit is kept in memory, so it only works for one Auth Service instance.
