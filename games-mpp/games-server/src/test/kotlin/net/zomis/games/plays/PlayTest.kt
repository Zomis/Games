package net.zomis.games.plays

import net.zomis.games.server.test.PlayTests
import net.zomis.games.server.test.TestPlayMenu
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

class PlayTest {

    fun recursive(directory: File): List<DynamicNode> {
        val pairs = directory.listFiles()?.partition { it.isDirectory } ?: return emptyList()
        val next = pairs.first.map {
            DynamicContainer.dynamicContainer(it.name, recursive(it))
        }
        return next + pairs.second.map { file(it) }
    }

    fun file(file: File): DynamicNode {
        return DynamicTest.dynamicTest(file.name) {
            if (file.name.endsWith("json")) {
                PlayTests.fullJsonTest(file, TestPlayMenu.choices, false)
            }
        }
    }

    @TestFactory
    fun test(): List<DynamicNode> {
        return recursive(File("../playthroughs"))
    }

}
