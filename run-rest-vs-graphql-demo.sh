#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR/numfeel-service"

usage() {
  cat <<'EOF'
Usage: ./run-rest-vs-graphql-demo.sh [NAME=VALUE ...]

Settings are environment variables. Pass them either as a command prefix or as
NAME=VALUE arguments:

  START_MYSQL=1 ./run-rest-vs-graphql-demo.sh
  ./run-rest-vs-graphql-demo.sh START_MYSQL=1

Supported names:
  START_MYSQL, MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DB,
  MYSQL_CONTAINER_NAME, SPRING_PROFILES_ACTIVE
EOF
}

# Accept NAME=VALUE arguments so "./run-... START_MYSQL=1" cannot be silently ignored.
for arg in "$@"; do
  if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
    usage
    exit 0
  fi
  if [[ ! "$arg" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then
    echo "Unexpected argument: $arg" >&2
    echo "Settings must use NAME=VALUE form; a bare word such as START_MYSQL is not accepted." >&2
    echo >&2
    usage >&2
    exit 1
  fi
  export "$arg"
  echo "Applied argument setting: ${arg%%=*}"
done

echo "Starting REST vs GraphQL demo on http://localhost:8080/pages/rest-vs-graphql/"
echo
echo "Requires Java 25 and MySQL. Override DB settings with MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DB."
echo "Set START_MYSQL=1 to start a Docker MySQL container for the demo."
echo

java_major_version() {
  java -version 2>&1 | awk -F '"' '/version/ {
    split($2, parts, ".");
    if (parts[1] == "1") print parts[2]; else print parts[1];
  }'
}

JAVA_MAJOR="$(java_major_version || true)"
if [[ "${JAVA_MAJOR:-0}" -lt 25 ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null)"; then
    export JAVA_HOME="$JAVA_25_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    JAVA_MAJOR="$(java_major_version || true)"
  fi
fi

if [[ "${JAVA_MAJOR:-0}" -lt 25 ]]; then
  echo "Java 25 is required, but the current shell is using:"
  java -version
  echo
  echo "Install a JDK 25 and run:"
  echo '  export JAVA_HOME=$(/usr/libexec/java_home -v 25)'
  echo '  export PATH="$JAVA_HOME/bin:$PATH"'
  echo "  ./run-rest-vs-graphql-demo.sh"
  exit 1
fi

echo "Using Java:"
java -version
echo

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DB="${MYSQL_DB:-demomockserver}"

mysql_port_open() {
  local host="$1"
  local port="$2"
  nc -z "$host" "$port" >/dev/null 2>&1
}

if [[ "${START_MYSQL:-0}" == "1" ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "START_MYSQL=1 was set, but Docker is not installed or not on PATH."
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "START_MYSQL=1 was set, but Docker is not running. Start Docker Desktop and try again."
    exit 1
  fi

  MYSQL_PASSWORD="${MYSQL_PASSWORD:-numfeel}"
  CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-numfeel-demo-mysql}"

  if ! docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
    echo "Creating MySQL container $CONTAINER_NAME on port $MYSQL_PORT..."
    docker run -d \
      --name "$CONTAINER_NAME" \
      -e MYSQL_ROOT_PASSWORD="$MYSQL_PASSWORD" \
      -e MYSQL_DATABASE="$MYSQL_DB" \
      -p "$MYSQL_PORT:3306" \
      mysql:8.4 >/dev/null
  elif [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER_NAME")" != "true" ]]; then
    echo "Starting MySQL container $CONTAINER_NAME..."
    docker start "$CONTAINER_NAME" >/dev/null
  fi

  echo "Waiting for MySQL container to accept connections..."
  for _ in {1..60}; do
    if docker exec "$CONTAINER_NAME" mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_PASSWORD" --silent >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
elif ! mysql_port_open "$MYSQL_HOST" "$MYSQL_PORT"; then
  echo "MySQL is not reachable at ${MYSQL_HOST}:${MYSQL_PORT}."
  echo
  echo "Either start your local MySQL and pass credentials:"
  echo "  MYSQL_PASSWORD=your_password ./run-rest-vs-graphql-demo.sh"
  echo
  echo "Or let the script start a Docker MySQL container:"
  echo "  START_MYSQL=1 ./run-rest-vs-graphql-demo.sh"
  exit 1
fi

MYSQL_HOST="$MYSQL_HOST" \
MYSQL_PORT="$MYSQL_PORT" \
MYSQL_USER="$MYSQL_USER" \
MYSQL_PASSWORD="$MYSQL_PASSWORD" \
MYSQL_DB="$MYSQL_DB" \
./mvnw spring-boot:run \
  -Dmaven.test.skip=true \
  -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-dev}" \
  -Dspring-boot.run.arguments="--static.pages-location=file:../numfeel-site/pages/ --static.components-location=file:../numfeel-site/components/"
