# 8-Week Learning Roadmap — pairing this repo with theory

Use this codebase as the *hands-on lab*. Books and courses below give the *why*.

## Week 1 — Foundations

**Read:**
- *Kafka: The Definitive Guide* (2nd ed.) — Ch. 1-3 (intro, install, producers)
- *Kubernetes: Up & Running* (3rd ed.) — Ch. 1-2

**Do:**
- `make infra-up` and explore Kafka UI
- Read every Avro schema in `libs/common-events/src/main/avro`
- Trace a single produce → consume cycle in the Kafka UI

## Week 2 — Producers, consumers, schemas

**Read:**
- *Kafka: The Definitive Guide* — Ch. 4-5 (consumers, internals)
- Confluent Schema Registry docs (compatibility rules)

**Do:**
- Run `order-service`, POST 100 orders with `hey` or `wrk`
- Inspect the outbox table in Postgres — observe relay behavior
- Break a schema deliberately (rename `productId` → `sku`) — observe Schema Registry rejection

## Week 3 — Kafka Streams

**Read:**
- *Kafka: The Definitive Guide* — Ch. 14 (Streams)
- Confluent Streams docs (KStream vs KTable, joins, windowing)

**Do:**
- Read `payment-service/streams/PaymentTopology.java`
- Read `inventory-service/streams/OrderCompletionTopology.java`
- Write a Streams test using `TopologyTestDriver`
- Add a windowed aggregation (e.g. orders per customer per hour)

## Week 4 — Spring for Kafka, Resilience

**Read:**
- Spring for Apache Kafka reference (in full)
- *Designing Data-Intensive Applications* — Ch. 11 (stream processing)

**Do:**
- Read `notification-service/consumer/OrderCompletedListener.java`
- Trigger a DLQ scenario by submitting an order with `orderId` starting `fail`
- Inspect retry topics in Kafka UI

## Week 5 — Kubernetes fundamentals

**Read:**
- *Kubernetes: Up & Running* — Ch. 3-12
- KodeKloud CKAD labs (or equivalent)

**Do:**
- `make kind-up` and explore with `k9s`
- `kubectl get pods -n eventflow -w`
- Kill a pod, observe self-healing

## Week 6 — Strimzi + Operators

**Read:**
- Strimzi documentation (Kafka CR, KafkaTopic, KafkaUser)
- [Kubernetes Patterns](https://k8spatterns.io/) — Operator pattern

**Do:**
- Install Strimzi operator (already automated in `make kind-up`)
- Apply `infra/k8s/base/kafka-cluster.yaml` and watch Kafka pods come up
- Scale Kafka up/down via the CR

## Week 7 — Observability

**Read:**
- OpenTelemetry Java agent docs
- Prometheus / Grafana dashboards for Kafka

**Do:**
- Configure Grafana datasource → Prometheus
- Import Kafka Streams dashboard (Confluent provides one)
- Add a custom Micrometer metric (`Counter` for declined payments)

## Week 8 — Production hardening

**Read:**
- *Kubernetes Patterns* (Ibryam) — Health Probe, Predictable Demands, Stateful Service
- Spring Boot reference: graceful shutdown, container support

**Do:**
- Run `kubectl apply -k infra/k8s/overlays/local` end-to-end
- Test rolling upgrade: change `image.tag` and watch rollout
- Trigger HPA: load-test order-service with `hey -z 60s -c 50`
- Read `Makefile`, understand every target

## Stretch goals

- **GitOps**: install Argo CD, point at `infra/k8s/overlays/prod`
- **CDC outbox**: replace `OutboxRelay` with Debezium Postgres connector
- **Service mesh**: add Istio sidecar injection, replace NetworkPolicy with AuthorizationPolicy
- **Cross-region**: enable MirrorMaker 2 between two Strimzi clusters
- **Cost engineering**: tune `linger.ms`, `batch.size`, `compression.type` and measure throughput delta with JMX exporter
