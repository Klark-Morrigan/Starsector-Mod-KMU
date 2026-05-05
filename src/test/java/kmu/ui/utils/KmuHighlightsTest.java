package kmu.ui.utils;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuHighlightsTest {
    // --- add ---

    @Test
    void addAppendsTokenToHighlights() {
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        KmuHighlights.add(highlights, colors, "token", Color.RED);

        assertThat(highlights).containsExactly("token");
    }

    @Test
    void addAppendsColorToColors() {
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        KmuHighlights.add(highlights, colors, "token", Color.RED);

        assertThat(colors).containsExactly(Color.RED);
    }

    @Test
    void addPreservesExistingEntries() {
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        KmuHighlights.add(highlights, colors, "first", Color.RED);

        KmuHighlights.add(highlights, colors, "second", Color.BLUE);

        assertThat(highlights).containsExactly("first", "second");
        assertThat(colors).containsExactly(Color.RED, Color.BLUE);
    }
}
