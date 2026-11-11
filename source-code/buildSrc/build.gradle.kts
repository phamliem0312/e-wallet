plugins { `kotlin-dsl` }

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.7")
}
