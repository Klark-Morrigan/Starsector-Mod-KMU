package kmu.conditions.ui.picker.render;

/**
 * Sizing constants and derived geometry for condition icon buttons.
 * Shared across the button, factory, and grid so each class can reference
 * measurements without depending on one another.
 */
final class KmuConditionIconButtonSizing {
    static final float ICON_MARGIN = 6f;
    static final float VANILLA_COLONY_CONDITION_ICON_HEIGHT = 40f;
    static final float FALLBACK_ICON_SIZE = VANILLA_COLONY_CONDITION_ICON_HEIGHT;
    static final float MIN_BUTTON_SIZE = 34f;

    private KmuConditionIconButtonSizing() {
    }

    static float computeSquareButtonWidth() {
        return VANILLA_COLONY_CONDITION_ICON_HEIGHT + ICON_MARGIN * 2f;
    }
}
