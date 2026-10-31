package example

import kotlin.test.Test
import kotlin.test.assertEquals

internal class GreetingTest {
    @Test
    fun `trims a supplied name`() {
        assertEquals("Hello, Ada!", Greeting().greeting(" Ada "))
    }

    @Test
    fun `uses a default for blank input`() {
        assertEquals("Hello, world!", Greeting().greeting("  "))
    }
}
