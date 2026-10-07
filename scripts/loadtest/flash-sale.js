// Flash-sale load test (k6). Hammers ONE sku that has very little stock and proves:
//   1. exactly STOCK orders succeed (no overselling, no under-selling)
//   2. stock never goes negative
//
// Run from the repo root (k6 in Docker, gateway on the host):
//   docker run --rm -i -e BASE=http://host.docker.internal:8080 -v "$PWD/scripts/loadtest:/s" grafana/k6 run /s/flash-sale.js
// Options (env): BASE, RATE (requests/s, default 500), DURATION (default 20s), STOCK (default 10), USERS (default 50),
//                ADMIN_EMAIL, ADMIN_PASSWORD. The gateway must run with a high RATE_LIMIT_RPM.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const STOCK = parseInt(__ENV.STOCK || '10');
const USERS = parseInt(__ENV.USERS || '50');
const SKU = 'FLASH-001';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const ordersCreated = new Counter('orders_created');
const outOfStock = new Counter('orders_out_of_stock');
const unexpected = new Counter('orders_unexpected');

export const options = {
  scenarios: {
    flash_sale: {
      executor: 'constant-arrival-rate',
      rate: parseInt(__ENV.RATE || '500'),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
    },
  },
  thresholds: {
    // the whole point of the test: successful orders must equal the stock exactly
    orders_created: [`count==${STOCK}`],
    orders_unexpected: ['count==0'],
    http_req_failed: [{ threshold: 'rate<1', abortOnFail: false }],
  },
};

function login(email, password) {
  const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password }), { headers: JSON_HEADERS });
  return r.json('data.accessToken');
}

export function setup() {
  const adminToken = login(__ENV.ADMIN_EMAIL || 'admin@example.com', __ENV.ADMIN_PASSWORD || 'Admin@12345');
  const admin = { headers: { ...JSON_HEADERS, Authorization: `Bearer ${adminToken}` } };

  const reset = http.put(`${BASE}/api/inventory/${SKU}`, JSON.stringify({ available: STOCK }), admin);
  check(reset, { 'stock reset': (r) => r.status === 200 });

  const run = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    const email = `load-${run}-${i}@example.com`;
    const r = http.post(`${BASE}/api/auth/register`,
      JSON.stringify({ email, password: 'Passw0rd!', fullName: `Load User ${i}` }), { headers: JSON_HEADERS });
    tokens.push(r.json('data.accessToken'));
  }
  return { adminToken, tokens };
}

export default function (data) {
  const token = data.tokens[__ITER % data.tokens.length];
  const res = http.post(`${BASE}/api/orders`,
    JSON.stringify({
      items: [{ sku: SKU, quantity: 1 }],
      shipping: { receiverName: 'Load Tester', phone: '0900000000', address: '1 Test Street, HCMC' },
      paymentMethod: 'MOCK',
    }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}`, 'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}` },
      tags: { name: 'place_order' },
    });

  if (res.status === 200) {
    ordersCreated.add(1);
  } else if (res.status === 409 && res.json('code') === 4001) {
    outOfStock.add(1); // expected once the stock is gone
  } else {
    unexpected.add(1);
  }
}

export function teardown(data) {
  sleep(2);
  const r = http.get(`${BASE}/api/inventory/${SKU}`, { headers: { Authorization: `Bearer ${data.adminToken}` } });
  const stock = r.json('data');
  console.log(`final stock for ${SKU}: available=${stock.available} reserved=${stock.reserved}`);
  check(stock, {
    'stock never negative': (s) => s.available >= 0,
    'all stock reserved or sold': (s) => s.available === 0 && s.reserved === STOCK,
  });
}
