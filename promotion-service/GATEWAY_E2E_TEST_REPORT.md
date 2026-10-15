# Gateway end-to-end test report

Date: 2026-10-14

The test topology was `API Gateway (8080) -> Eureka -> promotion-service (8085)` with PostgreSQL. Calls used the gateway URL only.

## Verified

| Area | Gateway path | Result |
|---|---|---|
| OpenAPI forwarding | `/v3/api-docs/promotion-service` | 200 |
| Admin voucher create | `POST /api/v1/admin/vouchers` | 201 |
| Admin voucher read/update/delete | `/api/v1/admin/vouchers/**` | executed with an ADMIN JWT |
| Organizer voucher create/list | `/api/v1/organizer/vouchers/**` | executed with an ORGANIZER JWT |
| Wallet and voucher issue | `/api/v1/vouchers/wallet`, `/api/v1/admin/vouchers/{id}/issue` | executed |
| Internal order flow | `/internal/v1/vouchers/validate`, `/apply`, `/release` | executed with `X-Internal-Secret` |

The local Eureka registration now uses its IP address (`eureka.instance.prefer-ip-address=true`), preventing the gateway from routing to an unresolvable workstation hostname.

