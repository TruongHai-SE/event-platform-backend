# Gateway end-to-end test report

Date: 2026-10-14

The test topology was `API Gateway (8080) -> Eureka -> ad-service (8086)` with PostgreSQL and Redis. Public requests were sent through the gateway only.

## Verified

| Area | Gateway path | Result |
|---|---|---|
| OpenAPI forwarding | `/v3/api-docs/ad-service` | 200 |
| Active public banners | `GET /api/v1/public/banners` | 200 |
| Service discovery route | `lb://ad-service` via API Gateway | 200 |

## Runtime fixes made during verification

- The service now reads and validates JWTs before applying `ADMIN` and `ORGANIZER` role checks.
- The Spring Boot packaging plugin was added, so the service produces an executable JAR.
- Springdoc was added so the gateway Swagger aggregation endpoint is available.
- Eureka now registers the service by IP address for reliable local gateway routing.

## External dependency

`POST /api/v1/admin/banners/upload` requires a real Cloudinary cloud name and unsigned upload preset. It is reachable through the gateway, but an upload success case needs those deployment credentials and was not claimed as verified locally.

