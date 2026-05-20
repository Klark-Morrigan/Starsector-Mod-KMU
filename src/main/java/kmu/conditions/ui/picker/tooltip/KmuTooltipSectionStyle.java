package kmu.conditions.ui.picker.tooltip;

import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.color.StarsectorUiColorProvider;

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
        return StarsectorUiColorProvider.get(titleColor);
    }

    Color backgroundColor() {
        return StarsectorUiColorProvider.get(backgroundColor);
    }

    Color bodyColor() {
        return StarsectorUiColorProvider.get(bodyColor);
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
