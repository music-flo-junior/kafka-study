#!/bin/bash
set -e

# Kafka Connect 실행
/etc/confluent/docker/run &

echo "Waiting for Kafka Connect to be ready..."
until curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/connectors | grep -q "200"; do
  echo "Kafka Connect is not ready yet. Waiting..."
  sleep 5
done

echo "Kafka Connect is ready. Registering connectors..."

# Kafka Connect 자동 등록
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @/init/file-source.json

echo "Kafka Connect connectors created successfully."

wait
