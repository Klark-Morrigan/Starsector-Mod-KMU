package kmu.ui.chooser.tooltip;

import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.function.Supplier;

final class KmuTooltipSectionPalette {
    static final Color FALLBACK_MUTED_TEXT = new Color(175, 175, 175, 180);
    static final Color FALLBACK_STANDARD_TEXT = new Color(220, 220, 220, 255);
    static final Color FALLBACK_STANDARD_BLUE_TITLE = new Color(170, 222, 255, 255);
    static final Color FALLBACK_STANDARD_SECTION_BACKDROP = new Color(31, 94, 112, 175);
    static final Color FALLBACK_WARNING_TITLE = new Color(255, 100, 0, 255);
    static final Color WARNING_BACKDROP = new Color(70, 20, 20);

    private KmuTooltipSectionPalette() {
    }

    static Color mutedText() {
        return starsectorColor(Misc::getGrayColor, FALLBACK_MUTED_TEXT);
    }

    static Color standardText() {
        return starsectorColor(Misc::getTextColor, FALLBACK_STANDARD_TEXT);
    }

    static Color standardBlueTitle() {
        return starsectorColor(Misc::getBasePlayerColor, FALLBACK_STANDARD_BLUE_TITLE);
    }

    static Color standardSectionBackdrop() {
        return starsectorColor(Misc::getDarkPlayerColor, FALLBACK_STANDARD_SECTION_BACKDROP);
    }

    static Color warningTitle() {
        return starsectorColor(Misc::getNegativeHighlightColor, FALLBACK_WARNING_TITLE);
    }

    private static Color starsectorColor(Supplier<Color> colorSupplier, Color fallback) {
        try {
            Color color = colorSupplier.get();
            return color != null ? color : fallback;
        } catch (Throwable exception) {
            return fallback;
        }
    }
}
