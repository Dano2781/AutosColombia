package co.autoscolombia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PlateNormalizationTest {
    @Test void removesSpacesAndHyphensAndUppercases() {
        AccessService service = new AccessService(null, null);
        assertEquals("ABC123", service.normalizePlate(" ab-c 123 "));
    }
}
