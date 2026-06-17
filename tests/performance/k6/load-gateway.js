import http from 'k6/http';
import { check, sleep } from 'k6';

import { smokeThresholds } from './thresholds.js';

export const options = {
  vus: Number(__ENV.K6_VUS || 5),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: smokeThresholds,
};

const gatewayBaseUrl = __ENV.GATEWAY_BASE_URL || 'http://localhost:9000';

export default function () {
  const response = http.get(`${gatewayBaseUrl}/actuator/health`, {
    timeout: '10s',
  });

  check(response, {
    'gateway health is 200': (res) => res.status === 200,
    'gateway reports status': (res) => String(res.body).includes('status'),
  });

  sleep(1);
}
