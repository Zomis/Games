import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version = "2.2.4"
val decomposeVersion = "2.0.0-compose-experimental-alpha-02"
val steamWorksVersion: String by project

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.4.0"
    // id("com.android.library")
}

group = "me.simon"
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
                implementation("io.ktor:ktor-client-websockets:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-websockets:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-jackson:$ktor_version")
                implementation("com.arkivanov.decompose:decompose:$decomposeVersion")
                implementation("com.arkivanov.decompose:extensions-compose-jetbrains:$decomposeVersion")
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
                implementation("com.code-disaster.steamworks4j:steamworks4j:$steamWorksVersion")
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

tasks.create<Copy>("steam") {
    dependsOn("packageUberJarForCurrentOS")
    from("build/compose/jars/")
    into("e:/SteamSDK/steam-package/jar/")
    include("*.jar")
    rename { "desktop.jar" }
}

tasks.create<Exec>("steamTest") {
    description = "Build JAR and build steam depot and upload to steam"
//    doFirst {
        workingDir = File("e:/SteamSDK")
        commandLine = listOf(
            "cmd", "/c", "build-test.bat",
        )
//    }
}
