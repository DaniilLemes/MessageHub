#!/usr/bin/env bash
# MessageHub - local demo (Kafka 4.0, KRaft, many Swing clients)
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KAFKA_DIR="$PROJECT_DIR/kafka_2.13-4.0.0"             # <-- adjust if your folder name differs
CONF="$KAFKA_DIR/config/broker.properties" # Kafka 4 ships this file for combined mode
JAR="$PROJECT_DIR/target/MessageHub-0.0.1-SNAPSHOT.jar"
CLIENT_PIDS=()  BROKER_PID=""

log() { printf "\e[32m[messagehub]\e[0m %s\n" "$*"; }

# 1) one-time storage format  (safe to repeat with --ignore-formatted)
format_storage() {
  log "Formatting log dirs for KRaft (only first run)…"
  CLUSTER_ID="$("$KAFKA_DIR/bin/kafka-storage.sh" random-uuid)"
  "$KAFKA_DIR/bin/kafka-storage.sh" format \
      -t "$CLUSTER_ID" -c "$CONF" --ignore-formatted >/dev/null
}

# 2) start combined controller+broker
start_kafka() {
  log "Starting Kafka broker (KRaft)…"
  "$KAFKA_DIR/bin/kafka-server-start.sh" "$CONF" >/dev/null 2>&1 &
  BROKER_PID=$!
  until nc -z localhost 9092; do sleep 0.5; done
  log "Kafka is up   (pid=$BROKER_PID)."
}

# 3) create chat topics once
create_topic() {
  "$KAFKA_DIR/bin/kafka-topics.sh" --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "$1" --partitions "$2" --replication-factor 1 \
      >/dev/null
}
init_topics() {
  log "Ensuring chat topics exist…"
  create_topic chat-room     3
  create_topic chat-private  3
  create_topic chat-presence 1
}

# 4) build Spring Boot JAR once
build_jar() {
  if [[ ! -f "$JAR" ]]; then
    log "Building fat JAR (mvn clean package)…"
    mvn -q -B clean package
  fi
}

# 5) spawn a Swing client
new_client() {
  log "Launching chat window…"
  ( java -jar "$JAR" ) &
  CLIENT_PIDS+=($!)
}

# 6) clean shutdown
cleanup() {
  log "Stopping clients…" ; for p in "${CLIENT_PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  log "Stopping Kafka…"   ; [[ $BROKER_PID ]] && kill "$BROKER_PID" 2>/dev/null || true
  wait
  log "Bye!"
}
trap cleanup EXIT

#format_storage
start_kafka
init_topics
build_jar

log "Ready!  n = new chat window   |   q = quit all"
while read -r -n1 key; do case "$key" in n|N) new_client ;; q|Q) break ;; esac; done
