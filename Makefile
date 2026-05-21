# ----------------------------------------------------------------------------
# Eventflow — Makefile shortcuts for common dev workflows
# ----------------------------------------------------------------------------

.PHONY: help build test infra-up infra-down clean kind-up kind-down k8s-deploy \
        helm-deploy helm-uninstall image-build smoke-test logs-order logs-all

help:
	@echo "Eventflow targets:"
	@echo "  build           Maven build (offline-friendly)"
	@echo "  test            Run all unit + integration tests"
	@echo "  infra-up        Start local Kafka + Postgres + Schema Registry + Prom/Grafana"
	@echo "  infra-down      Stop local infra"
	@echo "  image-build     Build Spring Boot OCI images via buildpacks"
	@echo "  kind-up         Create kind cluster"
	@echo "  kind-down       Delete kind cluster"
	@echo "  k8s-deploy      Apply kustomize overlay (local)"
	@echo "  helm-deploy     Helm install/upgrade"
	@echo "  smoke-test      curl an order through the API"
	@echo "  logs-all        Tail logs of all services"

# --- Build & test ----------------------------------------------------------

build:
	./mvnw clean install -DskipTests

test:
	./mvnw test

# --- Local infra (docker-compose) ------------------------------------------

infra-up:
	cd infra/docker && docker compose up -d
	@echo "Kafka UI:    http://localhost:8090"
	@echo "Schema Reg:  http://localhost:8081"
	@echo "Prometheus:  http://localhost:9090"
	@echo "Grafana:     http://localhost:3001 (admin/admin)"

infra-down:
	cd infra/docker && docker compose down -v

# --- Container images ------------------------------------------------------

image-build:
	./mvnw -pl services/order-service        spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=eventflow/order-service:0.1.0
	./mvnw -pl services/payment-service      spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=eventflow/payment-service:0.1.0
	./mvnw -pl services/inventory-service    spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=eventflow/inventory-service:0.1.0
	./mvnw -pl services/notification-service spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=eventflow/notification-service:0.1.0

# --- Kubernetes (kind) -----------------------------------------------------

kind-up:
	kind create cluster --config infra/k8s/kind-config.yaml
	# Strimzi operator
	kubectl create namespace eventflow || true
	kubectl create -f 'https://strimzi.io/install/latest?namespace=eventflow' -n eventflow

kind-down:
	kind delete cluster --name eventflow

k8s-load-images:
	kind load docker-image eventflow/order-service:0.1.0 --name eventflow
	kind load docker-image eventflow/payment-service:0.1.0 --name eventflow
	kind load docker-image eventflow/inventory-service:0.1.0 --name eventflow
	kind load docker-image eventflow/notification-service:0.1.0 --name eventflow

k8s-deploy:
	kubectl apply -k infra/k8s/overlays/local

# --- Helm -----------------------------------------------------------------

helm-deploy:
	helm upgrade --install eventflow infra/helm/eventflow \
		--namespace eventflow --create-namespace

helm-uninstall:
	helm uninstall eventflow -n eventflow

# --- Smoke test -----------------------------------------------------------

smoke-test:
	curl -sS -X POST http://localhost:8080/api/orders \
		-H 'Content-Type: application/json' \
		-d '{"customerId":"c1","productId":"p1","quantity":2,"amount":99.50,"currency":"USD"}' | jq .

logs-order:
	kubectl logs -n eventflow -l app=order-service -f --tail=100

logs-all:
	stern -n eventflow '.*-service'

clean:
	./mvnw clean
