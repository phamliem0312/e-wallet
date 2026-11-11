// Spring Cloud Gateway (WebFlux): không dùng platform-starter-web vì starter đó là servlet.
plugins { id("ewallet.service-conventions") }

dependencies {
    implementation(project(":platform:platform-starter-core"))
    implementation(project(":platform:platform-starter-observability"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    testImplementation("org.testcontainers:junit-jupiter")
}
