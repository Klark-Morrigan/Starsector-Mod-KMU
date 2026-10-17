package kmu.conditions.ui.picker.render;

/**
 * Where a condition icon button sits and how big it is: the button, the icon inside it, and the
 * offset that centres one in the other - with the sizing every button is measured by.
 *
 * <p>The measurements and the layout derived from them live in one place, so the grid and the
 * button cannot size a button by two different rules.
 */
final class KmuConditionIconButtonLayout {

    static final float ICON_MARGIN = 6f;
    static final float VANILLA_COLONY_CONDITION_ICON_HEIGHT = 40f;
    static final float FALLBACK_ICON_SIZE = VANILLA_COLONY_CONDITION_ICON_HEIGHT;
    static final float MIN_BUTTON_SIZE = 34f;

    private final float buttonWidth;
    private final float buttonHeight;
    private final float iconWidth;
    private final float iconHeight;
    private final float iconOffsetX;
    private final float iconOffsetY;

    private KmuConditionIconButtonLayout(
            float buttonWidth,
            float buttonHeight,
            float iconWidth,
            float iconHeight,
            float iconOffsetX,
            float iconOffsetY) {

        this.buttonWidth = buttonWidth;
        this.buttonHeight = buttonHeight;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconOffsetX = iconOffsetX;
        this.iconOffsetY = iconOffsetY;
    }

    static KmuConditionIconButtonLayout computeButtonLayout(float sourceWidth, float sourceHeight) {

        var bounds = computeIconSize(sourceWidth, sourceHeight);

        var buttonWidth = Math.max(MIN_BUTTON_SIZE, bounds.getWidth() + ICON_MARGIN * 2f);
        var buttonHeight = Math.max(MIN_BUTTON_SIZE, bounds.getHeight() + ICON_MARGIN * 2f);

        var iconOffsetX = (buttonWidth - bounds.getWidth()) / 2f;
        var iconOffsetY = (buttonHeight - bounds.getHeight()) / 2f;

        return new KmuConditionIconButtonLayout(
            buttonWidth,
            buttonHeight,
            bounds.getWidth(),
            bounds.getHeight(),
            iconOffsetX,
            iconOffsetY);
    }

    static KmuUiSize computeIconSize(float sourceWidth, float sourceHeight) {

        if (sourceWidth <= 0f || sourceHeight <= 0f) {
            return new KmuUiSize(FALLBACK_ICON_SIZE, FALLBACK_ICON_SIZE);
        }

        var scale = VANILLA_COLONY_CONDITION_ICON_HEIGHT / sourceHeight;

        return new KmuUiSize(sourceWidth * scale, sourceHeight * scale);
    }

    static float computeSquareButtonWidth() {
        return VANILLA_COLONY_CONDITION_ICON_HEIGHT + ICON_MARGIN * 2f;
    }

    float getButtonWidth() {
        return buttonWidth;
    }

    float getButtonHeight() {
        return buttonHeight;
    }

    float getIconWidth() {
        return iconWidth;
    }

    float getIconHeight() {
        return iconHeight;
    }

    float getIconOffsetX() {
        return iconOffsetX;
    }

    float getIconOffsetY() {
        return iconOffsetY;
    }
}
