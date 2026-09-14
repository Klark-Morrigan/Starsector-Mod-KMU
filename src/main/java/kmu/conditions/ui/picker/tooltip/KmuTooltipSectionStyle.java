package kmu.conditions.ui.picker.tooltip;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import java.awt.Color;

public enum KmuTooltipSectionStyle {
    MUTED(StarsectorUiColour.LIGHT_BLUE, StarsectorUiColour.DARK_BLUE, StarsectorUiColour.VANILLA_GRAY),
    WARNING(StarsectorUiColour.ORANGE, StarsectorUiColour.DARK_RED, StarsectorUiColour.VANILLA_TEXT);

    private final StarsectorUiColour titleColour;
    private final StarsectorUiColour backgroundColour;
    private final StarsectorUiColour bodyColour;

    KmuTooltipSectionStyle(
            StarsectorUiColour titleColour,
            StarsectorUiColour backgroundColour,
            StarsectorUiColour bodyColour) {
        this.titleColour = titleColour;
        this.backgroundColour = backgroundColour;
        this.bodyColour = bodyColour;
    }

    Color titleColour() {
        return titleColour.resolve();
    }

    Color backgroundColour() {
        return backgroundColour.resolve();
    }

    Color bodyColour() {
        return bodyColour.resolve();
    }

    StarsectorUiColour titleRawColour() {
        return titleColour;
    }

    StarsectorUiColour backgroundRawColour() {
        return backgroundColour;
    }

    StarsectorUiColour bodyRawColour() {
        return bodyColour;
    }
}
