package kmu.maplayers.base.chrome.arrange;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a row's toggle turns over rather than only turning off, and that it moves nothing else.
 *
 * <p>Worth its own suite because the editor exercises it only in the direction its own guard allows -
 * hiding a tab, and showing one back. A toggle that quietly rewrote the ID or the label would leave the
 * editor recording an arrangement about a layer the player never touched, and nothing in the editor's
 * own cases would name it.
 */
final class MapLayerArrangementRowTest {

    private static final MapLayerArrangementRow SHOWN_ROW =
        new MapLayerArrangementRow("alpha", "Alpha", false);

    @Nested
    class ToggleHidden {

        @Test
        void toggleHiddenTakesAShownRowsTabOffTheBar() {

            assertThat(SHOWN_ROW.toggleHidden())
                .isEqualTo(new MapLayerArrangementRow("alpha", "Alpha", true));
        }

        @Test
        void toggleHiddenPutsAHiddenRowsTabBackOnTheBar() {

            assertThat(SHOWN_ROW.toggleHidden().toggleHidden())
                .isEqualTo(SHOWN_ROW);
        }

        @Test
        void toggleHiddenLeavesTheRowItWasCalledOnAlone() {
            // A record, so the toggle answers with a new row - and the one the caller is still holding
            // is the one it was showing.
            SHOWN_ROW.toggleHidden();

            assertThat(SHOWN_ROW.isHidden())
                .isFalse();
        }
    }
}
