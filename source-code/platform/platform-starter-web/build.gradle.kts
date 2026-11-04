plugins { id("ewallet.java-conventions") }

dependencies {
    api(project(":platform:platform-starter-core"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
