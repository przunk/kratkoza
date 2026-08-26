plugins {
    id("androidx.room")
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
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
        // Bumped on every approved change, so the version shown in Settings identifies exactly
        // which build is on a phone. A screenshot from the wrong APK has already cost us a round
        // trip once; the number is what makes that impossible to repeat.
        versionCode = 13
        versionName = "0.5.2"

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

    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
