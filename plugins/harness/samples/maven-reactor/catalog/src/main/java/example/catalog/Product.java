package example.catalog;

/**
 * A product with its unit price in cents.
 *
 * @param name the product name
 * @param priceCents the nonnegative unit price
 */
public record Product(String name, int priceCents) {
    /**
     * Rejects prices that cannot represent a sale.
     *
     * @throws IllegalArgumentException if the price is negative
     */
    public Product {
        if (priceCents < 0) {
            throw new IllegalArgumentException("price must be nonnegative");
        }
    }
}
