package mimi.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test names have to survive Kotlin/Native.
 *
 * A backtick-quoted name is a real identifier, and the Native compiler rejects
 * punctuation in one — a comma in a `fun \`a, b\`()` fails the iOS build and
 * nothing else. The JVM accepts it happily, so the mistake is invisible until a
 * full assemble, which is minutes later and in a module nobody touched. This
 * catches it in the same second it is written.
 */
class TestNamingTest {

    private val repoDir = File(
        System.getProperty("mimi.repoDir") ?: error("mimi.repoDir is not set")
    )

    /** Punctuation the Native compiler refuses inside a quoted identifier. */
    private val forbidden = ",()[]{}<>;:.\\/\"'@#"

    private val namePattern = Regex("""fun\s+`([^`]+)`""")

    @Test
    fun `no test name uses punctuation that Kotlin Native rejects`() {
        val offenders = mutableListOf<String>()

        for (module in repoDir.listFiles().orEmpty()) {
            val src = File(module, "src").takeIf { it.isDirectory } ?: continue
            src.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && "Test" in it.path }
                .forEach { file ->
                    for (match in namePattern.findAll(file.readText())) {
                        val name = match.groupValues[1]
                        val bad = name.filter { it in forbidden }
                        if (bad.isNotEmpty()) {
                            offenders += "${file.relativeTo(repoDir)}: `$name` (contains $bad)"
                        }
                    }
                }
        }

        assertTrue(
            offenders.isEmpty(),
            "these names break the iOS build:\n" + offenders.joinToString("\n")
        )
    }
}
