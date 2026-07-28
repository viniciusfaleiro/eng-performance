// Architecture tests: ArchUnit rules that fail the build when hexagonal boundaries are violated.
dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":application"))
    testImplementation(project(":adapter-in-web"))
    testImplementation(project(":adapter-out-persistence"))
    testImplementation(project(":adapter-out-ado"))
    testImplementation(project(":bootstrap"))

    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
