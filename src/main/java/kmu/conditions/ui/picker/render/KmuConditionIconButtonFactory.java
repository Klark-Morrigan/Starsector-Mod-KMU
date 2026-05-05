package kmu.conditions.ui.picker.render;

import kmu.ui.geometry.KmuUiSize;

/**
 * Pure geometry helpers for sizing condition icon buttons.
 * Kept separate from {@link KmuConditionIconButton} so the grid and
 * style classes can access sizing constants without pulling in the
 * full button class.
 */
final class KmuConditionIconButtonFactory {
    private KmuConditionIconButtonFactory() {
    }

    static KmuConditionIconButtonLayout computeKmuConditionIconButtonLayout(float sourceWidth, float sourceHeight) {
        KmuUiSize bounds = computeIconSize(sourceWidth, sourceHeight);
        float buttonWidth = Math.max(
                KmuConditionIconButtonSizing.MIN_BUTTON_SIZE,
                bounds.getWidth() + KmuConditionIconButtonSizing.ICON_MARGIN * 2f);
        float buttonHeight = Math.max(
                KmuConditionIconButtonSizing.MIN_BUTTON_SIZE,
                bounds.getHeight() + KmuConditionIconButtonSizing.ICON_MARGIN * 2f);
        float iconOffsetX = (buttonWidth - bounds.getWidth()) / 2f;
        float iconOffsetY = (buttonHeight - bounds.getHeight()) / 2f;
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
            float fallback = KmuConditionIconButtonSizing.FALLBACK_ICON_SIZE;
            return new KmuUiSize(fallback, fallback);
        }
        float scale = KmuConditionIconButtonSizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT / sourceHeight;
        return new KmuUiSize(sourceWidth * scale, sourceHeight * scale);
    }

}
