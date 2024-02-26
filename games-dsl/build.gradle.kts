import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
}

version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs += "-Xcontext-receivers"
            }
        }
    }
    js(IR) {
        browser()
    }
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("common/src/main/kotlin")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.coroutines.core)
                implementation(libs.klog.common)
            }
        }
        val commonTest by getting {
            kotlin.srcDir("common/src/test/kotlin")
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("jvm/src/main/kotlin")
            dependencies {
                implementation(libs.klog.jvm)
                implementation(kotlin("reflect"))
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("src/jvmTest/kotlin")
            dependencies {
                implementation(libs.jupiter.api)
                implementation(libs.jupiter.params)
                runtimeOnly(libs.jupiter.engine)
                implementation(libs.coroutines.test)
            }
        }
        val jsMain by getting {
            kotlin.srcDir("js/src/main/kotlin")
            dependencies {
                implementation(libs.klog.js)
            }
        }
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}