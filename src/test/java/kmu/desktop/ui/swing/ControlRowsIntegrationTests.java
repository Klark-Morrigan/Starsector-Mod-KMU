package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JRadioButton;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ControlRows#buildRadio} against a real {@link SavedValues} file: the row opens
 * on what was remembered and tells its owner so before anything draws, a pick is written down
 * under the option's own name rather than its label, the reset puts both the buttons and the
 * owner back to the row's own fallback, and a remembered name no longer offered falls back to it
 * rather than leaving the group with nothing selected.
 *
 * <p>Against the file rather than a stand-in, because the fault this guards is a row that
 * appears to work for a whole session and remembers nothing - which only the round trip
 * through the store can catch.
 */
final class ControlRowsIntegrationTests {

    // What the row picks between. A stand-in with three options, so the reset has somewhere to
    // come back FROM and the fallback has a neighbour it could wrongly land on.
    private enum Loudness {
        QUIET,
        LOUD,
        DEAFENING
    }

    private static final String KEY = "loudness";

    private static final List<ControlRows.Pick<Loudness>> PICKS = List.of(
        new ControlRows.Pick<>("Quiet", Loudness.QUIET),
        new ControlRows.Pick<>("Loud", Loudness.LOUD),
        new ControlRows.Pick<>("Deafening", Loudness.DEAFENING));

    @TempDir
    private Path remembered;

    private final List<Loudness> told = new ArrayList<>();

    private final List<String> refreshes = new ArrayList<>();

    private SavedValues saved;

    @BeforeEach
    void installTheStore() {

        saved = SavedValues.rememberIn(remembered.resolve("viewer.json"));
    }

    @Nested
    class BuildRadio {

        @Test
        void tellsTheOwnerTheFallbackWhenNothingIsRemembered() {

            buildRadio(Loudness.QUIET);

            assertThat(told)
                .containsExactly(Loudness.QUIET);
        }

        // The fallback is the row's own, not whichever option happens to be listed first, so two
        // rows sharing one list of options can open on different ones.
        @Test
        void opensOnItsOwnFallbackWhereThatIsNotTheFirstOption() {

            var row = buildRadio(Loudness.DEAFENING);

            assertThat(told)
                .containsExactly(Loudness.DEAFENING);
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Deafening");
        }

        @Test
        void opensOnTheRememberedPick() {

            saved.put(KEY, "DEAFENING");

            var row = buildRadio(Loudness.QUIET);

            assertThat(told)
                .containsExactly(Loudness.DEAFENING);
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Deafening");
        }

        @Test
        void remembersAPickUnderTheOptionsOwnName() {

            var row = buildRadio(Loudness.QUIET);

            ComponentTreeFixture.findAll(row, JRadioButton.class).get(1).doClick();

            assertThat(told)
                .containsExactly(Loudness.QUIET, Loudness.LOUD);
            assertThat(saved.get(KEY, "nothing"))
                .isEqualTo("LOUD");
            assertThat(refreshes)
                .containsExactly("refreshed");
        }

        @Test
        void putsTheRowAndTheOwnerBackToTheFallback() {

            saved.put(KEY, "QUIET");

            var row = buildRadio(Loudness.LOUD);

            ComponentTreeFixture.findOnly(row, JButton.class).doClick();

            assertThat(told)
                .containsExactly(Loudness.QUIET, Loudness.LOUD);
            assertThat(saved.get(KEY, "nothing"))
                .isEqualTo("LOUD");
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Loud");
        }

        @Test
        void fallsBackWhereTheRememberedNameIsNoLongerOffered() {

            saved.put(KEY, "INAUDIBLE");

            var row = buildRadio(Loudness.QUIET);

            assertThat(told)
                .containsExactly(Loudness.QUIET);
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Quiet");
        }

        @Test
        void refreshesNothingWhileBuilt() {

            buildRadio(Loudness.QUIET);

            assertThat(refreshes)
                .isEmpty();
        }
    }

    private Container buildRadio(Loudness fallback) {

        return ControlRows.buildRadio(
            "Loudness",
            PICKS,
            ControlRows.createRememberedPick(KEY, PICKS, fallback, told::add),
            () -> refreshes.add("refreshed"));
    }

    private static List<String> listSelectedLabelsIn(Container row) {

        var selected = new ArrayList<String>();

        for (var button : ComponentTreeFixture.findAll(row, JRadioButton.class)) {
            if (button.isSelected()) {
                selected.add(button.getText());
            }
        }
        return selected;
    }
}
