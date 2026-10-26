package org.example.bench;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** Silencia java.util.logging para que el log no entre en la medida. */
public final class BenchLogging {

    private BenchLogging() {
    }

    public static void quiet() {
        LogManager.getLogManager().reset();
        Logger.getLogger("").setLevel(Level.OFF);
    }
}
