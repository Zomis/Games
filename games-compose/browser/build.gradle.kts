plugins {
    kotlin("js")
    id("org.jetbrains.compose") version "1.1.0"
}

group = "me.simon"
version = "1.0"

dependencies {
    implementation(project(":common"))
    implementation(compose.web.core)
    implementation(compose.runtime)
    testImplementation(kotlin("test"))
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
}