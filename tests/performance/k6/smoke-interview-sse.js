import http from 'k6/http';
import { check } from 'k6';

import { sseThresholds } from './thresholds.js';

export const options = {
  vus: Number(__ENV.K6_SSE_VUS || 1),
  iterations: Number(__ENV.K6_SSE_ITERATIONS || 1),
  thresholds: sseThresholds,
};

const gatewayBaseUrl = __ENV.GATEWAY_BASE_URL || 'http://localhost:9000';
const username = __ENV.SMOKE_USERNAME || 'admin';
const password = __ENV.SMOKE_PASSWORD || 'admin123';
const message = __ENV.SMOKE_MESSAGE || 'I am ready for the interview.';

function login() {
  const response = http.post(
    `${gatewayBaseUrl}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '15s' },
  );
  return response.json('data.accessToken');
}

export default function () {
  const token = login();
  check(token, {
    'login returned token': (value) => Boolean(value),
  });

  const response = http.post(
    `${gatewayBaseUrl}/api/v1/interviews/chat`,
    JSON.stringify({
      sessionId: null,
      message,
      resumeId: null,
      jobId: null,
    }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
      },
      timeout: `${Number(__ENV.SSE_MAX_TIME || 45)}s`,
    },
  );

  check(response, {
    'sse request is 200': (res) => res.status === 200,
    'sse content type': (res) => String(res.headers['Content-Type'] || '').includes('text/event-stream'),
    'sse has event data': (res) => String(res.body || '').includes('data:'),
  });
}
