import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    throughput_test: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '15s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<150', 'p(99)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

export default function () {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': 'benchmark-test-key',
      'User-Agent': 'k6-benchmark-agent',
    },
  };

  const res = http.get(`${BASE_URL}/api/v1/orders/123`, params);

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'has trace id': (r) => r.headers['X-Request-Id'] !== undefined || r.headers['x-request-id'] !== undefined,
  });

  sleep(0.01);
}
