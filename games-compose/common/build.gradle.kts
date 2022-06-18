import org.jetbrains.compose.compose

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.1.0"
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
                implementation(project(":games-mpp"))
            }
        }
        val desktopTest by getting
//        val jsMain by getting
//        val jsTest by getting
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
