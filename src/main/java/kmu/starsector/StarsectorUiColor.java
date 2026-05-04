package kmu.starsector;

import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Optional;
import java.util.function.Supplier;

public enum StarsectorUiColor {
    GRAY(Misc::getGrayColor),
    TEXT_WHITE(Misc::getTextColor),
    BLUE(Misc::getBasePlayerColor),
    DARK_BLUE(Misc::getDarkPlayerColor),
    GOLD(Misc::getHighlightColor),
    RED(Misc::getNegativeHighlightColor),
    GREEN(Misc::getPositiveHighlightColor),
    WHITE(Color.WHITE),
    DIM_GRAY(new Color(130, 130, 130)),
    ORANGE(new Color(255, 100, 0, 255)),
    DARK_RED(new Color(70, 20, 20)),
    MUTED_RED(new Color(150, 50, 45)),
    BRIGHT_RED(new Color(255, 90, 80)),
    DARK_GREEN(new Color(35, 80, 45)),
    BRIGHT_GREEN(new Color(90, 220, 95)),
    LIGHT_BLUE(new Color(100, 180, 255));

    private final Supplier<Color> starsectorColor;
    private final Color customColor;

    StarsectorUiColor(Supplier<Color> starsectorColor) {
        this(starsectorColor, null);
    }

    StarsectorUiColor(Color customColor) {
        this(null, customColor);
    }

    StarsectorUiColor(Supplier<Color> starsectorColor, Color customColor) {
        if (starsectorColor == null && customColor == null) {
            throw new IllegalArgumentException("A color must define a Starsector source or a custom value.");
        }
        this.starsectorColor = starsectorColor;
        this.customColor = customColor;
    }

    Optional<Supplier<Color>> starsectorColor() {
        return Optional.ofNullable(starsectorColor);
    }

    Optional<Color> customColor() {
        return Optional.ofNullable(customColor);
    }

    boolean isCustom() {
        return customColor != null;
    }
}
