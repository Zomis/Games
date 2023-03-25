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
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("common/src/main/kotlin")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
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
                implementation("com.github.lewik.klog:klog-jvm:2.0.5")
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("src/jvmTest/kotlin")
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")
            }
        }
        val jsMain by getting {
            kotlin.srcDir("js/src/main/kotlin")
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