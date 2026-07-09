// Outbound adapter: implements the application's outbound ports.
// In-memory for now; a JPA/DB implementation slots in here later without touching inner layers.
dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
