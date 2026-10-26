package org.example.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TypeNamesTest {

    @Test
    void referencesWholeIdentifiersOnly() {
        assertTrue(TypeNames.references("User", "User"));
        assertTrue(TypeNames.references("List<User>", "User"));
        assertTrue(TypeNames.references("Map<String, User>", "User"));
        assertTrue(TypeNames.references("User[]", "User"));
        assertTrue(TypeNames.references("Optional<com.acme.User>", "User"));
        assertFalse(TypeNames.references("UserRole", "User"), "Antes daba falso positivo con contains");
        assertFalse(TypeNames.references("List<UserRole>", "User"));
        assertFalse(TypeNames.references("SuperUser", "User"));
        assertFalse(TypeNames.references(null, "User"));
    }

    @Test
    void predictorPicksTheFieldThatReallyReferencesTheTarget() {
        ClassInfo account = new ClassInfo("Account");
        account.addField(FieldInfo.simple("role", "UserRole"));
        account.addField(FieldInfo.simple("owner", "User"));

        FieldInfo field = MLRelationPredictor.findFieldReferencing(account, "User");

        assertEquals("owner", field.name(), "No debe elegir 'role' (UserRole) para el destino User");
    }

    @Test
    void featureTwoIsNoLongerACopyOfFeatureSeven() {
        ClassInfo post = new ClassInfo("Post");
        post.addField(FieldInfo.simple("author", "User"));
        double[] f = new FeatureExtractor().extractFeatures("Post", "User", "ManyToOne",
            post, new ClassInfo("User", List.of(), false, List.of()), "author", "User");

        assertNotEquals(f[2], f[7], "La feature 2 (Jaro-Winkler campo/destino) y la 7 (consistencia) son distintas");
        assertTrue(f[2] >= 0.0 && f[2] <= 1.0);
    }
}
