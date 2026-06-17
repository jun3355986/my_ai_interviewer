export const smokeThresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<3000'],
};

export const sseThresholds = {
  http_req_failed: ['rate<0.05'],
  http_req_duration: ['p(95)<45000'],
};
