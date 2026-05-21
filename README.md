# Eventflow — Event-Driven Order Processing on Kafka + Kubernetes

Production-style learning capstone: 4 Spring Boot 3 services on Java 21, Kafka with Avro/Schema Registry, transactional outbox, Kafka Streams joins, retry topics + DLQ, Strimzi on Kubernetes, observability with Prometheus/Grafana/OpenTelemetry.

## Architecture

```
                                    ┌──────────────┐
                                    │ schema-reg.  │
                                    └──────┬───────┘
                                           │
        ┌───────────┐  POST /orders        │
        │  Client   │──────────────►┌──────┴─────────┐
        └───────────┘               │ order-service  │
                                    │ (REST + Outbox)│
                                    │   ↳ Postgres   │
                                    └────────┬───────┘
                                             │ outbox relay
                                             ▼
                          ┌───────────── Kafka ─────────────┐
                          │  orders.placed                  │
                          │  payments.processed             │
                          │  inventory.reserved             │
                          │  orders.completed               │
                          └──┬───────────┬───────────────┬──┘
                             │           │               │
                  ┌──────────▼──┐  ┌─────▼────────┐  ┌───▼───────────┐
                  │ payment-svc │  │ inventory-svc │  │ notif-service │
                  │  (Streams)  │  │  (Streams +   │  │  @RetryTopic  │
                  │             │  │   KTable +    │  │  + DLQ        │
                  │             │  │   Join)       │  │               │
                  └─────────────┘  └───────────────┘  └───────────────┘
```

**Flow**: client posts an order → order-service writes Order + OutboxEvent in one transaction → relay publishes to `orders.placed` → payment-service emits `payments.processed` → inventory-service emits `inventory.reserved` → inventory-service joins both into `orders.completed` → notification-service consumes for downstream effects → order-service listener updates DB.

## Tech Stack

| Layer | Stack |
|-------|-------|
| Language | Java 21 (virtual threads), Spring Boot 3.3 |
| Messaging | Apache Kafka (Confluent images locally, Strimzi on K8s), Schema Registry, Avro |
| Streams | Kafka Streams with EXACTLY_ONCE_V2 |
| DB | Postgres 16, Flyway migrations |
| Resilience | Resilience4j, transactional outbox |
| Container | Buildpacks (paketo) → distroless-style images |
| Orchestration | Kubernetes (kind locally), Kustomize overlays, Helm chart |
| Observability | Micrometer → Prometheus, OpenTelemetry → OTLP, structured logs |

## Prerequisites

```bash
brew install openjdk@21 maven docker kind kubectl helm k9s stern jq
brew install --cask docker
```

## Quickstart — Local (docker-compose)

```bash
# 1. Start infra
make infra-up

# 2. Build Java
make build

# 3. Run services in 4 terminals (or via your IDE)
./mvnw -pl services/order-service        spring-boot:run
./mvnw -pl services/payment-service      spring-boot:run
./mvnw -pl services/inventory-service    spring-boot:run
./mvnw -pl services/notification-service spring-boot:run

# 4. Smoke test
make smoke-test
```

Visit:
- Kafka UI: http://localhost:8090
- Schema Registry: http://localhost:8081
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- Order API: http://localhost:8080

## Quickstart — Kubernetes (kind)

```bash
# 1. Create kind cluster + install Strimzi operator
make kind-up

# 2. Build Spring Boot images (buildpacks)
make image-build

# 3. Load images into kind
make k8s-load-images

# 4. Deploy via Kustomize OR Helm
make k8s-deploy        # kustomize
# or
make helm-deploy       # helm

# 5. Watch the cluster
k9s -n eventflow
```

## Production Patterns Demonstrated

| Pattern | Where | Why |
|---------|-------|-----|
| **Transactional outbox** | `order-service` | Avoid dual-write to DB + Kafka |
| **Idempotent producer** | `KafkaDefaults.producer()` | Safe retries, no duplicates |
| **Exactly-once Streams** | `payment-service`, `inventory-service` | EOS-V2 |
| **Stream-stream join** | `inventory-service` | Combine payment + inventory facts |
| **Retry topics + DLQ** | `notification-service` | `@RetryableTopic` + `@DltHandler` |
| **Schema Registry + Avro** | All services | Schema evolution without breakage |
| **Container-aware JVM** | All services | `MaxRAMPercentage=75` not `-Xmx` |
| **K8s probes** | All services | `/actuator/health/{liveness,readiness}` |
| **Graceful shutdown** | All services | `preStop sleep 10` + `terminationGracePeriodSeconds: 60` |
| **HPA + PDB** | `order-service` | Scale safely under load |
| **NetworkPolicy** | `infra/k8s/base` | Default-deny, explicit allow |
| **StatefulSet for Streams** | `inventory-service` | Stable identity for state stores |

## Project Structure

```
kafka-k8s-playground/
├── pom.xml                       # parent POM, Spring Boot 3.3, Java 21
├── libs/
│   ├── common-events/            # Avro schemas + Topics constants
│   └── common-kafka/             # KafkaDefaults, ErrorHandlers
├── services/
│   ├── order-service/            # REST + JPA + outbox
│   ├── payment-service/          # Kafka Streams (mapValues)
│   ├── inventory-service/        # Streams (KTable + join)
│   └── notification-service/     # @KafkaListener + @RetryableTopic
├── infra/
│   ├── docker/                   # docker-compose, prometheus.yml, otel
│   ├── k8s/
│   │   ├── kind-config.yaml
│   │   ├── base/                 # Deployments, Services, Strimzi Kafka CR
│   │   └── overlays/{local,prod} # Kustomize overlays
│   └── helm/eventflow/           # Helm chart (single chart, all services)
├── scripts/
└── docs/
```

## Learning Path (8-week roadmap)

See `docs/ROADMAP.md` for the full 8-week schedule pairing this codebase with Kafka/Kubernetes books and courses.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `OrderPlaced` events not appearing in `orders.placed` | Outbox relay paused on error | Check `order-service` logs; confirm Kafka broker reachable |
| Streams app `INVALID_TOPIC_EXCEPTION` | Topic auto-create off + topic missing | Create via `kubectl apply -f kafka-cluster.yaml` (KafkaTopic CRs) |
| Consumer stuck at lag | Slow downstream / poison pill | Inspect DLT topics; check `kafka-consumer-groups.sh --describe` |
| Pod CrashLoopBackOff with OOM | JVM not aware of limits | Confirm `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` |
| Schema Registry 409 | Incompatible schema change | Update compatibility mode or rev the schema (`OrderPlacedV2`) |

## License

MIT — learning material, use freely.
