# Postman collection

Files:

| File | What |
|---|---|
| `omnichannel.postman_collection.json` | 64 requests in 8 folders covering every endpoint, with tests |
| `production.postman_environment.json` | `baseUrl` = `https://api.cunghuynh.id.vn` (fill in `adminPassword`) |
| `local.postman_environment.json` | `baseUrl` = `http://localhost:8080` and the local Docker admin login |

## Use it

1. Postman -> **Import** -> drop all three files in.
2. Top right, choose the environment **Omnichannel - production** (or **local docker**).
3. Open the environment and fill in `adminPassword` for production (the local one is already filled).
   Do not commit that password anywhere.
4. Run the whole flow: right-click the collection -> **Run collection** -> **Run**.
   Or run requests one by one, top to bottom: folder 1 first (it logs in and stores the tokens).

Tokens and ids (`adminToken`, `token`, `sku`, `orderId`...) are stored automatically as collection variables.
Requests marked **[ADMIN]** use the admin token, everything else the customer token.

## Folders

| Folder | Covers |
|---|---|
| 1. Auth | register, login (admin and customer), refresh, logout, duplicate email, wrong password |
| 2. Users and addresses | profile, address create/list/delete, admin role update |
| 3. Catalog | categories, brands, products (create/update/search/get), stock, duplicate SKU |
| 4. Orders and payments | place order, idempotent replay, pay (mock), duplicate payment, CONFIRMED, ship, complete, refund, reconciliation |
| 5. Saga failure paths | declined payment, customer cancel, out of stock, unknown SKU, stock always returns to 49 |
| 6. Security checks | no/garbage token, customer on admin APIs, spoofed `X-User-*` headers, another user's order, bad input |
| 7. Provider callbacks | VNPay and MoMo callbacks with bad signatures must be rejected |
| 8. Cleanup | deactivate product, delete brand and category |

## Notes

- Each run creates its own customer, SKU and orders, so it can be repeated. Cleanup removes the catalog data it created
  (the product is only deactivated). The test customer and orders remain.
- The payment step uses the **MOCK** provider, which exists only while `PAYMENT_MOCK_ENABLED=true`. With real
  VNPay/MoMo you pay on the provider's page instead.
- Folders 4 and 5 contain short waits (2-3.5 s) because the order and payment services talk through RabbitMQ.
  If a check fails with a PENDING status on a slow machine, run that request again.
- Every order request needs a unique `Idempotency-Key`. The collection generates one per order; sending the same key
  again returns the same order, which is also tested.

## Run it from the command line (optional)

```bash
npx newman run docs/postman/omnichannel.postman_collection.json \
  -e docs/postman/local.postman_environment.json
```
