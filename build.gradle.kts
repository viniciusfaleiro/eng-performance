import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.SpotBugsExtension
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.springframework.boot") version "3.4.1" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
    id("com.github.spotbugs") version "6.0.26" apply false
}

// Versions for libraries not governed by the Spring Boot BOM.
val findsecbugsVersion = "1.13.0"
val spotbugsAnnotationsVersion = "4.8.6"
val googleJavaFormatVersion = "1.25.2"
val checkstyleToolVersion = "10.21.0"

// Modules that carry business rules and therefore must meet a coverage gate.
val coverageGatedModules = setOf("domain", "application")

// OSS Index (Sonatype) credentials for the dependency-vulnerability gate.
// Anonymous access now returns 401, so the gate stays dormant until a free
// account's email + token are provided via -Possindex... , gradle.properties or env.
val ossIndexUser: String? =
    (findProperty("ossindexUsername") as String?) ?: System.getenv("OSSINDEX_USERNAME")
val ossIndexToken: String? =
    (findProperty("ossindexToken") as String?) ?: System.getenv("OSSINDEX_TOKEN")
val ossIndexEnabled = !ossIndexUser.isNullOrBlank() && !ossIndexToken.isNullOrBlank()

allprojects {
    group = "com.engperf"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Emit method parameter names so Spring MVC (@PathVariable/@RequestParam) and Jackson
    // record binding work without explicit names — the same default the Spring Boot plugin sets.
    tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    // ---- Formatting: google-java-format is the single source of truth ----
    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat(googleJavaFormatVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // ---- Lint / style rules ----
    extensions.configure<CheckstyleExtension> {
        toolVersion = checkstyleToolVersion
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        configDirectory.set(rootProject.file("config/checkstyle"))
        maxWarnings = 0
        isIgnoreFailures = false
    }

    // ---- Security-focused static analysis (SpotBugs + FindSecBugs) ----
    extensions.configure<SpotBugsExtension> {
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
        ignoreFailures.set(false)
    }

    dependencies {
        "spotbugsPlugins"("com.h3xstream.findsecbugs:findsecbugs-plugin:$findsecbugsVersion")
        "compileOnly"("com.github.spotbugs:spotbugs-annotations:$spotbugsAnnotationsVersion")
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    // Enforce a coverage floor only where real logic lives.
    if (name in coverageGatedModules) {
        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        minimum = "0.70".toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        minimum = "0.60".toBigDecimal()
                    }
                }
            }
        }
        tasks.named("check") {
            dependsOn("jacocoTestCoverageVerification")
        }
    }
}
