package app.sitara.music

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every module's English strings have a Bangla counterpart with the same format arguments and plural keys, and no
 * screen draws an English literal with Text("…") outside the resources (the brand mark and a URL hint excepted).
 */
class TranslationsTest {
    private val root = File("..").canonicalFile
    private val modules = listOf("app", "core/designsystem", "core/player", "feature/home", "feature/library", "feature/player", "feature/search", "feature/settings")

    private class Entry(val args: List<String>, val quantities: Set<String>)

    private fun read(file: File): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val entries = mutableMapOf<String, Entry>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            entries[e.getAttribute("name")] = Entry(args(e.textContent), emptySet())
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
            val other = (0 until items.length).map { items.item(it) as Element }.first { it.getAttribute("quantity") == "other" }
            entries[e.getAttribute("name")] = Entry(args(other.textContent), quantities)
        }
        return entries
    }

    private fun args(text: String) = Regex("%(\\d+\\$)?[sdf]").findAll(text).map { it.value }.toList().sorted()

    @Test
    fun `every English string has a Bangla one with the same arguments`() {
        var total = 0
        for (module in modules) {
            val english = read(root.resolve("$module/src/main/res/values/strings.xml"))
            val bangla = read(root.resolve("$module/src/main/res/values-bn/strings.xml"))
            assertEquals("$module: keys", english.keys.sorted(), bangla.keys.sorted())
            for ((key, entry) in english) {
                val translated = bangla.getValue(key)
                assertEquals("$module $key: format arguments", entry.args, translated.args)
                if (entry.quantities.isNotEmpty()) assertTrue("$module $key: plural needs other", "other" in translated.quantities)
            }
            total += english.size
        }
        assertTrue("strings were found ($total)", total > 500)
    }

    @Test
    fun `screens draw no English literal outside the resources`() {
        val allowed = setOf("Text(\"Sitara\"", "Text(\"music.youtube.com/playlist?list=…\")")
        val literal = Regex("""Text\("[A-Za-z][^"]*"""")
        val found = modules.filter { it != "core/player" }.flatMap { module ->
            root.resolve("$module/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    literal.find(line)?.value?.takeIf { value -> allowed.none { line.contains(it) } }?.let { "${file.name}:${index + 1} $it" }
                }
            }.toList()
        }
        assertTrue("Hard-coded text:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
