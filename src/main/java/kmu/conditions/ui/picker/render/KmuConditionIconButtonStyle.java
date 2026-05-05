package kmu.conditions.ui.picker.render;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

import java.awt.Color;
import java.util.Objects;

enum KmuConditionIconButtonStyle {
    DEFAULT(
            StarsectorUiColor.DARK_BLUE,
            StarsectorUiColor.BLUE,
            0.28f,
            0.18f),
    VISIBLE_PRESENT(
            StarsectorUiColor.DARK_GREEN,
            StarsectorUiColor.BRIGHT_GREEN,
            0.28f,
            0.42f),
    SUPPRESSED(
            StarsectorUiColor.MUTED_RED,
            StarsectorUiColor.BRIGHT_RED,
            0.34f,
            0.50f);

    private final StarsectorUiColor backdropColor;
    private final StarsectorUiColor borderColor;
    private final float backdropAlpha;
    private final float borderAlpha;

    KmuConditionIconButtonStyle(
            StarsectorUiColor backdropColor,
            StarsectorUiColor borderColor,
            float backdropAlpha,
            float borderAlpha) {
        this.backdropColor = backdropColor;
        this.borderColor = borderColor;
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

    Color getBackdropColor() {
        return StarsectorUiColorProvider.get(backdropColor);
    }

    Color getBorderColor() {
        return StarsectorUiColorProvider.get(borderColor);
    }

    float getBackdropAlpha() {
        return backdropAlpha;
    }

    float getBorderAlpha() {
        return borderAlpha;
    }

    StarsectorUiColor getBackdropRawColor() {
        return backdropColor;
    }

    StarsectorUiColor getBorderRawColor() {
        return borderColor;
    }
}
