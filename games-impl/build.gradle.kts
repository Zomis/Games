plugins {
    kotlin("multiplatform")
}

version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }
    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.coroutines.core)
                api(project(":games-dsl"))
                api(project(":games-analyze"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.klog.jvm)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.jupiter.api)
                implementation(libs.jupiter.params)
                runtimeOnly(libs.jupiter.engine)
                implementation(libs.coroutines.test)
            }
        }
        val jsMain by getting {
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