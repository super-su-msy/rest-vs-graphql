# REST vs GraphQL Pitch Demo

A focused Spring Boot WebFlux + Spring GraphQL demo that compares REST and GraphQL side by side for a 10-20 minute technical pitch.

## Run Locally

```bash
./run-rest-vs-graphql-demo.sh
```

Then open http://localhost:8080/pages/rest-vs-graphql/.

If local MySQL is not running:

```bash
START_MYSQL=1 ./run-rest-vs-graphql-demo.sh
```

The script uses Java 25, starts or reuses the `numfeel-demo-mysql` container, creates the demo database, and lets Spring create and seed the bookstore tables on startup.

## Structure

- `numfeel-service/` - Spring Boot WebFlux + Spring GraphQL backend
- `numfeel-site/pages/rest-vs-graphql/` - static frontend demo page
- `REST_VS_GRAPHQL_PITCH.md` - pitch notes and runbook
- `run-rest-vs-graphql-demo.sh` - single-process local demo launcher
