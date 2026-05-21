{{/*
Reusable deployment template — produces a Deployment OR StatefulSet, Service, HPA.
Called like: include "eventflow.service" (dict "name" "order" "svc" .Values.services.order "ctx" .)
*/}}
{{- define "eventflow.service" -}}
{{- $name := .name -}}
{{- $svc := .svc -}}
{{- $ctx := .ctx -}}
{{- if $svc.enabled }}
apiVersion: apps/v1
kind: {{ if $svc.stateful }}StatefulSet{{ else }}Deployment{{ end }}
metadata:
  name: {{ $name }}-service
  labels:
    app: {{ $name }}-service
    app.kubernetes.io/part-of: eventflow
spec:
  {{- if $svc.stateful }}
  serviceName: {{ $name }}-service
  {{- end }}
  replicas: {{ $svc.replicas }}
  selector:
    matchLabels:
      app: {{ $name }}-service
  template:
    metadata:
      labels:
        app: {{ $name }}-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "{{ $svc.port }}"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: app
          image: {{ $svc.image }}:{{ $svc.tag }}
          imagePullPolicy: {{ $ctx.Values.global.imagePullPolicy }}
          ports:
            - containerPort: {{ $svc.port }}
              name: http
          env:
            - name: KAFKA_BOOTSTRAP
              value: {{ $ctx.Values.global.kafkaBootstrap | quote }}
            - name: SCHEMA_REGISTRY_URL
              value: {{ $ctx.Values.global.schemaRegistryUrl | quote }}
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
            {{- if eq $name "order" }}
            - name: DB_URL
              value: jdbc:postgresql://postgres.{{ $ctx.Release.Namespace }}.svc:5432/{{ $ctx.Values.postgres.database }}
            - name: DB_USER
              valueFrom: { secretKeyRef: { name: postgres-creds, key: username } }
            - name: DB_PASS
              valueFrom: { secretKeyRef: { name: postgres-creds, key: password } }
            {{- end }}
          resources:
            {{- toYaml $svc.resources | nindent 12 }}
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            initialDelaySeconds: 60
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            initialDelaySeconds: 20
          {{- if $svc.stateful }}
          volumeMounts:
            - name: state
              mountPath: /tmp/kafka-streams
          {{- end }}
      terminationGracePeriodSeconds: 60
  {{- if $svc.stateful }}
  volumeClaimTemplates:
    - metadata: { name: state }
      spec:
        accessModes: [ReadWriteOnce]
        resources: { requests: { storage: {{ $svc.storageSize }} } }
  {{- end }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}-service
spec:
  {{- if $svc.stateful }}
  clusterIP: None
  {{- end }}
  selector:
    app: {{ $name }}-service
  ports:
    - port: 80
      targetPort: {{ $svc.port }}
      name: http
{{- end }}
{{- end -}}
