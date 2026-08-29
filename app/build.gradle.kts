import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    id("androidx.room")
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The branch this build came from, or an empty string outside a git checkout.
 *
 * A ValueSource rather than a plain `exec`, so the configuration cache knows the value depends on
 * git state instead of silently freezing whatever it saw first.
 */
abstract class GitBranchSource : ValueSource<String, GitBranchSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val projectDirectory: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String = try {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            workingDir = parameters.projectDirectory.get().asFile
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        output.toString().trim()
    } catch (_: Exception) {
        // No git, or no commit yet. A build must not depend on either.
        ""
    }
}

/**
 * Marks builds made off an integration branch, so a test APK says so in Settings.
 *
 * Two people work on two branches, and `versionCode` is bumped once, by whoever merges — otherwise
 * both would claim the same number for different code. That leaves branch builds sharing a version
 * with `develop`, which is exactly the trap that already cost a round trip once: a screenshot of an
 * APK nobody could identify.
 *
 * The suffix closes that without touching `versionCode`, so installing over an existing build still
 * works and Play still sees one number per release. Releases come off `develop` or `master` and
 * carry no suffix.
 */
val versionSuffix: String = providers.of(GitBranchSource::class) {
    parameters.projectDirectory.set(rootProject.layout.projectDirectory)
}.get().let { branch ->
    if (branch.isEmpty() || branch in setOf("develop", "master", "HEAD")) "" else "+$branch"
}

android {
    namespace = "com.przunk.kratkoza"

    // The newest stable platform installed in .tooling (Android 17). project.md pins targetSdk to
    // API 36 while leaving compileSdk free to track the newest stable SDK.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.przunk.kratkoza"
        minSdk = 29
        targetSdk = 36
        // Bumped once per merge into `develop`, by whoever performs the merge — never on a task
        // branch, or two branches would claim the same number for different code. Branch builds are
        // told apart by `versionSuffix` above instead.
        versionCode = 46
        versionName = "0.29.0$versionSuffix"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // The whole UI is translated; without this the app also ships every language bundled by
        // AndroidX, which dilutes the per-locale resources for no benefit.
        localeFilters += listOf("en", "pl")

        // Lets AGP write the manifest's localeConfig from the filters above, so Android 13+ offers
        // Kratkoza in the system's per-app language picker. Hand-writing that resource works too,
        // but then the list of languages lives in two places and drifts.
        generateLocaleConfig = true
    }

    signingConfigs {
        create("release") {
            // Read from ~/.gradle/gradle.properties, never from this repository. A key committed
            // beside the code has to be treated as public from the day it is written.
            val storePath = providers.gradleProperty("PRZUNK_UPLOAD_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = providers.gradleProperty("PRZUNK_UPLOAD_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("PRZUNK_UPLOAD_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("PRZUNK_UPLOAD_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // The upload key when it is configured, the local debug key otherwise. An unsigned APK
            // cannot be installed at all, so falling back keeps the sideload workflow alive.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
            // On, and on before the first upload rather than after it. R8 removes code it cannot
            // see being used, and what it removes wrongly shows up on a device, not in the tests —
            // which run against unminified code. The rules it needs are in proguard-rules.pro, each
            // with the reason it is there.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    bundle {
        language {
            // The in-app language switcher would otherwise be a lie in a Play-installed build:
            // Play splits an App Bundle by language and ships only the device's own, so picking
            // Polish on an English phone would find no Polish resources to show. Two languages of
            // strings cost a few kilobytes; keeping them both in every install is the only way the
            // setting can work.
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        // Only for BuildConfig.VERSION_NAME / VERSION_CODE, which the settings screen shows.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        lintConfig = file("lint.xml")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.room:room-runtime:2.8.4")

    // The one unavoidable external dependency: Play Games Services has no protocol to speak to
    // directly, so ranking means Google's SDK or no ranking. project.md section 27 asks for the
    // ranking, and technical_requirements.md section 8 asks for restraint everywhere else.
    implementation("com.google.android.gms:play-services-games-v2:20.1.2")

    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Room's SQL is the one part of the data layer a hand-written fake cannot stand in for: a fake
    // reimplements the query instead of running it, which is how a broken reset once passed its
    // tests. These four let the real database run on the JVM.
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    // Compose UI tests run on the JVM through Robolectric, so the one gap nothing else covers —
    // that a screen actually shows what its state says — is closed without a device in the room.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
