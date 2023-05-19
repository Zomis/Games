module net.zomis {
    requires java.base;
    requires java.desktop;
    requires java.logging;
    requires ui.desktop;
    requires decompose.jvm;
    requires kotlinx.coroutines.core.jvm;
    requires kotlin.stdlib;
    requires com.fasterxml.jackson.kotlin;
    requires com.fasterxml.jackson.databind;
    requires lifecycle.jvm;
    requires io.ktor.client.core;
    requires io.ktor.client.cio;
    requires io.ktor.client.content.negotiation;
    requires io.ktor.serialization.jackson;
    requires extensions.compose.jetbrains.jvm;
    requires material.desktop;
    requires foundation.layout.desktop;
    requires java.xml;

    opens net.zomis.games.compose to java.desktop;
}
