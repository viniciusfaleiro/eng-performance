// Outbound adapter: sends transactional email. SMTP via spring-boot-starter-mail when configured,
// with a log fallback so the flows that depend on email stay exercisable without a mail server.
dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-context")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
