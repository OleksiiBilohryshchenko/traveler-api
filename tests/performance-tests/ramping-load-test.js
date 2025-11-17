import { sleep, check } from 'k6';
import { DEFAULT_THRESHOLDS } from './config/endpoints.js';
import { checkHealth } from './utils/api-client.js';

export const options = {
    stages: [
        { duration: '30s', target: 20 },     // Розігрів
        { duration: '30s', target: 50 },     // Легке навантаження
        { duration: '30s', target: 100 },    // Середнє
        { duration: '30s', target: 150 },    // Високе
        { duration: '30s', target: 200 },    // Пік
        { duration: '30s', target: 0 },      // Зняття навантаження
    ],

    thresholds: {
        ...DEFAULT_THRESHOLDS,
        'http_req_failed': ['rate<0.05'],        // менше 5% помилок
        'http_req_duration': ['p(95)<1000'],     // 95% < 1 секунда
        'checks': ['rate>0.90'],                 // 90% успішних перевірок
    },

    noConnectionReuse: false,
    userAgent: 'K6-Ramping-Load-Test/1.0'
};

export default function () {
    const ok = checkHealth();

    check(ok, {
        'health endpoint is responsive': (r) => r === true,
    });

    sleep(1);
}
