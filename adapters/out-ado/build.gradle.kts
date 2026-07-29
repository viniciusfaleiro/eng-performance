// Outbound adapter: the real Azure DevOps source. Only this module talks HTTP to ADO and does the
// interactive Entra device-code auth (no PAT). Maps ADO activity to the domain's RawEvent, behind
// the application's ports. Uses the JDK HttpClient + Jackson — no PAT, no stored secret.
dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("com.fasterxml.jackson.core:jackson-databind")
    // SLF4J facade so the sync path emits diagnostable logs (binding provided by the app at runtime).
    implementation("org.slf4j:slf4j-api")
    // Spring only for @Component discovery (like the persistence adapter) — never leaks inward.
    implementation("org.springframework:spring-context")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
