import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = 'http://localhost:4567/api';

// метрики
export const createLatency = new Trend('create_plan_latency');
export const readLatency = new Trend('read_plan_latency');
export const listLatency = new Trend('list_plans_latency');
export const errors = new Rate('errors');
export const createdPlans = new Counter('created_plans');

export const options = {
    stages: [
        { duration: '20s', target: 20 },   // нормальний фон
        { duration: '10s', target: 200 },  // перший spike
        { duration: '20s', target: 200 },  // утримання піку
        { duration: '10s', target: 20 },   // спад

        { duration: '20s', target: 20 },   // пауза

        { duration: '10s', target: 300 },  // другий більший spike
        { duration: '20s', target: 300 },  // утримання
        { duration: '20s', target: 0 },    // зняття навантаження
    ],

    thresholds: {
        http_req_failed: ['rate<0.10'],
        http_req_duration: ['p(95)<1500'],
        checks: ['rate>0.90'],
        errors: ['rate<0.10'],
    },
};

export default function () {
    // 0. GET all travel plans (каталог)
    let listRes = http.get(`${BASE_URL}/travel-plans`);
    listLatency.add(listRes.timings.duration);

    if (!check(listRes, { 'list OK': (r) => r.status === 200 })) {
        errors.add(1);
    }

    // 1. create plan
    const payload = JSON.stringify({
        title: `Spike plan ${__VU}-${__ITER}`,
        description: 'spike test',
        start_date: '2025-01-01',
        end_date: '2025-01-05',
        budget: 800,
        currency: 'EUR',
        is_public: true,
    });

    let createRes = http.post(`${BASE_URL}/travel-plans`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    createLatency.add(createRes.timings.duration);

    const ok = check(createRes, { 'plan created': (r) => r.status === 201 });
    if (!ok) {
        errors.add(1);
        return;
    }

    createdPlans.add(1);

    const id = createRes.json().id;

    // 2. read created plan
    let readRes = http.get(`${BASE_URL}/travel-plans/${id}`);
    readLatency.add(readRes.timings.duration);

    if (!check(readRes, { 'plan readable': (r) => r.status === 200 })) {
        errors.add(1);
    }

    // 3. add location (write-heavy)
    const locPayload = JSON.stringify({
        name: 'City X',
        latitude: 50.45,
        longitude: 30.52,
        budget: 120,
    });

    let locRes = http.post(`${BASE_URL}/travel-plans/${id}/locations`, locPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    if (!check(locRes, { 'location added': (r) => r.status === 201 })) {
        errors.add(1);
    }

    // emulate real user think time
    sleep(0.3);
}
