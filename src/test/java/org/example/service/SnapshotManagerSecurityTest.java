package org.example.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

import org.example.model.AnalysisSnapshot;
import org.example.model.SnapshotMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El ID de un snapshot se usa como nombre de fichero: no puede salir del directorio de snapshots.
 */
class SnapshotManagerSecurityTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"../outside", "..\\outside", "sub/inner", "a/../../b", "C:\\Windows\\x", "/etc/passwd", "..", "a.b"})
    void rejectsIdsThatCouldEscapeTheDirectory(String id) throws IOException {
        Path snapshots = Files.createDirectories(tempDir.resolve("snapshots"));
        Path outside = tempDir.resolve("outside.json");
        Files.writeString(outside, "{\"id\":\"outside\"}");
        SnapshotManager manager = new SnapshotManager(snapshots.toString());

        assertFalse(SnapshotManager.isValidSnapshotId(id));
        assertNull(manager.loadSnapshot(id));
        assertFalse(manager.deleteSnapshot(id));
        assertTrue(Files.exists(outside), "El fichero de fuera no se toca");

        AnalysisSnapshot snapshot = new AnalysisSnapshot(id,
            new SnapshotMetadata(id, LocalDateTime.now(), "/p", "p", "d"), new ArrayList<>(), new HashMap<>());
        assertThrows(IllegalArgumentException.class, () -> manager.saveSnapshot(snapshot));
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_20260101_120000", "test_project0_123456789", "3f2c1a9e-8d4b-4c6e-9a1f-0b2c3d4e5f60"})
    void acceptsGeneratedAndUuidIds(String id) {
        assertTrue(SnapshotManager.isValidSnapshotId(id));
    }

    @Test
    void generatedIdsAreAlwaysValid() {
        SnapshotManager manager = new SnapshotManager(tempDir.toString());
        assertTrue(SnapshotManager.isValidSnapshotId(manager.generateSnapshotId("../../my project!")));
    }
}
