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

// Modules are grouped on disk by hexagon layer (core/adapters/app/test) for readability, while the
// logical project names stay flat (:domain, :adapter-in-web, …) so module dependencies are unchanged.
project(":domain").projectDir = file("core/domain")
project(":application").projectDir = file("core/application")
project(":adapter-in-web").projectDir = file("adapters/in-web")
project(":adapter-out-persistence").projectDir = file("adapters/out-persistence")
project(":adapter-out-ado").projectDir = file("adapters/out-ado")
project(":bootstrap").projectDir = file("app/bootstrap")
project(":architecture-tests").projectDir = file("test/architecture-tests")
