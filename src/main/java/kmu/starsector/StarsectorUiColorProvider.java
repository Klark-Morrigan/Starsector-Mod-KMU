package kmu.starsector;

import java.awt.Color;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class StarsectorUiColorProvider {
    private StarsectorUiColorProvider() {
    }

    public static Color get(StarsectorUiColor rawColor) {
        Objects.requireNonNull(rawColor, "rawColor");

        Optional<Supplier<Color>> starsectorSource = rawColor.starsectorColor();
        if (starsectorSource.isPresent()) {
            return resolveStarsectorColor(rawColor, starsectorSource.get());
        }

        return rawColor.customColor()
            .orElseThrow(() -> new IllegalStateException("Missing color value for " + rawColor.name()));
    }

    private static Color resolveStarsectorColor(
            StarsectorUiColor rawColor,
            Supplier<Color> starsectorSource) {
        Color resolvedColor = starsectorSource.get();
        return Objects.requireNonNull(resolvedColor, "Starsector color for " + rawColor.name());
    }
}
