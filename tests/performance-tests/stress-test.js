import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomSeed } from 'k6/crypto';

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 200 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 500 },
        { duration: '30s', target: 600 },
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.10'],      // деградація почнеться десь тут
        http_req_duration: ['p(95)<1500'],  // >1500ms — вже погано
    },
};

const BASE_URL = 'http://localhost:4567/api';

export default function () {

    const planPayload = JSON.stringify({
        title: `Plan ${Math.random()}`,
        description: 'Stress test',
        start_date: '2025-01-01',
        end_date: '2025-01-02',
        budget: 500,
        currency: 'USD',
        is_public: true
    });

    let res = http.post(`${BASE_URL}/travel-plans`, planPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, { 'plan created': (r) => r.status === 201 });

    if (res.status === 201) {
        const id = res.json().id;

        http.get(`${BASE_URL}/travel-plans/${id}`);

        const loc = JSON.stringify({
            name: "City",
            latitude: 10,
            longitude: 20,
            budget: 100
        });
        http.post(`${BASE_URL}/travel-plans/${id}/locations`, loc);
    }

    sleep(0.3);
}
