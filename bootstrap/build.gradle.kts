// Bootstrap: the only executable module. Wires ports to adapters and serves the app.
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapter-in-web"))
    implementation(project(":adapter-out-persistence"))

    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
