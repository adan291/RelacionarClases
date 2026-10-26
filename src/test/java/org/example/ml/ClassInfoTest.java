package org.example.ml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClassInfoTest {

    @Test
    void fourArgConstructorInitializesAllLists() {
        ClassInfo info = new ClassInfo("Order", List.of("Customer", "String"), true, List.of("OrderLine"));

        assertDoesNotThrow(info::getFields);
        assertTrue(info.getFields().isEmpty());
        assertTrue(info.getInterfaces().isEmpty());
        assertTrue(info.getConstructorParams().isEmpty());
        assertFalse(info.hasIdField());
        assertFalse(info.hasConstructorInjection("Customer"));
        assertEquals(2, info.getFieldCount(), "Sin FieldInfo, cuenta los tipos de campo");

        info.addField(FieldInfo.withAnnotations("id", "Long", List.of("Id")));
        info.addInterface("Serializable");
        info.addConstructorParam("Customer");

        assertEquals(1, info.getFields().size());
        assertTrue(info.hasIdField());
        assertEquals(List.of("Serializable"), info.getInterfaces());
        assertTrue(info.hasConstructorInjection("Customer"));
    }

    @Test
    void fourArgConstructorWorksWithFeatureExtractor() {
        ClassInfo a = new ClassInfo("Order", List.of("Customer"), false, null);
        ClassInfo b = new ClassInfo("Customer", null, false, null);

        double[] features = assertDoesNotThrow(
            () -> new FeatureExtractor().extractFeatures("Order", "Customer", "ManyToOne", a, b));
        assertEquals(FeatureExtractor.FEATURE_COUNT, features.length);
    }
}
