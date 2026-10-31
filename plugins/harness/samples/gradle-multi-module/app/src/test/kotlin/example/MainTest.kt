package example

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

internal class MainTest {
    @Test
    fun `application prints a library greeting`() {
        val original: PrintStream = System.out
        val output: ByteArrayOutputStream = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(output))
            Main.main(arrayOf("Ada"))
        } finally {
            System.setOut(original)
        }
        assertEquals("Hello, Ada!${System.lineSeparator()}", output.toString())
    }
}
