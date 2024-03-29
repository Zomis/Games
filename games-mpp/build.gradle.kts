import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("multiplatform")
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.github.ben-manes.versions") version "0.39.0"
}

group = "net.zomis"
version = "1.0-SNAPSHOT"

val jacksonVersion = "2.13.1"
val jupiterVersion = "5.7.1"
val coroutinesVersion = "1.6.3"
val ktorVersion = "2.0.3"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation(project(":games-dsl"))
                implementation(project(":games-impl"))
//                implementation("com.github.lewik.klog:klog-metadata:2.0.2")
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
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
                implementation("com.github.lewik.klog:klog-jvm:2.0.2")
                implementation(kotlin("reflect"))

                // For Kotlin scripting inside a shadow jar (some of these dependencies may be removed)
                implementation(kotlin("compiler-embeddable"))
                implementation(kotlin("scripting-compiler-embeddable"))
                implementation(kotlin("scripting-compiler-impl-embeddable"))
                implementation(kotlin("scripting-jsr223"))
                implementation(kotlin("scripting-common"))
                implementation(kotlin("script-runtime"))
                implementation(kotlin("script-util"))

                implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
                implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
                implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

                // Ktor
                implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
                implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-http-redirect-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")

                implementation("com.beust:jcommander:1.81")
                implementation("org.java-websocket:Java-WebSocket:1.5.2")
                implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.18.0")
                implementation("org.apache.logging.log4j:log4j-core:2.18.0")

                implementation("com.amazonaws:aws-java-sdk-dynamodb:1.11.1031")
                implementation("org.postgresql:postgresql:42.2.20")
                implementation("com.zaxxer:HikariCP:3.4.5")
                implementation("com.github.ben-manes.caffeine:caffeine:2.9.1")

                implementation("ai.djl:api:0.3.0")
                implementation("ai.djl:model-zoo:0.3.0")
                runtimeOnly("ai.djl.mxnet:mxnet-engine:0.3.0")
                runtimeOnly("ai.djl.mxnet:mxnet-native-auto:1.6.0")
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("games-server/src/test/kotlin")
            resources.srcDir("games-server/src/test/resources")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
                implementation("com.tngtech.archunit:archunit-junit5:1.0.0")
                implementation("com.google.truth:truth:1.1.3")
            }
        }
        val jsMain by getting {
            kotlin.srcDir("games-js/src/main/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
                implementation("com.github.lewik.klog:klog-js:2.0.2")
            }
        }
    }

}

tasks {
    val shadowCreate by creating(ShadowJar::class) {
        manifest {
            attributes["Main-Class"] = "net.zomis.games.server2.Main"
        }
        archiveClassifier.set("all")
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations =
            mutableListOf(kotlin.jvm().compilations.getByName("main").compileDependencyFiles as Configuration)
    }
    val build by existing {
        dependsOn(shadowCreate)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<Delete> {
        delete("/documentation/INVITES.md")
        delete("/documentation/LOGIN_AND_LOBBY.md")
    }

}
