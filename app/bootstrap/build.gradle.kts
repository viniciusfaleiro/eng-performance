// Bootstrap: the only executable module. Wires ports to adapters and serves the app.
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapter-in-web"))
    implementation(project(":adapter-out-persistence"))
    implementation(project(":adapter-out-ado"))
    implementation(project(":adapter-out-email"))

    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// The running build must be identifiable from the UI: `/api/version` reads this file. The commit
// is what actually distinguishes two deploys — the artifact version only moves on a release.
springBoot {
    buildInfo {
        properties {
            additional.set(mapOf("commit" to gitCommit()))
        }
    }
}

fun gitCommit(): String =
    try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && out.isNotEmpty()) out else "unknown"
    } catch (e: Exception) {
        // No git available (a source tarball, a slim CI image): the version still reports.
        "unknown"
    }
