#!/usr/bin/env bash
# GameFlow LB — local startup (no containers, no cloud).
# Starts the Spring Boot backend (REST + WS on :8080, RMI registry on :1099)
# and the Vite frontend (http://localhost:5173).
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21+ is required on PATH." >&2; exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "Node.js + npm are required on PATH." >&2; exit 1
fi

echo "→ backend:  mvn spring-boot:run  (http://localhost:8080)"
(cd "$ROOT/backend" && mvn -q spring-boot:run > /tmp/gameflow-backend.log 2>&1 &) 
echo "→ frontend: npm run dev         (http://localhost:5173)"
(cd "$ROOT/frontend" && npm run dev > /tmp/gameflow-frontend.log 2>&1 &)

echo "Waiting for backend…"
for i in $(seq 1 60); do
  if curl -sf -o /dev/null http://localhost:8080/api/system; then break; fi
  sleep 2
done
curl -sf -o /dev/null http://localhost:8080/api/system \
  && echo "✓ backend up" \
  || { echo "✗ backend failed to start — see /tmp/gameflow-backend.log"; exit 1; }

echo "✓ open http://localhost:5173 and press Start Simulation"
