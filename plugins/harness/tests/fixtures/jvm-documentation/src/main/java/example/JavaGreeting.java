package example;

/**
 * Formats greetings for a recipient.
 */
public final class JavaGreeting {
    /**
     * Creates a greeting formatter.
     */
    public JavaGreeting() {}

    /**
     * Formats a greeting for the supplied recipient.
     *
     * @param name recipient's name
     * @return the formatted greeting
     * @throws IllegalArgumentException if the name is blank
     */
    public String greet(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return "Hello, " + name;
    }
}
