package org.example.config;

/**
 * Configuration record for UI-related settings.
 * 
 * @param bulkActionConfirmationThreshold Threshold for requiring confirmation on bulk actions
 * @param windowWidth Default window width
 * @param windowHeight Default window height
 * @param defaultTheme Default theme (light/dark)
 */
public record UIConfig(
    int bulkActionConfirmationThreshold,
    int windowWidth,
    int windowHeight,
    String defaultTheme
) {
    /** Default configuration values */
    public static final UIConfig DEFAULT = new UIConfig(
        5,      // bulkActionConfirmationThreshold
        1200,   // windowWidth
        750,    // windowHeight
        "light" // defaultTheme
    );
}
