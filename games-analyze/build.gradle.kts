plugins {
    kotlin("multiplatform")
}

version = "1.0-SNAPSHOT"

val jupiterVersion = "5.7.1"

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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
                implementation(project(":games-dsl"))
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
                implementation("com.github.lewik.klog:klog-jvm:2.0.5")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("com.github.lewik.klog:klog-js:2.0.5")
            }
        }
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}
