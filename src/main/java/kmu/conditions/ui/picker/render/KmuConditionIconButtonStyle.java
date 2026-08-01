package kmu.conditions.ui.picker.render;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;

import java.awt.Color;
import java.util.Objects;

enum KmuConditionIconButtonStyle {
    DEFAULT(
        StarsectorUiColour.DARK_BLUE,
        StarsectorUiColour.LIGHT_BLUE,
        0.28f,
        0.18f),
    VISIBLE_PRESENT(
        StarsectorUiColour.DARK_GREEN,
        StarsectorUiColour.BRIGHT_GREEN,
        0.28f,
        0.42f),
    SUPPRESSED(
        StarsectorUiColour.MUTED_RED,
        StarsectorUiColour.BRIGHT_RED,
        0.34f,
        0.50f);

    private final StarsectorUiColour backdropColour;
    private final StarsectorUiColour borderColour;
    private final float backdropAlpha;
    private final float borderAlpha;

    KmuConditionIconButtonStyle(
            StarsectorUiColour backdropColour,
            StarsectorUiColour borderColour,
            float backdropAlpha,
            float borderAlpha) {
        this.backdropColour = backdropColour;
        this.borderColour = borderColour;
        this.backdropAlpha = backdropAlpha;
        this.borderAlpha = borderAlpha;
    }

    static KmuConditionIconButtonStyle forEntry(KmuConditionPickerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isSuppressed()) {
            return SUPPRESSED;
        }
        if (entry.isPresent() && !entry.isSuppressed() && !entry.isHidden()) {
            return VISIBLE_PRESENT;
        }
        return DEFAULT;
    }

    Color getBackdropColour() {
        return backdropColour.resolve();
    }

    Color getBorderColour() {
        return borderColour.resolve();
    }

    float getBackdropAlpha() {
        return backdropAlpha;
    }

    float getBorderAlpha() {
        return borderAlpha;
    }

    StarsectorUiColour getBackdropRawColour() {
        return backdropColour;
    }

    StarsectorUiColour getBorderRawColour() {
        return borderColour;
    }
}
