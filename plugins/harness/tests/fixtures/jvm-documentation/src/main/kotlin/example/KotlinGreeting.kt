package example

/**
 * Formats greetings for a recipient.
 */
class KotlinGreeting {
    /**
     * Formats a greeting for the supplied recipient.
     *
     * @param name recipient's name
     * @return the formatted greeting
     * @throws IllegalArgumentException if the name is blank
     */
    fun greet(name: String): String {
        require(name.isNotBlank()) { "name must not be blank" }
        return "Hello, $name"
    }
}
