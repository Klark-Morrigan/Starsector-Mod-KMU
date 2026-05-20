package kmu.conditions.ui.picker.tooltip;

import kmlib.starsector.ui.color.StarsectorUiColor;

import java.awt.Color;

public enum KmuTooltipSectionStyle {
    MUTED(StarsectorUiColor.LIGHT_BLUE, StarsectorUiColor.DARK_BLUE, StarsectorUiColor.VANILLA_GRAY),
    WARNING(StarsectorUiColor.ORANGE, StarsectorUiColor.DARK_RED, StarsectorUiColor.VANILLA_TEXT);

    private final StarsectorUiColor titleColor;
    private final StarsectorUiColor backgroundColor;
    private final StarsectorUiColor bodyColor;

    KmuTooltipSectionStyle(
            StarsectorUiColor titleColor,
            StarsectorUiColor backgroundColor,
            StarsectorUiColor bodyColor) {
        this.titleColor = titleColor;
        this.backgroundColor = backgroundColor;
        this.bodyColor = bodyColor;
    }

    Color titleColor() {
        return titleColor.resolve();
    }

    Color backgroundColor() {
        return backgroundColor.resolve();
    }

    Color bodyColor() {
        return bodyColor.resolve();
    }

    StarsectorUiColor titleRawColor() {
        return titleColor;
    }

    StarsectorUiColor backgroundRawColor() {
        return backgroundColor;
    }

    StarsectorUiColor bodyRawColor() {
        return bodyColor;
    }
}
