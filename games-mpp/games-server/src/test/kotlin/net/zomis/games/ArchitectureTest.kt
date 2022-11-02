package net.zomis.games

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import net.zomis.games.api.Scope
import net.zomis.games.dsl.impl.GameMarker
import net.zomis.games.impl.alchemists.Alchemists
import net.zomis.games.impl.alchemists.Ingredient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {

    val classes: JavaClasses = ClassFileImporter().importPackages("net.zomis.games")

    @Test
    fun `gameMarker should be scopes`() {
        ArchRuleDefinition.classes().that().areAnnotatedWith(GameMarker::class.java).should().haveNameMatching(".*Scope")
            .check(classes)
    }

    @Test
    fun `contexts should not be interfaces`() {
        ArchRuleDefinition.classes().that().haveNameMatching(".*Context").should().notBeInterfaces()
            .check(classes)
    }

    @Test
    fun scopes() {
        val rule = ArchRuleDefinition.classes().that().haveNameMatching(".*Scope").should().beInterfaces().andShould()
            .beAssignableTo(Scope::class.java)
        rule.check(classes)
    }

    @Test
    fun printNames() {
        classes.map { it.fullName }.sortedBy { it }.forEach {
            println(it)
        }
    }

    @Test
    fun cycleTest() {
        SlicesRuleDefinition.slices().matching("net.zomis.games.(*)..").should().beFreeOfCycles()
    }

}
