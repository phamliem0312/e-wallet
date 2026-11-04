// Cấu hình dùng chung cho mọi module Java. Phiên bản thư viện do BOM của Spring Boot quản lý ở đây;
// module/service không tự khai báo version.
plugins {
    `java-library`
    id("io.spring.dependency-management")
}

group = "com.ewallet"

repositories { mavenCentral() }

// Docker Engine 29 từ chối API version cũ của Testcontainers 1.21.3 (bản Spring Boot 3.5.7 quản lý).
// Bỏ dòng này khi Spring Boot đã quản lý Testcontainers >= 1.21.4.
extra["testcontainers.version"] = "1.21.4"

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.7") }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.test { useJUnitPlatform() }

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
