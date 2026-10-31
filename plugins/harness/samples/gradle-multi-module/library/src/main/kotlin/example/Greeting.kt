package example

/**
 * Produces greetings for named recipients.
 */
class Greeting {
    /**
     * Returns a greeting for a name, or a generic greeting for blank input.
     */
    fun greeting(name: String): String = "Hello, ${name.trim().ifEmpty { "world" }}!"
}
