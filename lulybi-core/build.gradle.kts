plugins {
    `java-library`
    `maven-publish`
    // We don't need a version here because it's defined in the root build.gradle.kts
    id("com.diffplug.spotless")
}

dependencies {
    api(platform(project(":lulybi-bom")))

    // Processor dependencies
    implementation(libs.javapoet)
    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Ensure the JAR includes the necessary metadata for the Annotation Processor
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Lulybi Core",
            "Implementation-Version" to project.version
        )
    }
}

java {
    // Required for the Annotation Processor to read parameter names via reflection-free AOT
    withSourcesJar()
    withJavadocJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Enable parameter metadata for AOT processing
    options.compilerArgs.add("-parameters")
}