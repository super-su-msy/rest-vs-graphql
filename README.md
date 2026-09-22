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

Settings are environment variables, so they go in front of the command. The same value also
works as a `NAME=VALUE` argument: `./run-rest-vs-graphql-demo.sh START_MYSQL=1`. A bare
`START_MYSQL` without `=1` is rejected instead of being silently ignored.

Verify the demo is up:

```bash
curl -s http://localhost:8080/api/rest-vs-graphql/status
# {"status":200,"data":{"dataReady":true,"authors":200,"books":10000,"reviews":25054}}
```

The script uses Java 25, starts or reuses the `numfeel-demo-mysql` container, creates the demo database, and lets Spring create and seed the bookstore tables on startup.

## Structure

- `numfeel-service/` - Spring Boot WebFlux + Spring GraphQL backend
- `numfeel-site/pages/rest-vs-graphql/` - static frontend demo page
- `REST_VS_GRAPHQL_PITCH.md` - pitch notes and runbook
- `PITCH_ONE_PAGER.html` - print-ready handout; its content is sized to the top half of a Letter sheet (`PITCH_ONE_PAGER.pdf` is the same thing pre-rendered)
- `run-rest-vs-graphql-demo.sh` - single-process local demo launcher
