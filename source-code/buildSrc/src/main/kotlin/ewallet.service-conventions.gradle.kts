// Service chạy được (bootJar). Dùng: plugins { id("ewallet.service-conventions") }
plugins {
    id("ewallet.java-conventions")
    id("org.springframework.boot")
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
