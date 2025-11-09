# Traveler API

RESTful сервіс для планування подорожей з підтримкою конкурентного редагування, гарантіями цілісності даних (ACID) та оптимістичним блокуванням (Optimistic Locking).

## Структура проекту
~~~
src/
└─ main/
├─ java/ua/sumdu/dds/travelerapi/
│   ├─ controller/                # REST-контролери (обробка HTTP запитів)
│   │    ├─ HealthCheckController
│   │    ├─ LocationController
│   │    └─ TravelPlanController
│   │
│   ├─ dto/                       # DTO-об’єкти для запитів/відповідей
│   │    ├─ ApiErrorResponse
│   │    ├─ CreateLocationRequest
│   │    ├─ CreateTravelPlanRequest
│   │    ├─ HealthCheckResponse
│   │    ├─ UpdateLocationRequest
│   │    └─ UpdateTravelPlanRequest
│   │
│   ├─ exception/                 # Кастомні винятки для бізнес-логіки
│   │    ├─ NotFoundException
│   │    ├─ ValidationException
│   │    └─ VersionConflictException
│   │
│   ├─ handler/                   # Глобальний обробник помилок
│   │    └─ GlobalExceptionHandler
│   │
│   ├─ model/                     # JPA-сутності (таблиці БД)
│   │    ├─ Location
│   │    └─ TravelPlan
│   │
│   ├─ repository/                # Репозиторії для доступу до даних
│   │    ├─ LocationRepository
│   │    └─ TravelPlanRepository
│   │
│   ├─ service/                   # Бізнес-логіка та транзакції
│   │    ├─ HealthCheckService
│   │    └─ TravelPlanService
│   │
│   └─ TravelerApiApplication     # Точка входу (Spring Boot)
│
└─ resources/
├─ application.properties     # Налаштування підключення до БД, логування
└─ db/
└─ migration/             # Flyway міграції структури БД
└─ V1__init.sql

tests/
└─ *.hurl                            # API тест-кейси
~~~
## Архітектура та сутності

### TravelPlan
| Поле | Тип | Опис |
|------|-----|------|
| id | UUID | Первинний ключ |
| title | string | Назва плану (обов’язково) |
| description | string | Опис (необов’язково) |
| start_date / end_date | date | Період подорожі (`end_date >= start_date`) |
| budget | decimal | Загальний бюджет |
| currency | string | Валюта (`USD` за замовчуванням) |
| is_public | boolean | Доступність плану |
| version | int | **Optimistic Locking** |
| created_at / updated_at | timestamp | Автоматичні службові поля |

### Location
| Поле | Тип | Опис |
|------|-----|------|
| id | UUID | Первинний ключ |
| travel_plan_id | UUID | FK → TravelPlan (ON DELETE CASCADE) |
| name | string | Назва локації |
| latitude / longitude | decimal | Валідація координат |
| visit_order | int | Порядок (**визначається автоматично**) |
| arrival_date / departure_date | timestamp | `departure >= arrival` |
| budget | decimal | Частина бюджету |
| notes | text | Коментар |
| created_at | timestamp | Дата створення |

---

## ER-діаграма

┌───────────────────┐ ┌─────────────────────────┐
│ travel_plan │ 1 ∞ │ location │
├───────────────────┤ ├─────────────────────────┤
│ id (UUID) │◄────────│ travel_plan_id (FK) │
│ title │ │ name │
│ ... │ │ latitude/longitude │
│ version (int) │ ← optimistic locking │
│ created_at,updated │ │ visit_order (auto inc.) │
└───────────────────┘ └─────────────────────────┘


---

## API Endpoints

### Travel Plans
| Method | Endpoint | Опис |
|--------|----------|------|
| GET | `/api/travel-plans` | Отримати список планів |
| POST | `/api/travel-plans` | Створити новий план |
| GET | `/api/travel-plans/{id}` | Отримати план з локаціями |
| PUT | `/api/travel-plans/{id}` | Оновити план (**з version**) |
| DELETE | `/api/travel-plans/{id}` | Видалити план (каскадно) |

### Locations
| Method | Endpoint | Опис |
|--------|----------|------|
| POST | `/api/travel-plans/{id}/locations` | Додати локацію (`visit_order = max + 1`) |
| PUT | `/api/locations/{id}` | Оновити локацію |
| DELETE | `/api/locations/{id}` | Видалити локацію |

---

## Конкурентність

### Optimistic Locking
При оновленні клієнт передає поле `version`.  
Якщо версія не співпадає з актуальною в БД → `409 Conflict`.

**Приклад відповіді:**
```json
{
  "error": "Conflict: plan was modified",
  "current_version": 4
}

Автоматичне visit_order

SELECT COALESCE(MAX(visit_order), 0) + 1

Flyway Міграції

Розташування:

src/main/resources/db/migration

Особливості:

UUID PK
FK з ON DELETE CASCADE
CHECK обмеження для дат та координат
Індекси для ефективних SELECT

Тестування (Hurl)
hurl --test tests/*.hurl --variable host=http://localhost:4567

Запуск

Вимоги
Java 21
Maven
PostgreSQL (порт 5432)
База: traveler

Конфігурація (application.properties)
server.port=4567
spring.datasource.url=jdbc:postgresql://localhost:5432/traveler
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
spring.jpa.hibernate.ddl-auto=none
spring.flyway.enabled=true
spring.jackson.property-naming-strategy=SNAKE_CASE

Старт
mvn spring-boot:run

Перевірка
curl -i http://localhost:4567/api/health

Стек
Java 21 / Spring Boot 3
PostgreSQL + Flyway
Spring Data JPA / Hibernate
Hurl для e2e API тестів