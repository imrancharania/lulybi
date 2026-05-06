plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    // 1. Align versions using our Workspace BOM
    implementation(platform(project(":lulybi-bom")))

    // 2. Core Abstractions (The Hub) including the Processor logic
    api(project(":lulybi-core"))

    // 3. Annotation Processing (The AOT Engine)
    // Now provided by lulybi-core
    annotationProcessor(project(":lulybi-core"))

    // 4. Service-Specific Dependencies (AWS SDK)
    // Versions for these should be managed in the [libraries] section of your TOML
    implementation(libs.aws.athena)

    // 5. Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.generatedSourceOutputDirectory.set(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main"))
}