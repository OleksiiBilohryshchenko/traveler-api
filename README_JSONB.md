# Travel Planner API — JSONB Support

This module adds **PostgreSQL JSONB–based flexible storage** to the Travel Planner API.  
It enables dynamic metadata and attributes while preserving transactional integrity, versioning, and query performance.

The implementation follows a **hybrid relational + document approach**, using JSONB only where schema flexibility is justified.

---

## Key Features

- JSONB storage for **dynamic, user-defined data**
- Optimistic locking for **safe concurrent updates**
- Merge vs replace update strategies
- Native PostgreSQL JSONB queries with indexes
- Full integration testing with real PostgreSQL (Testcontainers)

---

## JSONB Fields

### Travel Plans

**Table:** `travel_plans`  
**Column:** `metadata` (`jsonb`)

Used for:
- user preferences
- tags
- participants
- optional/custom fields

### Locations

**Table:** `locations`  
**Column:** `attributes` (`jsonb`)

Used for:
- ratings
- category
- accessibility options
- contact info
- business hours
- tags

---

## Database Migration

Migration `V3__add_jsonb_columns.sql` introduces:

```sql
ALTER TABLE travel_plans
    ADD COLUMN metadata JSONB DEFAULT '{}'::jsonb NOT NULL;

ALTER TABLE locations
    ADD COLUMN attributes JSONB DEFAULT '{}'::jsonb NOT NULL;
```

### Indexes

```sql
CREATE INDEX idx_travel_plans_metadata_gin
    ON travel_plans USING GIN (metadata);

CREATE INDEX idx_locations_attributes_gin
    ON locations USING GIN (attributes);

CREATE INDEX idx_travel_plans_metadata_travel_style
    ON travel_plans ((metadata->>'travel_style'));

CREATE INDEX idx_locations_attributes_category
    ON locations ((attributes->>'category'));

CREATE INDEX idx_locations_attributes_rating
    ON locations (((attributes->>'rating')::numeric));
```

---

## JSON Mapping

JSONB columns are mapped using **Hibernate Types**:

```java
@Type(JsonBinaryType.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> metadata;
```

Dependency:

```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

---

## Update Semantics

### Merge (default)

Shallow merge at root level.

### Replace

Full replacement.

Nested objects are **not deep-merged** by design.

---

## Optimistic Locking

All update operations require a `version` field.  
Version mismatches result in **409 Conflict**.

---

## REST API

### Travel Plan Metadata

- `GET /api/plans/{id}/metadata`
- `PATCH /api/plans/{id}/metadata`
- `DELETE /api/plans/{id}/metadata/{key}`
- `GET /api/plans/search`
- `GET /api/plans/by-travel-style/{style}`
- `GET /api/plans/by-budget-category/{category}`

### Location Attributes

- `GET /api/plans/{planId}/locations/{locId}/attributes`
- `PATCH /api/plans/{planId}/locations/{locId}/attributes`
- `DELETE /api/plans/{planId}/locations/{locId}/attributes/{key}`
- `GET /api/locations/search`
- `GET /api/locations/by-category/{category}`
- `GET /api/plans/{planId}/locations/top-rated`

---

## JSONB Queries Used

- `@>` containment
- `->` / `->>` extraction
- numeric casting
- GIN indexes

---

## Testing

- Integration tests with PostgreSQL 15 (Testcontainers)
- Unit tests for service layer
- Version conflict and merge/replace coverage

Run:

```bash
mvn test
```

---

## Design Trade-offs

- JSONB used only for evolving data
- Core domain remains relational
- Shallow merge only
- No JSON schema validation

---

