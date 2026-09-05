package kmu.maplayers.base.chrome.arrange;

import kmu.maplayers.base.layer.MapLayerArrangements;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the refusals, which are the whole of what this dialog can be asked outside a running game -
 * everything past them stands a vanilla panel in a core UI that only exists while the game is up.
 *
 * <p>They are worth pinning for that same reason. Each is a path that ends with the screen untouched,
 * and each would otherwise be discovered by a player watching a dialog half-open over a bar it cannot
 * write to - a store that was never bound, or a core UI the reach could not find.
 *
 * <p>And that a dialog which is down says so. Two map-side gates read that answer every frame to decide
 * whether to stand down, so a dialog reporting itself up from rest would take the map's hover and the
 * sidebar with it for the whole session.
 */
final class MapLayerArrangementDialogTest {

    private final MapLayerArrangementDialog dialog = MapLayerArrangementDialog.INSTANCE;

    @BeforeEach
    void bindNoArrangementStore() {
        // The reading an install gives before its composition root has run, which is the state every
        // refusal here is about.
        MapLayerArrangements.forgetTheArrangement();
    }

    @AfterEach
    void leaveNoArrangementStoreBehind() {
        MapLayerArrangements.forgetTheArrangement();
    }

    @Nested
    class IsDialogRaised {

        @Test
        void isDialogRaisedIsFalseWithNothingHavingOpenedIt() {

            assertThat(dialog.isDialogRaised())
                .isFalse();
        }
    }

    @Nested
    class OpenDialog {

        @Test
        void openDialogStandsNothingUpWithNoStoreToRecordTo() {
            // Refused before anything is built, which is what keeps it testable at all: a dialog that
            // stood a panel up first and then found nowhere to write would need a running game to say so.
            dialog.openDialog();

            assertThat(dialog.isDialogRaised())
                .isFalse();
        }
    }

    @Nested
    class CloseDialog {

        @Test
        void closeDialogDoesNothingWithTheDialogAlreadyDown() {
            // Every way the dialog can end says the same thing, so closing one that is already closed
            // has to be the ordinary case rather than a throw out of a render pass.
            assertThatCode(dialog::closeDialog)
                .doesNotThrowAnyException();

            assertThat(dialog.isDialogRaised())
                .isFalse();
        }
    }
}
