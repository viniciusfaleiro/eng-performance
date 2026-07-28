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
    "adapter-out-ado",
    "bootstrap",
    "architecture-tests",
)
