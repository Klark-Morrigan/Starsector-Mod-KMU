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
 * owner back to the first option, and a remembered name no longer offered falls back to it
 * rather than leaving the group with nothing selected.
 *
 * <p>Against the file rather than a stand-in, because the fault this guards is a row that
 * appears to work for a whole session and remembers nothing - which only the round trip
 * through the store can catch.
 */
final class ControlRowsIntegrationTest {

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

    private SavedValues saved;

    @BeforeEach
    void installTheStore() {

        saved = SavedValues.rememberIn(remembered.resolve("viewer.json"));
    }

    @Nested
    class BuildRadio {

        @Test
        void buildRadioTellsTheOwnerTheFirstOptionWhenNothingIsRemembered() {

            var told = new ArrayList<Loudness>();

            ControlRows.buildRadio(KEY, "Loudness", PICKS, told::add, () -> { });

            assertThat(told)
                .containsExactly(Loudness.QUIET);
        }

        @Test
        void buildRadioOpensOnTheRememberedPick() {

            saved.put(KEY, "DEAFENING");

            var told = new ArrayList<Loudness>();
            var row = ControlRows.buildRadio(KEY, "Loudness", PICKS, told::add, () -> { });

            assertThat(told)
                .containsExactly(Loudness.DEAFENING);
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Deafening");
        }

        @Test
        void buildRadioRemembersAPickUnderTheOptionsOwnName() {

            var told = new ArrayList<Loudness>();
            var refreshes = new ArrayList<String>();
            var row = ControlRows.buildRadio(
                KEY,
                "Loudness",
                PICKS,
                told::add,
                () -> refreshes.add("refreshed"));

            ComponentTreeFixture.findAll(row, JRadioButton.class).get(1).doClick();

            assertThat(told)
                .containsExactly(Loudness.QUIET, Loudness.LOUD);
            assertThat(saved.get(KEY, "nothing"))
                .isEqualTo("LOUD");
            assertThat(refreshes)
                .containsExactly("refreshed");
        }

        @Test
        void buildRadioPutsTheRowAndTheOwnerBackToTheFirstOption() {

            saved.put(KEY, "LOUD");

            var told = new ArrayList<Loudness>();
            var row = ControlRows.buildRadio(KEY, "Loudness", PICKS, told::add, () -> { });

            ComponentTreeFixture.findOnly(row, JButton.class).doClick();

            assertThat(told)
                .containsExactly(Loudness.LOUD, Loudness.QUIET);
            assertThat(saved.get(KEY, "nothing"))
                .isEqualTo("QUIET");
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Quiet");
        }

        @Test
        void buildRadioFallsBackWhereTheRememberedNameIsNoLongerOffered() {

            saved.put(KEY, "INAUDIBLE");

            var told = new ArrayList<Loudness>();
            var row = ControlRows.buildRadio(KEY, "Loudness", PICKS, told::add, () -> { });

            assertThat(told)
                .containsExactly(Loudness.QUIET);
            assertThat(listSelectedLabelsIn(row))
                .containsExactly("Quiet");
        }
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
