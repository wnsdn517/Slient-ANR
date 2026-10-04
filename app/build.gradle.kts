plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.wnsdn517.silentanr"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.wnsdn517.silentanr"
        minSdk = 29
        targetSdk = 35
        // CI passes these from the release workflow; local builds fall back to 1 / 1.0.0.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0.0"
        // Only English strings ship; drops the translations bundled with AndroidX/Compose.
        resourceConfigurations += "en"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so CI artifacts are installable; replace for distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // META-INF/xposed/** must stay: LSPosed reads the fixed scope from it.
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            "/META-INF/androidx/**",
            "/kotlin/**",
            "/kotlin-tooling-metadata.json",
            "DebugProbesKt.bin",
        )
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

base {
    // APKs come out as SilentANR-release.apk / SilentANR-debug.apk.
    archivesName.set("SilentANR")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    compileOnly(project(":xposed-stub"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
