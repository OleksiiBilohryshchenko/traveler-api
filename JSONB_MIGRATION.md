# Laboratory Work: JSONB Implementation in Travel Planner API

## Introduction

This document describes the implementation of PostgreSQL JSONB functionality for the Travel Planner application. The main goal was to add flexible schema support using JSONB columns for storing dynamic metadata and attributes.

Two main JSONB fields were added:
- `travel_plans.metadata` - stores user preferences, tags, participant info
- `locations.attributes` - stores location-specific data like ratings, categories, contact details

## Technical Implementation

### Database Changes
I added JSONB support using Flyway migration V3. The migration creates two JSONB columns with GIN indexes for query performance. PostgreSQL JSONB operators are used in queries: @>, ?, ?|, ?&, ->, ->>

### Application Code
The Java side uses Hibernate Types library to map JSONB to Java Map objects. I created DTOs for handling updates with merge/replace strategies. Repository layer has native SQL queries for JSONB operations. Service layer handles the business logic with version checking. REST controllers expose the functionality through HTTP endpoints.


### Required Dependencies
I added Hibernate Types library to pom.xml for JSONB support:
```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.3</version>
</dependency>
```

This library provides the @Type annotation needed to map JSONB columns to Java objects.

### Database Schema
The V3 migration adds JSONB columns and indexes:
```sql
-- Add JSONB columns
ALTER TABLE travel_plans ADD COLUMN metadata JSONB DEFAULT '{}'::jsonb NOT NULL;
ALTER TABLE locations ADD COLUMN attributes JSONB DEFAULT '{}'::jsonb NOT NULL;

-- GIN indexes for fast queries
CREATE INDEX idx_travel_plans_metadata_gin ON travel_plans USING GIN (metadata);
CREATE INDEX idx_locations_attributes_gin ON locations USING GIN (attributes);

-- Additional indexes for common queries
CREATE INDEX idx_travel_plans_metadata_travel_style ON travel_plans ((metadata->>'travel_style'));
CREATE INDEX idx_locations_attributes_category ON locations ((attributes->>'category'));
CREATE INDEX idx_locations_attributes_rating ON locations (((attributes->>'rating')::numeric));
```

The GIN indexes are important for query performance when using JSONB operators.


## Data Structure Examples

### TravelPlan Metadata
The metadata field stores different types of information:
```json
{
  "preferences": {
    "travel_style": "adventure",
    "budget_category": "moderate",
    "pace": "relaxed"
  },
  "participants": [
    {
      "name": "John Doe",
      "age": 30,
      "role": "organizer"
    }
  ],
  "tags": ["europe", "summer", "family"],
  "custom_fields": {
    "group_size": 4,
    "dietary_restrictions": ["vegetarian"]
  }
}
```

### Location Attributes
The attributes field for locations contains:
```json
{
  "rating": 4.5,
  "category": "museum",
  "contact": {
    "phone": "+33123456789",
    "website": "https://example.com"
  },
  "business_hours": {
    "monday": "09:00-18:00",
    "tuesday": "09:00-18:00"
  },
  "accessibility": ["wheelchair", "elevator"],
  "tags": ["historical", "art"]
}
```



## REST API Endpoints

I created two new controllers for handling JSONB operations.

### Travel Plan Metadata Endpoints

**Get metadata:**
```http
GET /api/plans/{planId}/metadata
```

**Update metadata:**
```http
PATCH /api/plans/{planId}/metadata
Content-Type: application/json

{
  "version": 5,
  "metadata": {
    "tags": ["updated"]
  },
  "merge": true
}
```
The merge flag controls whether to merge with existing data (true) or replace completely (false).

**Delete specific key:**
```http
DELETE /api/plans/{planId}/metadata/{key}?version=5
```

**Search plans:**
```http
GET /api/plans/search?travel_style=adventure&tags=europe&is_public=true
```

**Find by travel style:**
```http
GET /api/plans/by-travel-style/adventure
```

**Find by tags:**
```http
GET /api/plans/by-tags?tags=europe,summer
```

### Location Attributes Endpoints

**Get attributes:**
```http
GET /api/plans/{planId}/locations/{locationId}/attributes
```

**Update attributes:**
```http
PATCH /api/plans/{planId}/locations/{locationId}/attributes
Content-Type: application/json

{
  "version": 3,
  "attributes": {
    "rating": 4.9
  },
  "merge": true
}
```

**Delete specific key:**
```http
DELETE /api/plans/{planId}/locations/{locationId}/attributes/{key}?version=3
```

**Search locations:**
```http
GET /api/locations/search?category=museum&min_rating=4.5
```

**Find by category:**
```http
GET /api/locations/by-category/museum
```

**Find top rated:**
```http
GET /api/plans/{planId}/locations/top-rated?min_rating=4.0
```



## PostgreSQL JSONB Operators

I used several JSONB operators in the repository queries:

**Containment (@>)** - checks if left JSON contains right JSON
```sql
SELECT * FROM travel_plans 
WHERE metadata @> '{"preferences": {"pace": "relaxed"}}';
```

**Key Exists (?)** - checks if key exists
```sql
SELECT * FROM travel_plans 
WHERE metadata ? 'participants';
```

**Array Overlap (?|)** - checks if arrays have any common elements
```sql
SELECT * FROM travel_plans 
WHERE metadata->'tags' ?| array['europe', 'summer'];
```

