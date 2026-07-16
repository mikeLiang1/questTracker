plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

/**
 * :core must stay pure Kotlin so a future KMP migration only has to swap platform
 * adapters, not rewrite domain logic. This scans source files directly rather than
 * relying on compile-time failure, since an android/androidx dependency added to this
 * module's classpath would otherwise compile just fine.
 */
val verifyNoAndroidImports by tasks.registering {
    group = "verification"
    description = "Fails if any :core source file imports android.* or androidx.*"

    val sourceDir = layout.projectDirectory.dir("src")
    val rootDir = layout.projectDirectory.asFile
    inputs.dir(sourceDir)

    doLast {
        val offendingImportRegex = Regex("""^\s*import\s+(android|androidx)\..*""")
        val violations = sourceDir.asFileTree
            .matching { include("**/*.kt") }
            .files
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> offendingImportRegex.matches(line) }
                    .map { (index, line) -> "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}" }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                ":core must contain zero android.*/androidx.* imports. Violations:\n" +
                    violations.joinToString("\n")
            )
        }
    }
}

tasks.check {
    dependsOn(verifyNoAndroidImports)
}
