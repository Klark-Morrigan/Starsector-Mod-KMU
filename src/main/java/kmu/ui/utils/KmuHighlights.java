package kmu.ui.utils;

import java.awt.Color;
import java.util.List;

/**
 * Utility for populating parallel highlight/color lists used by
 * {@link kmu.conditions.ui.picker.render.spec.KmuLabelSpec}.
 */
public final class KmuHighlights {
    private KmuHighlights() {
    }

    /**
     * Appends a token and its color to the respective parallel lists.
     */
    public static void add(List<String> highlights, List<Color> colors, String token, Color color) {
        highlights.add(token);
        colors.add(color);
    }
}
