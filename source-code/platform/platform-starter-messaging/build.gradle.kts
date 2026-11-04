plugins { id("ewallet.java-conventions") }

dependencies {
    api(project(":platform:platform-starter-core"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.kafka:spring-kafka")
    api("org.springframework.boot:spring-boot-starter-json") // cấu hình sẵn ObjectMapper (JavaTimeModule...)

    // Migration của bảng outbox/inbox/shedlock nằm trong starter (db/platform); service trỏ Flyway tới đó.
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
}
