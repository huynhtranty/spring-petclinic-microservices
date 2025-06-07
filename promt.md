### 1. cài đặt
``` bash
docker compose up
```
** Thêm image loki và promtail vào docker compose**
``` yml
loki:
  image: grafana/loki:2.9.2
  container_name: loki
  user: "0:0"
  ports:
    - "3100:3100"
  volumes:
    - ./loki/loki-config.yaml:/etc/loki/local-config.yaml:ro  # Read-only mount
    - ./data/loki:/loki  # Bind mount cho data
  command: -config.file=/etc/loki/local-config.yaml

promtail:
  image: grafana/promtail:2.9.2
  container_name: promtail
  volumes:
  - /var/lib/docker/containers:/var/lib/docker/containers:ro
  - ./promtail/promtail-config.yaml:/etc/promtail/config.yml

```
 **loki-config.yaml**
``` yaml
auth_enabled: false

server:
  http_listen_port: 3100

ingester:
  lifecycler:
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1
  chunk_idle_period: 5m
  max_chunk_age: 1h

schema_config:
  configs:
  - from: 2022-01-01
    store: boltdb-shipper
    object_store: filesystem
    schema: v11
    index:
      prefix: index_
      period: 24h

storage_config:
  boltdb_shipper:
    active_index_directory: /tmp/loki/index
    cache_location: /tmp/loki/boltdb-cache
    shared_store: filesystem
  filesystem:
    directory: /tmp/loki/chunks

limits_config:
  enforce_metric_name: false

chunk_store_config:
  max_look_back_period: 0s

table_manager:
  retention_deletes_enabled: true
  retention_period: 24h

```


### 2. gửi trace lên Zipkin 

**Cấu hình trong application.yml mỗi service**
``` yaml
spring:
  zipkin:
    base-url: http://tracing-server:9411
  sleuth:
    sampler:
      probability: 1.0
```

**Cấu hình pom.xml**
``` xml
Đã có sẵn
```

### 3. Gửi metrics lên Prometheus
``` xml
Đã có sẵn

```
**application.yml**
``` yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```
**check**: 
``` bash 
http://<service>:port/actuator/prometheus 
```


### 4: Gửi log lên Grafana Loki
**File promtail-config.yaml**
``` yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://tracing-server:3100/loki/api/v1/push

scrape_configs:
  - job_name: system
    static_configs:
      - targets:
          - localhost
        labels:
          job: varlogs
          __path__: /var/log/*.log

```

**Thêm loki vào Grafana `datasources/all.yml`**
``` yaml
- name: Loki
  type: loki
  access: proxy
  url: http://tracing-server:3100
  is_default: false
  editable: true
```

### 5. Biểu đồ request 
Check trong Grafana UI: dashboard hiển thị biểu đồ sau:

Tổng số request

2xx response

5xx error

Prometheus query:

``` promql
rate(http_server_requests_seconds_count{status=~"2.."}[1m])
rate(http_server_requests_seconds_count{status=~"5.."}[1m])

```
**Thêm file `alert-rules.yaml`** trong `docker\prometheus` folder  

### 6. Hiển thị log
Kiểm tra: vào Grafana > Explore > chọn Loki > xem log theo job/service.

### 7. Alert nếu lỗi > 10 trong 30s
1. Tạo rules
``` yaml
groups:
- name: error_alert
  rules:
  - alert: HighErrorRate
    expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[30s])) > 10
    for: 30s
    labels:
      severity: critical
    annotations:
      summary: "High error rate"

```
2. Thêm vào prometheus.yml
``` yml
rule_files:
  - "alert.rules.yml"
```

3. Thêm vào Dockerfile Prometheus
``` dockerfile
ADD prometheus.yml /etc/prometheus/
ADD alert.rules.yml /etc/prometheus/

```

### 8. Liên kết trace - metrics - log
Thêm vào từng `src/main/resources/` của từng service 

**logback-spring.xml**
``` xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <!-- Định nghĩa biến -->
  <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId:-}] [%X{spanId:-}] [%thread] %-5level %logger{36} - %msg%n" />
  <property name="LOG_FILE" value="logs/${spring.application.name:-application}.log" />

  <!-- Console log -->
  <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>${LOG_PATTERN}</pattern>
    </encoder>
  </appender>

  <!-- Ghi log ra file (tùy chọn) -->
  <appender name="File" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>logs/${spring.application.name:-application}.%d{yyyy-MM-dd}.log</fileNamePattern>
      <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>${LOG_PATTERN}</pattern>
    </encoder>
  </appender>

  <!-- Log mức INFO trở lên -->
  <root level="INFO">
    <appender-ref ref="Console" />
    <!-- Nếu bạn muốn ghi ra file -->
    <!-- <appender-ref ref="File" /> -->
  </root>

</configuration>

``` 

### Script lỗi kích hoạt alert
``` bash
#!/bin/bash

for i in {1..50}
do
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/fail
   sleep 0.5
done

```

### Nâng cấp phiên bản grafana
10.2.3 trong file Dockerfile của folder `docker\grafana`