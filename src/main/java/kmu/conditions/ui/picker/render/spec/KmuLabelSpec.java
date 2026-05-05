package kmu.conditions.ui.picker.render.spec;

import com.fs.starfarer.api.ui.LabelAPI;

import java.awt.Color;

/**
 * Pairs a rendered paragraph's text with its base color, highlight terms, and
 * corresponding colors so that text construction and color specification always
 * stay in sync.
 *
 * <p>{@code baseColor} is the paragraph's default text color. A {@code null}
 * value means the renderer should fall back to its own default.
 */
public final class KmuLabelSpec {
    private final String text;
    private final Color baseColor;
    private final String[] highlights;
    private final Color[] highlightColors;

    /** Constructs a spec with an explicit base color. */
    public KmuLabelSpec(String text, Color baseColor, String[] highlights, Color[] highlightColors) {
        this.text = text;
        this.baseColor = baseColor;
        this.highlights = highlights;
        this.highlightColors = highlightColors;
    }

    /** Constructs a spec using the renderer's default base color. */
    public KmuLabelSpec(String text, String[] highlights, Color[] highlightColors) {
        this(text, null, highlights, highlightColors);
    }

    public String getText() {
        return text;
    }

    /**
     * Returns the explicit base color for this label, or {@code null} to use
     * the renderer's default.
     */
    public Color getBaseColor() {
        return baseColor;
    }

    /** Applies highlights and their colors to an already-rendered label. */
    public void applyTo(LabelAPI label) {
        label.setHighlight(highlights);
        label.setHighlightColors(highlightColors);
    }

    public String[] getHighlights() {
        return highlights;
    }

    public Color[] getHighlightColors() {
        return highlightColors;
    }
}
