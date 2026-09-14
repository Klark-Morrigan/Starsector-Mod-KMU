package kmu.conditions.ui.picker.render;

final class KmuConditionIconButtonLayout {
    private final float buttonWidth;
    private final float buttonHeight;
    private final float iconWidth;
    private final float iconHeight;
    private final float iconOffsetX;
    private final float iconOffsetY;

    KmuConditionIconButtonLayout(
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
