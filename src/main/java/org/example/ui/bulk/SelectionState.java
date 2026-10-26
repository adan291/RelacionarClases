package org.example.ui.bulk;

/**
 * Immutable record representing the current state of relation selection.
 * Used to communicate selection state to UI components and listeners.
 * 
 * Requirements: 2.1, 2.2
 */
public record SelectionState(
    int totalSelected,
    int visibleSelected,
    int hiddenSelected,
    boolean hasSelection,
    boolean allVisibleSelected
) {
    /**
     * Creates an empty selection state with all counts at zero.
     * 
     * @return an empty SelectionState
     */
    public static SelectionState empty() {
        return new SelectionState(0, 0, 0, false, false);
    }
}
