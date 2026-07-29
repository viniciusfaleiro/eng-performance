// Application: use cases + ports. Depends only on the domain (no Spring, no adapters).
dependencies {
    implementation(project(":domain"))
    // SLF4J facade only — a logging API, not a framework (ArchUnit bans Spring here, not logging).
    implementation("org.slf4j:slf4j-api")
}
