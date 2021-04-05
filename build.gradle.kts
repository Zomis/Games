plugins {
    kotlin("multiplatform") version "1.4.32"
}

group = "net.zomis"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io") // KLogging
}

kotlin {
    jvm {
        withJava()
    }
    js {
        browser {

        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("games-core/src/main/kotlin")
            resources.srcDir("games-core/src/main/resources")
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("games-server/src/main/kotlin")
            resources.srcDir("games-server/src/main/resources")
        }
        val jvmTest by getting {
            kotlin.srcDir("games-server/src/test/kotlin")
            resources.srcDir("games-server/src/test/resources")
        }
        val jsMain by getting {
            kotlin.srcDir("games-js/src/main/kotlin")
        }
    }

}
