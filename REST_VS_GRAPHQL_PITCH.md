# REST vs GraphQL Pitch Demo

This document explains the existing Numfeel REST-vs-GraphQL demo and how to use it for a 10-20 minute technical pitch to an audience that currently uses REST APIs.

The pitch should not be "GraphQL beats REST." The pitch is:

> What changes when clients can choose their response shape?

That framing keeps the conversation technical instead of tribal.

## What We Are Using

This demo uses the existing Java Spring reactive code in `numfeel-service`:

- REST controller: `numfeel-service/src/main/java/run/runnable/numfeelservice/controller/RestVsGraphqlController.java`
- GraphQL controller: `numfeel-service/src/main/java/run/runnable/numfeelservice/controller/BookGraphqlController.java`
- REST service: `numfeel-service/src/main/java/run/runnable/numfeelservice/service/RestVsGraphqlService.java`
- GraphQL service: `numfeel-service/src/main/java/run/runnable/numfeelservice/service/BookGraphqlService.java`
- Dataset initializer: `numfeel-service/src/main/java/run/runnable/numfeelservice/service/BookStoreDataInitializer.java`
- GraphQL schema: `numfeel-service/src/main/resources/graphql/schema.graphqls`
- Frontend: `numfeel-site/pages/rest-vs-graphql/`

The backend is Spring Boot WebFlux plus Spring for GraphQL. The data layer uses reactive `DatabaseClient` against MySQL via R2DBC.

## What Has Been Achieved

The demo already shows four useful tradeoffs:

1. **Over-fetching**
   - REST full returns a broad DTO with 11 fields.
   - The page visibly uses only the list-card fields.
   - This makes over-fetching concrete.

2. **REST can be lean too**
   - REST light returns only the fields needed for this screen.
   - This prevents the bad takeaway that REST is inherently wasteful.

3. **GraphQL handles frontend field changes well**
   - Toggling `description` changes the GraphQL query.
   - REST light does not have the field until the backend contract changes.
   - REST full has it, but only because every previous response already paid for it.

4. **GraphQL moves complexity to the server**
   - Scalar fields cost 1 SQL call.
   - Adding `author` costs `1 + N` SQL calls in this demo.
   - Adding `reviews` costs `1 + N + N` SQL calls.
   - This intentional N+1 behavior makes the operational risk visible.

The frontend now supports an API override:

```text
?api=http://localhost:8080
```

That lets the same page talk to a locally running Spring service during a pitch.

## Running The Existing Demo

Production demo:

```text
https://numfeel.996.ninja/pages/rest-vs-graphql/
```

Local frontend against production API:

```bash
cd numfeel-site
python3 -m http.server 3000
```

Open:

```text
http://localhost:3000/pages/rest-vs-graphql/
```

Single-process local demo (Spring serves both API and static page):

```bash
./run-rest-vs-graphql-demo.sh
```

Open:

```text
http://localhost:8080/pages/rest-vs-graphql/
```

The script passes the correct `numfeel-site/pages` and `numfeel-site/components` static paths into `numfeel-service`.

If local MySQL is not running, use the Docker-backed one-command path:

```bash
START_MYSQL=1 ./run-rest-vs-graphql-demo.sh
```

This starts/reuses a `numfeel-demo-mysql` container, creates the demo database, then starts Spring. The app creates the bookstore tables and seeds the demo data on startup.

Local frontend against local Spring API with a separate static server:

```text
http://localhost:3000/pages/rest-vs-graphql/?api=http://localhost:8080
```

Local Spring backend requires the normal `numfeel-service` setup: Java 25 and MySQL credentials.

```bash
cd numfeel-service
MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD=xxx MYSQL_DB=demomockserver \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The bookstore dataset is initialized by `BookStoreDataInitializer` on application startup. Until initialization completes, the REST and GraphQL endpoints return `503`.

Useful status endpoint:

```text
GET /api/rest-vs-graphql/status
```

## Demo Flow: 10 Minutes

### 0:00-1:00 - Frame The Question

"We already use REST. It is simple, cacheable, and predictable. The question today is not whether REST is bad. The question is where REST starts creating coordination cost for product frontends."

### 1:00-3:00 - Precise Fetching

Show the three panels:

- REST full
- REST light
- GraphQL

Say:

"GraphQL solves over-fetching by design because the client selects fields. But REST light shows the important nuance: careful REST can also be lean."

### 3:00-5:00 - API Evolution

Toggle `description`.

Say:

"The product screen changed. GraphQL adds one field to the query. REST light needs a backend contract change. REST full already has the field, but only because it has been sending that field all along."

### 5:00-7:00 - HTTP Caching

Run the 10-request cache experiment.

Say:

"REST GET gets standard HTTP caching almost for free. GraphQL POST can be cached, but usually through persisted queries, gateways, or app-level caching. It is not the same default browser/CDN path."

### 7:00-9:00 - Server Cost

Set `limit=100`, enable `author` and `reviews`, then run.

Say:

"This is the bill. GraphQL makes nested data easy for the client. If the backend resolves nested fields naively, one request becomes 201 SQL calls. Real GraphQL needs batching, complexity limits, persisted queries, and metrics."

### 9:00-10:00 - Recommendation

"Do not replace REST by default. Keep REST for stable, cacheable, public APIs. Use GraphQL where client flexibility and cross-resource composition are valuable enough to justify the server-side guardrails."

## Demo Flow: 20 Minutes

Use the 10-minute flow, then add:

- Open DevTools Network and inspect `Cache-Control`.
- Show the actual GraphQL POST body.
- Compare `limit=10` and `limit=100`.
- Show the Spring files listed above so the audience sees it is real WebFlux/Spring GraphQL code.
- Ask where the current system hurts: over-fetching, under-fetching, endpoint sprawl, mobile payload size, or frontend/backend coordination.

## Pros And Cons

| Area | REST | GraphQL |
| --- | --- | --- |
| Stable resource APIs | Strong | Often unnecessary |
| HTTP caching | Excellent with GET | Needs extra design |
| Client field flexibility | Lower | Excellent |
| API evolution for UI fields | Backend-dependent | Client query change |
| Backend predictability | Higher | Requires guardrails |
| Nested data composition | Multiple endpoints or custom DTOs | Natural |
| Operational risk | Familiar | N+1, query abuse, resolver complexity |
| Public APIs | Usually easier to govern | Use persisted queries and limits |
| Internal BFFs | Can become endpoint sprawl | Strong fit |

## Recommended Adoption Pattern

Recommended:

1. Keep REST for stable, cacheable, public APIs.
2. Use GraphQL behind a BFF for product frontends that change often.
3. Add batching before launch, not after.
4. Add query depth and complexity limits before broad exposure.
5. Track per-query DB calls, resolver timings, and cache hit rates.

Avoid:

- Exposing unlimited public GraphQL without persisted queries.
- Treating GraphQL as a direct database gateway.
- Assuming GraphQL is automatically faster.
- Using one giant REST DTO and claiming REST is inherently wasteful.

## Current Demo Caveat

The GraphQL service intentionally demonstrates N+1 behavior:

```text
books only:          1 SQL
books + author:      1 + N SQL
books + reviews:     1 + N SQL
books + both:        1 + N + N SQL
```

That is deliberate for teaching. A production GraphQL implementation should use batching/DataLoader-style loading or join planning where appropriate.
