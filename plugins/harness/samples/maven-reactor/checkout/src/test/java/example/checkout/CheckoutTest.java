package example.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import example.catalog.Product;
import org.junit.jupiter.api.Test;

class CheckoutTest {
    @Test
    void totalsCatalogProducts() {
        assertEquals(375, Checkout.totalCents(new Product("pen", 125), 3));
    }

    @Test
    void rejectsNegativeQuantities() {
        assertThrows(IllegalArgumentException.class, () -> Checkout.totalCents(new Product("pen", 125), -1));
    }
}
