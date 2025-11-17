import http from 'k6/http';
import { sleep, check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { ENDPOINTS } from './config/endpoints.js';
import { generateTravelPlan, generateLocation } from './utils/data-generator.js';

export const options = {
    stages: [
        { duration: '5m', target: 50 },      // Розігрів
        { duration: '25m', target: 50 },     // Основне тривале навантаження (разом 30 хв)
        { duration: '2m', target: 0 },       // Спад
    ],
    thresholds: {
        http_req_failed: ['rate<0.03'],      // <3% помилок
        http_req_duration: ['p(95)<600'],    // p95 < 600ms
    }
};

export default function () {

    // 1. CREATE TRAVEL PLAN
    const plan = generateTravelPlan();
    const createRes = http.post(
        ENDPOINTS.TRAVEL_PLANS,
        JSON.stringify(plan),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(createRes, {
        'plan created 201': (r) => r.status === 201
    });

    if (createRes.status !== 201) {
        sleep(1);
        return;
    }

    const createdPlan = JSON.parse(createRes.body);
    const planId = createdPlan.id;
    const version = createdPlan.version;

    sleep(1);

    // 2. GET TRAVEL PLAN
    const getRes = http.get(ENDPOINTS.TRAVEL_PLAN_BY_ID(planId));
    check(getRes, {
        'plan retrieved 200': (r) => r.status === 200
    });

    sleep(1);

    // 3. UPDATE TRAVEL PLAN (OPTIMISTIC LOCKING)
    const updated = {
        ...plan,
        title: plan.title + ' (updated)',
        version: version
    };

    const updateRes = http.put(
        ENDPOINTS.TRAVEL_PLAN_BY_ID(planId),
        JSON.stringify(updated),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(updateRes, {
        'plan update status OK or 409': (r) =>
            r.status === 200 || r.status === 409
    });

    sleep(1);

    // 4. CREATE LOCATION
    const location = generateLocation();
    const locationRes = http.post(
        ENDPOINTS.LOCATIONS_FOR_PLAN(planId),
        JSON.stringify(location),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(locationRes, {
        'location created 201': (r) => r.status === 201
    });

    let locationId = null;
    if (locationRes.status === 201) {
        locationId = JSON.parse(locationRes.body).id;
    }

    sleep(1);

    // 5. READ LOCATIONS (HEAVY READ)
    http.get(ENDPOINTS.LOCATIONS_FOR_PLAN(planId));

    sleep(1);

    // 6. UPDATE LOCATION
    if (locationId) {
        const locUpdate = {
            ...location,
            notes: 'Updated via endurance test'
        };

        http.put(
            ENDPOINTS.LOCATION_BY_ID(locationId),
            JSON.stringify(locUpdate),
            { headers: { 'Content-Type': 'application/json' } }
        );
    }

    // REASONABLE THINK TIME
    sleep(1 + Math.random() * 2);
}
