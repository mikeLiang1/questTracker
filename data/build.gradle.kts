plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mikeliang.questtracker.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("test") {
            // Exported Room schemas double as MigrationTestHelper's test assets.
            assets.srcDirs("$projectDir/schemas")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // In-memory Room on Android still needs a Context, so local (JVM) DAO/migration
    // tests run under Robolectric rather than as instrumented tests. Robolectric's
    // runner is JUnit4; the vintage engine lets it execute under the same
    // useJUnitPlatform() task as the JUnit5 suite above.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.sqlite.bundled.jvm)
    testRuntimeOnly(libs.junit.vintage.engine)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
