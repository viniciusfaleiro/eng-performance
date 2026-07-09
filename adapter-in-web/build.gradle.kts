// Inbound adapter: HTTP + server-rendered UI (Thymeleaf + Material Design 3 web components).
dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
