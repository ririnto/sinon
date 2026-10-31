package example

/**
 * Runs the greeting application.
 */
object Main {
    /**
     * Writes a greeting for the first argument.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        System.out.write("${Greeting().greeting(args.firstOrNull().orEmpty())}${System.lineSeparator()}".toByteArray())
    }
}
