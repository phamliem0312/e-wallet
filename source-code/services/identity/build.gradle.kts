plugins { id("ewallet.service-conventions") }

dependencies {
    implementation(project(":platform:platform-starter-web"))
    implementation(project(":platform:platform-starter-messaging"))
    implementation(project(":platform:platform-starter-observability"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.bouncycastle:bcprov-jdk18on") // Argon2PasswordEncoder

    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
}
