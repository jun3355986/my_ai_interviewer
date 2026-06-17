import http from 'k6/http';
import { check, sleep } from 'k6';

import { smokeThresholds } from './thresholds.js';

export const options = {
  vus: Number(__ENV.K6_VUS || 5),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: smokeThresholds,
};

const gatewayBaseUrl = __ENV.GATEWAY_BASE_URL || 'http://localhost:9000';
const username = __ENV.SMOKE_USERNAME || 'admin';
const password = __ENV.SMOKE_PASSWORD || 'admin123';
const keyword = encodeURIComponent(__ENV.SMOKE_JOB_KEYWORD || 'java');

function login() {
  const response = http.post(
    `${gatewayBaseUrl}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '15s' },
  );
  const body = response.json();
  return body?.data?.accessToken;
}

export default function () {
  const token = login();
  check(token, {
    'login returned token': (value) => Boolean(value),
  });

  const response = http.get(`${gatewayBaseUrl}/api/v1/jobs/search?keyword=${keyword}`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: '15s',
  });

  check(response, {
    'job search is 200': (res) => res.status === 200,
    'job search result code is 200': (res) => res.json('code') === 200,
  });

  sleep(1);
}
