// Chỉ gom dependency: actuator + metric Prometheus + tracing W3C (traceId vào MDC, traceparent qua HTTP).
// Chưa có: log JSON mặc định, traceparent trong event Kafka, exporter OTLP (chờ chuẩn hạ tầng, OQ-027).
plugins { id("ewallet.java-conventions") }

dependencies {
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
