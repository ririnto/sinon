package example.checkout;

import example.catalog.Product;

/**
 * Calculates totals using the catalog's price units.
 *
 * <p>Totals remain in cents so that no floating point conversion is required.
 */
public final class Checkout {
    private Checkout() {}

    /**
     * Calculates the total price for a quantity of one product.
     *
     * @param product the catalog product
     * @param quantity the nonnegative number of units
     * @return the total in cents
     */
    public static int totalCents(Product product, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be nonnegative");
        }
        return Math.multiplyExact(product.priceCents(), quantity);
    }
}
