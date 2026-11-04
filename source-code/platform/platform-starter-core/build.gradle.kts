plugins { id("ewallet.java-conventions") }

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}
