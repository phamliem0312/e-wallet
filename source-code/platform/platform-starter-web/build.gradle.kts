plugins { id("ewallet.java-conventions") }

dependencies {
    api(project(":platform:platform-starter-core"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server") // bật bảo mật JWT khi service có dependency này

    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