**Array Contains All (?&)** - checks if array contains all specified elements
```sql
SELECT * FROM travel_plans 
WHERE metadata->'tags' ?& array['europe', 'summer'];
```

**Get JSON Field (->)** - extracts JSON object
```sql
SELECT metadata->'preferences' FROM travel_plans;
```

**Get Text Field (->>)** - extracts as text
```sql
SELECT metadata->'preferences'->>'travel_style' FROM travel_plans;
```

These operators work with the GIN indexes I created in the migration.



## Merge vs Replace Operations

I implemented two update strategies:

**Merge (merge: true)** - only updates specified keys, keeps others
```
Before: {"a": 1, "b": 2, "c": 3}
Update: {"b": 99, "d": 4}
After:  {"a": 1, "b": 99, "c": 3, "d": 4}
```

**Replace (merge: false)** - replaces entire JSON object
```
Before: {"a": 1, "b": 2, "c": 3}
Update: {"x": 100}
After:  {"x": 100}
```

The merge is a shallow merge at the root level only. Nested objects are replaced completely if updated.



## Version Control

All update operations check the version field to prevent concurrent modification issues. If the version doesn't match, the API returns 409 Conflict error.

Example request:
```json
{
  "version": 5,
  "metadata": {...}
}
```

If someone else updated the record (version is now 6), you'll get an error telling you to refetch the current data.



## Testing

I wrote tests at different levels:

**Integration Tests** (30 tests)
- TravelPlanMetadataIntegrationTest - 14 tests for metadata operations
- LocationAttributesIntegrationTest - 16 tests for attributes operations
- These tests use Testcontainers with real PostgreSQL 15

**Unit Tests** (12 tests)
- TravelPlanServiceTest - tests service layer with Mockito

Running tests:
```bash
mvn test
```

The integration tests spin up a real PostgreSQL container using Testcontainers, so they test the actual JSONB functionality rather than mocking it.



## Performance Notes

**Indexes**
The GIN indexes help with JSONB query performance. Without these indexes, PostgreSQL would have to scan every row and parse the JSON for each query. With GIN indexes, containment queries (@>), key existence (?), and array operations (?|, ?&) are much faster.

I also added some specialized indexes for common query patterns:
```sql
CREATE INDEX idx_metadata_travel_style ON travel_plans ((metadata->>'travel_style'));
CREATE INDEX idx_attributes_rating ON locations (((attributes->>'rating')::numeric));
```

These help when filtering by specific JSON fields frequently.

**Query Optimization**
- Use @> for exact matches (it's indexed)
- Extract values with ->> for simple comparisons
- You can combine JSONB filters with regular column filters in WHERE clauses



## How to Deploy This

**Step 1:** Add the Hibernate Types dependency to pom.xml

**Step 2:** Run the Flyway migration
```bash
mvn flyway:migrate
```

**Step 3:** Copy the updated entities (TravelPlan.java, Location.java)

**Step 4:** Add the new repositories, service methods, and controllers

**Step 5:** Run tests to verify everything works
```bash
mvn test
```

## Usage Examples

**Adding preferences to a travel plan:**
```http
PATCH /api/plans/{id}/metadata
{
  "version": 1,
  "metadata": {
    "preferences": {
      "travel_style": "adventure",
      "budget_category": "moderate"
    }
  },
  "merge": true
}
```

**Adding tags:**
```http
PATCH /api/plans/{id}/metadata
{
  "version": 2,
  "metadata": {
    "tags": ["europe", "summer"]
  },
  "merge": true
}
```

**Setting location rating:**
```http
PATCH /api/plans/{planId}/locations/{locId}/attributes
{
  "version": 1,
  "attributes": {
    "rating": 4.5,
    "category": "museum"
  },
  "merge": true
}
```

**Searching locations:**
```http
GET /api/locations/search?category=museum&min_rating=4.0&tags=historical
```



## Things I Learned

**What worked well:**
- Using merge for partial updates is convenient
- The version checking prevents data loss from concurrent updates
- GIN indexes make JSONB queries fast enough
- Testcontainers helped catch PostgreSQL-specific issues

**What to avoid:**
- Don't put large amounts of data in JSONB fields
- Don't use JSONB when you need complex joins
- Don't forget to add indexes if you'll be querying the JSONB fields
- Deeply nested JSON (more than 3 levels) gets hard to query
- Mixing relational and document approaches can make the schema confusing

**Issues I encountered:**
- Had to cast JSONB values to numeric for rating comparisons
- The merge is shallow only - nested objects get replaced completely
- Version conflicts happen more often than expected when testing concurrent updates



## Possible Improvements

Some things that could be added later:
- JSON Schema validation at the database level
- Full-text search in JSONB fields
- Audit trail for tracking JSONB changes over time
- Better caching for frequent queries

## References

I used these resources while implementing this:
- PostgreSQL JSONB documentation
- Hibernate Types library GitHub page
- Stack Overflow for specific JSONB operator questions

## Summary

The implementation adds flexible schema support using PostgreSQL JSONB. Main components:
- Database migration with JSONB columns and GIN indexes
- Java entities using Hibernate Types for mapping
- Repository methods with native JSONB queries
- Service layer with merge/replace logic and version checking
- REST API endpoints for CRUD and search
- 42 tests covering integration and unit levels

Files included:
- V3__add_jsonb_columns.sql - database migration
- Updated models (TravelPlan.java, Location.java)
- 4 DTOs for requests
- 2 repositories with JSONB queries
- Updated TravelPlanService
- 2 new controllers
- 3 test files
