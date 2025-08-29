# Health Check Service

A lightweight **open source** Quarkus-based service that periodically checks the availability of dependent services and exposes simple status metrics for observability platforms (e.g., Prometheus/Grafana).

The service reads a list of target endpoints from a JSON file, performs HTTP GET checks every 10 seconds, and updates Micrometer gauges to indicate UP (1) or DOWN (0) for each target. It’s designed to run locally, in Docker, or on Kubernetes with Kustomize overlays.

## Features
- Periodic health checks (every 10s) of configured services (external and optional in-cluster/local URLs).
- Exposes Micrometer metrics with Prometheus registry.
- Quarkus native health endpoint for liveness/readiness.
- Simple configuration via JSON file (ConfigMap in K8s).
- Container-first build with multi-stage Dockerfile.

## How it works
- On startup, the service loads servers.json (path configurable via `servers.file`).
- For each server item, it optionally tracks:
  - default URL ("url"): checked and exported as `service.status.default{service="<name>"}`.
  - local/in-cluster URL ("localUrl"): checked and exported as `service.status.local{service="<name>"}`.
  - combined metric `service.status.combined{service="<name>"}` = 1 only if both default and local are UP.
- Every 10 seconds the service performs HTTP GET checks (5s timeout; 3s connect timeout) and logs per-target status.

## Endpoints
- Health: `GET /q/health` (provided by Quarkus SmallRye Health)
- Prometheus metrics: `GET /q/metrics`

## Configuration
Configuration is primarily via `application.properties` and a JSON file with the target servers list.

`src/main/resources/application.properties` (relevant entries):
- `servers.file=/config/servers.json` (production default)
- `%dev.servers.file=${PWD}/servers.json` (when running with `mvn quarkus:dev`)
- Micrometer binder defaults are tuned to only what’s needed.

### servers.json format
An array of objects; each object supports:
- `name` (string): Logical name of the service (used as the `service` tag in metrics; lowercased in metrics).
- `url` (string): Public/default URL to check (optional but recommended).
- `localUrl` (string): In-cluster/internal URL to check (optional). When present, combined metric is also emitted.

Example:
```json
[
  {
    "name": "users-service-v1",
    "url": "https://example.org/users-service/v1/quarkus/health",
    "localUrl": "http://users-service-v1.apps.svc.cluster.local:8080/users-service/v1/quarkus/health"
  },
  {
    "name": "visits",
    "url": "https://example.org/visits-service/quarkus/health"
  }
]
```

## Build and Run

### Prerequisites
- Java 21 (JDK)
- Maven 3.x

### Run in dev mode
Dev mode auto-reloads on code changes and uses `servers.json` from the project root (thanks to `%dev` config):

```bash
mvn quarkus:dev
```

Then access:
- http://localhost:8080/q/health
- http://localhost:8080/q/metrics

Ensure there is a `servers.json` file at the project root (you can copy or adapt from `gitops/k8s/overlays/dev/servers.json`).

### Build a production jar
```bash
mvn clean package -DskipTests
```
Artifacts will be in `target/quarkus-app/`. You can run the app with:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Optionally point to a specific servers file:
```bash
java -Dservers.file=/path/to/servers.json -jar target/quarkus-app/quarkus-run.jar
```

### Native build (optional)
See Quarkus native profile in `pom.xml`:
```bash
mvn clean package -DskipTests -Dnative
```

## Docker
Multi-stage Dockerfile builds and runs the app as a non-root user.

Build image (tests ON by default; set `--build-arg SKIP_TESTS=true` to skip):
```bash
docker build -t health-check-service .
# or skipping tests
docker build --build-arg SKIP_TESTS=true -t health-check-service .
```

Run with a local servers.json mounted at `/config/servers.json` (matches default `servers.file`):
```bash
docker run --rm -p 8080:8080 \
  -v $(pwd)/servers.json:/config/servers.json:ro \
  health-check-service
```

Then visit:
- http://localhost:8080/q/health
- http://localhost:8080/q/metrics

## Kubernetes (Kustomize)
Kustomize overlays are provided under `gitops/k8s/overlays/dev` and `gitops/k8s/overlays/prod`.

- Base Deployment: `gitops/k8s/base/deployment.yaml` (exposes port 8080)
- Overlays:
  - Mount a ConfigMap named `health-check-service-servers` at `/config/servers.json`.
  - Patch the container image per environment.

To apply an overlay (example for dev):
```bash
kubectl apply -k gitops/k8s/overlays/dev
```
Make sure to set the image in the patch `patch-image.yaml` (place-holder in dev overlay). The ConfigMap is generated from `servers.json` within the overlay directory.

## Metrics reference
The service exposes the following custom gauges (value: 1 for UP, 0 for DOWN):
- `service.status.default{service="<name>"}`
- `service.status.local{service="<name>"}` (only if localUrl provided)
- `service.status.combined{service="<name>"}` (1 only when both default and local are UP)

Examples Prometheus queries:
- `service_status_default{service="users-service-v1"}`
- `sum by (service) (service_status_combined)`

Note: In Prometheus, metric names will use underscores; Quarkus/Micrometer converts dots to underscores (e.g., `service.status.default` -> `service_status_default`).

## Logging
During checks, informational logs indicate whether each target is UP or DOWN with HTTP status or error message.

## Project details
- Java: 21
- Build: Maven
- Framework: Quarkus 3.25.4
- Key extensions: SmallRye Health, Micrometer (Prometheus), Scheduler, REST