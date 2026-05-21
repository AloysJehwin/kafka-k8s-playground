#!/usr/bin/env bash
# scripts/load-test.sh — fire N orders at the local order-service
set -euo pipefail
COUNT="${1:-100}"
ENDPOINT="${2:-http://localhost:8080/api/orders}"

for i in $(seq 1 "$COUNT"); do
  curl -s -X POST "$ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d "{\"customerId\":\"cust-$((RANDOM % 10))\",\"productId\":\"prod-$((RANDOM % 5))\",\"quantity\":$((RANDOM % 5 + 1)),\"amount\":$((RANDOM % 1500 + 10)).00,\"currency\":\"USD\"}" > /dev/null
  if (( i % 10 == 0 )); then echo "sent $i"; fi
done
echo "done — sent $COUNT orders"
