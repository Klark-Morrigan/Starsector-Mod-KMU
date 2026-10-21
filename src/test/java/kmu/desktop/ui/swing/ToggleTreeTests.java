package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what {@link ToggleTree.PairRow} refuses: halves at another depth than the pair, and a
 * pair inside a pair.
 */
final class ToggleTreeTests {

    @Nested
    class PairRow {

        @Test
        void refusesAHalfAtAnotherDepth() {

            assertThatThrownBy(() -> ToggleTree.Row.ofPair(
                    1,
                    buildSwitchRow("left", 1),
                    buildSwitchRow("right", 2)))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAPairAsAHalf() {

            var inner = ToggleTree.Row.ofPair(1, buildSwitchRow("a", 1), buildSwitchRow("b", 1));

            assertThatThrownBy(() -> ToggleTree.Row.ofPair(1, inner, buildSwitchRow("c", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static ToggleTree.Row buildSwitchRow(String key, int indent) {

        return ToggleTree.Row.ofSwitch(
            indent,
            new ToggleTree.Switch(
                key,
                new RememberedValue<>(key, false, SavedValueFormat.FLAG, isOn -> { })));
    }
}
