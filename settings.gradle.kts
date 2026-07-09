rootProject.name = "eng-performance"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "domain",
    "application",
    "adapter-in-web",
    "adapter-out-persistence",
    "bootstrap",
    "architecture-tests",
)
