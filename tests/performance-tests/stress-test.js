import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

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
        http_req_failed: ['rate<0.10'],
        http_req_duration: ['p(95)<1500'],

        // кастомні пороги
        'create_plan_duration{type:create}': ['p(95)<1200'],
        'get_plan_duration{type:read}': ['p(95)<800'],
        'add_location_duration{type:add_location}': ['p(95)<1500'],
    },
};

// кастомні метрики
const createPlanDuration = new Trend('create_plan_duration');
const getPlanDuration = new Trend('get_plan_duration');
const addLocationDuration = new Trend('add_location_duration');
const planCreateErrors = new Rate('plan_create_errors');
const locationErrors = new Rate('location_errors');

const BASE_URL = 'http://localhost:4567/api';

export default function () {

    // --------------------------------------------------------
    // 1. CREATE TRAVEL PLAN
    // --------------------------------------------------------
    const planPayload = JSON.stringify({
        title: `Plan ${Math.random()}`,
        description: 'Stress test',
        start_date: '2025-01-01',
        end_date: '2025-01-02',
        budget: 500,
        currency: 'USD',
        is_public: true
    });

    const createRes = http.post(`${BASE_URL}/travel-plans`, planPayload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { type: 'create' }
    });

    createPlanDuration.add(createRes.timings.duration);

    const created = check(createRes, {
        'plan created': (r) => r.status === 201
    });

    if (!created) {
        planCreateErrors.add(1);
        sleep(0.3);
        return;
    }

    const id = createRes.json().id;


    // --------------------------------------------------------
    // 2. GET TRAVEL PLAN
    // --------------------------------------------------------
    const getRes = http.get(`${BASE_URL}/travel-plans/${id}`, {
        tags: { type: 'read' }
    });

    getPlanDuration.add(getRes.timings.duration);

    check(getRes, { 'plan retrieved': (r) => r.status === 200 });


    // --------------------------------------------------------
    // 3. ADD LOCATION
    // --------------------------------------------------------
    const locPayload = JSON.stringify({
        name: "City",
        latitude: 10,
        longitude: 20,
        budget: 100
    });

    const locRes = http.post(`${BASE_URL}/travel-plans/${id}/locations`, locPayload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { type: 'add_location' }
    });

    addLocationDuration.add(locRes.timings.duration);

    const locationOk = check(locRes, {
        'location added': (r) => r.status === 201
    });

    if (!locationOk) {
        locationErrors.add(1);
    }

    sleep(0.3);
}
