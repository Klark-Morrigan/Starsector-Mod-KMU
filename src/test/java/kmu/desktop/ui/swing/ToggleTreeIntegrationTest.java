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

import javax.swing.JCheckBox;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ToggleTree.Row#ofSwitchPair} against a real {@link SavedValues} file.
 *
 * <p>What a pair has to be is two switches that behave exactly as they would apart, drawn on
 * one line. So the fault worth guarding is the half that gets forgotten: a right-hand switch
 * that never tells its owner, is not written down, or is not seen by the roll-up covering it
 * would leave a control that works to the eye and does nothing - and on a block of a dozen
 * nobody would notice which half had gone quiet.
 *
 * <p>Against the file rather than a stand-in, for the reason the radio's own round trip is:
 * a switch that appears to work for a session and remembers nothing is only caught here.
 */
final class ToggleTreeIntegrationTest {

    private static final String LEFT_KEY = "showLeft";
    private static final String RIGHT_KEY = "showRight";

    @TempDir
    private Path store;

    private final List<String> applied = new ArrayList<>();

    private boolean isLeftOn;
    private boolean isRightOn;

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(store.resolve("toggle-tree.json"));
    }

    @Nested
    class OfSwitchPair {

        @Test
        void ofSwitchPairTellsBothOwnersBeforeAnythingDraws() {

            buildPairedTree(true, false);

            assertThat(applied).containsExactly("left=true", "right=false");
            assertThat(isLeftOn).isTrue();
            assertThat(isRightOn).isFalse();
        }

        // The roll-up's own box is a checkbox too, so it is named here: what the pair adds is
        // the two under it, and what makes them a PAIR is that they share a parent.
        @Test
        void ofSwitchPairDrawsBothSwitchesOnOneRow() {

            var tree = buildPairedTree(true, false);
            var boxes = collectCheckBoxes(tree);

            assertThat(boxes)
                .extracting(JCheckBox::getText)
                .containsExactly("Both", "Left", "Right");

            assertThat(boxes.get(1).getParent())
                .isSameAs(boxes.get(2).getParent())
                .isNotSameAs(boxes.get(0).getParent());
        }

        @Test
        void ofSwitchPairOpensEachHalfOnWhatWasRemembered() {

            SavedValues.findSavedValues().putBoolean(LEFT_KEY, false);
            SavedValues.findSavedValues().putBoolean(RIGHT_KEY, true);

            buildPairedTree(true, false);

            assertThat(isLeftOn).isFalse();
            assertThat(isRightOn).isTrue();
        }

        @Test
        void ofSwitchPairWritesDownBothHalves() {

            buildPairedTree(true, false);

            assertThat(SavedValues.findSavedValues().getBoolean(LEFT_KEY, false)).isTrue();
            assertThat(SavedValues.findSavedValues().getBoolean(RIGHT_KEY, true)).isFalse();
        }

        // The half a roll-up would forget: a pair is one ROW, and a roll-up that walked rows
        // rather than keys would cover the left switch and miss the right.
        @Test
        void ofSwitchPairLetsARollUpReachTheRightHalf() {

            var tree = buildPairedTree(false, false);
            var rollUp = collectCheckBoxes(tree).get(0);

            rollUp.doClick();

            assertThat(isLeftOn).isTrue();
            assertThat(isRightOn).isTrue();
        }
    }

    // A roll-up over a pair, which is the smallest tree in which a pair can be got wrong.
    private Container buildPairedTree(boolean leftFallback, boolean rightFallback) {

        return ToggleTree.buildToggleTree(
            () -> { },
            ToggleTree.Row.ofRollUp(0, "both", "Both", LEFT_KEY, RIGHT_KEY),
            ToggleTree.Row.ofSwitchPair(
                1,
                new ToggleTree.Switch(LEFT_KEY, "Left", leftFallback, on -> {
                    applied.add("left=" + on);
                    isLeftOn = on;
                }),
                new ToggleTree.Switch(RIGHT_KEY, "Right", rightFallback, on -> {
                    applied.add("right=" + on);
                    isRightOn = on;
                })));
    }

    // Every checkbox the block drew, in the order it drew them, whatever it nested them in.
    private static List<JCheckBox> collectCheckBoxes(Container root) {

        var found = new ArrayList<JCheckBox>();

        for (var child : root.getComponents()) {

            if (child instanceof JCheckBox box) {
                found.add(box);

            } else if (child instanceof Container nested) {
                found.addAll(collectCheckBoxes(nested));
            }
        }
        return found;
    }
}
