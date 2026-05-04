package kmu.conditions.ui.picker.render.spec;

import com.fs.starfarer.api.ui.LabelAPI;

import java.awt.Color;

/**
 * Pairs a rendered paragraph's text with its highlight terms and corresponding
 * colors so that text construction and highlight specification always stay in sync.
 */
public final class KmuLabelSpec {
    private final String text;
    private final String[] highlights;
    private final Color[] highlightColors;

    public KmuLabelSpec(String text, String[] highlights, Color[] highlightColors) {
        this.text = text;
        this.highlights = highlights;
        this.highlightColors = highlightColors;
    }

    public String getText() {
        return text;
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
