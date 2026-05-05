package kmu.conditions.ui.picker.render.spec;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class KmuLabelSpecTest {
    private static final Color GRAY = new Color(155, 155, 155);

    @Test
    void storesText() {
        KmuLabelSpec spec = new KmuLabelSpec("hello", new String[0], new Color[0]);

        assertThat(spec.getText()).isEqualTo("hello");
    }

    @Test
    void storesHighlightsAndColors() {
        String[] highlights = {"hello"};
        Color[] colors = {GRAY};
        KmuLabelSpec spec = new KmuLabelSpec("hello", highlights, colors);

        assertThat(spec.getHighlights()).containsExactly("hello");
        assertThat(spec.getHighlightColors()).containsExactly(GRAY);
    }

    @Test
    void threeArgConstructorDefaultsBaseColorToNull() {
        KmuLabelSpec spec = new KmuLabelSpec("hello", new String[0], new Color[0]);

        assertThat(spec.getBaseColor()).isNull();
    }

    @Test
    void fourArgConstructorStoresBaseColor() {
        KmuLabelSpec spec = new KmuLabelSpec("hello", GRAY, new String[0], new Color[0]);

        assertThat(spec.getBaseColor()).isEqualTo(GRAY);
    }
}
