import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ['rate==1.0'],
    },
};

const BASE_URL = 'http://localhost:4567/api';

export default function () {
    console.log('Running Traveler API smoke test...');

    // ---- 1. Health check ----
    let res = http.get(`${BASE_URL}/health`);
    check(res, { 'Health OK (200)': (r) => r.status === 200 });

    // ---- 2. CRUD basic ----
    const planPayload = JSON.stringify({
        title: 'Smoke Plan',
        description: 'Smoke test validation',
        start_date: '2025-01-01',
        end_date: '2025-01-05',
        budget: 1000,
        currency: 'USD',
        is_public: true,
    });

    // CREATE
    res = http.post(`${BASE_URL}/travel-plans`, planPayload, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'Travel plan created (201)': (r) => r.status === 201 });
    const plan = res.json();

    // READ
    res = http.get(`${BASE_URL}/travel-plans/${plan.id}`);
    check(res, { 'Travel plan fetched (200)': (r) => r.status === 200 });

    // UPDATE
    const updated = {
        ...plan,
        title: 'Smoke Plan Updated',
        version: plan.version,
    };
    res = http.put(`${BASE_URL}/travel-plans/${plan.id}`, JSON.stringify(updated), {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'Travel plan updated (200)': (r) => r.status === 200 });

    // ---- 3. Location management ----
    const locationPayload = JSON.stringify({
        name: 'Kyiv',
        latitude: 50.45,
        longitude: 30.52,
        budget: 300,
    });
    res = http.post(`${BASE_URL}/travel-plans/${plan.id}/locations`, locationPayload, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'Location created (201)': (r) => r.status === 201 });
    const location = res.json();

    res = http.get(`${BASE_URL}/travel-plans/${plan.id}/locations`);
    check(res, { 'Locations list fetched (200)': (r) => r.status === 200 });

    // ---- 4. Validation ----
    const invalidPlan = JSON.stringify({
        title: '',
        start_date: '2025-02-10',
        end_date: '2025-02-01',
    });
    res = http.post(`${BASE_URL}/travel-plans`, invalidPlan, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'Invalid plan rejected (400)': (r) => r.status === 400 });

    // ---- 5. Delete ----
    res = http.del(`${BASE_URL}/travel-plans/${plan.id}`);
    check(res, { 'Travel plan deleted (204)': (r) => r.status === 204 });

    sleep(1);
}
