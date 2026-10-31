package example.catalog;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductTest {
    @Test
    void rejectsNegativePrices() {
        assertThrows(IllegalArgumentException.class, () -> new Product("pen", -1));
    }
}
