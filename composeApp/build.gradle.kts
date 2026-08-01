import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }

        // Intermediate source set for JVM code shared by Android and Desktop
        val commonJvmMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(commonJvmMain)
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("androidx.documentfile:documentfile:1.0.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
            }
        }

        val desktopMain by getting {
            dependsOn(commonJvmMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            }
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "app.linkshare"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.linkshare"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "LinkShareRelease2026"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "linkshare"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "LinkShareRelease2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val keystoreFile = rootProject.file("keystore/release.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "app.linkshare.platform.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LinkShare"
            packageVersion = "1.0.0"
            description = "High-Performance LAN & Wi-Fi Direct P2P File Sharing"
            vendor = "LinkShare"

            macOS {
                bundleID = "app.linkshare"
            }

            windows {
                menuGroup = "LinkShare"
                upgradeUuid = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
            }

            linux {
                debMaintainer = "linkshare@localhost"
            }
        }
    }
}
