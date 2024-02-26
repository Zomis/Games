import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.4.0"
    // id("com.android.library")
}

group = "net.zomis"
version = "1.0"

kotlin {
    // android()
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
//    js(LEGACY) {
//        binaries.executable()
//        browser {
//            commonWebpackConfig {
//                cssSupport.enabled = true
//            }
//        }
//    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                implementation(project(":games-dsl"))
                implementation(project(":games-impl"))
                implementation(project(":games-mpp"))
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.jackson)
                implementation(libs.decompose)
                implementation(libs.decompose.jetbrains)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        /*
        val androidMain by getting {
            dependencies {
                api("androidx.appcompat:appcompat:1.4.1")
                api("androidx.core:core-ktx:1.7.0")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
        */
        val desktopMain by getting {
            dependencies {
                api(compose.preview)
                implementation(compose.desktop.currentOs)
                implementation(project(":games-mpp"))
                implementation(libs.steamworks4j)
            }
        }
        val desktopTest by getting
//        val jsMain by getting
//        val jsTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "net.zomis.games.compose.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "jvm"
            packageVersion = "1.0.0"
        }
    }
}
/*
android {
    compileSdkVersion(31)
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdkVersion(24)
        targetSdkVersion(31)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
*/

tasks.withType<KotlinCompile> {
    kotlinOptions {
        if (project.findProperty("composeReports") == "true") {
            freeCompilerArgs += listOf("-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                        project.buildDir.absolutePath + "/compose_metrics"
            )
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                        project.buildDir.absolutePath + "/compose_metrics"
            )
        }
    }
}
