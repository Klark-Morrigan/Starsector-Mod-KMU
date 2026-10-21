package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;
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
import javax.swing.JCheckBox;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ToggleTree} against a real {@link SavedValues} file: a pair of switches behaves as
 * two switches would apart, a roll-up reads and writes the switches it covers in three states,
 * and a fold hides every row indented beneath it while leaving the switches alone.
 *
 * <p>Against the file rather than a stand-in, for the reason the radio's own round trip is:
 * a switch that appears to work for a session and remembers nothing is only caught here.
 */
final class ToggleTreeIntegrationTests {

    private static final String LEFT_KEY = "showLeft";
    private static final String RIGHT_KEY = "showRight";
    private static final String BRANCH_KEY = "showBranch";
    private static final String LEAF_KEY = "showLeaf";
    private static final String DEEP_KEY = "showDeep";

    @TempDir
    private Path store;

    private final List<String> applied = new ArrayList<>();
    private final List<String> changes = new ArrayList<>();

    private boolean isLeftOn;
    private boolean isRightOn;

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(store.resolve("toggle-tree.json"));
    }

    @Nested
    class OfSwitchPair {

        @Test
        void tellsBothOwnersBeforeAnythingDraws() {

            buildPairedTree(true, false);

            assertThat(applied)
                .containsExactly("left=true", "right=false");
            assertThat(isLeftOn)
                .isTrue();
            assertThat(isRightOn)
                .isFalse();
        }

        // The roll-up's own box is a checkbox too, so it is named here: what the pair adds is
        // the two under it, and what makes them a PAIR is that they share a parent.
        @Test
        void drawsBothSwitchesOnOneRow() {

            var tree = buildPairedTree(true, false);
            var boxes = ComponentTreeFixture.findAll(tree, JCheckBox.class);

            assertThat(boxes)
                .extracting(JCheckBox::getText)
                .containsExactly("Both", "Left", "Right");

            assertThat(boxes.get(1).getParent())
                .isSameAs(boxes.get(2).getParent())
                .isNotSameAs(boxes.get(0).getParent());
        }

        @Test
        void opensEachHalfOnWhatWasRemembered() {

            SavedValues.findSavedValues().putBoolean(LEFT_KEY, false);
            SavedValues.findSavedValues().putBoolean(RIGHT_KEY, true);

            buildPairedTree(true, false);

            assertThat(isLeftOn)
                .isFalse();
            assertThat(isRightOn)
                .isTrue();
        }

        @Test
        void writesDownBothHalvesOnAChange() {

            var tree = buildPairedTree(true, false);

            ComponentTreeFixture.findAll(tree, JCheckBox.class).get(2).doClick();

            assertThat(SavedValues.findSavedValues().getBoolean(LEFT_KEY, false))
                .isTrue();
            assertThat(SavedValues.findSavedValues().getBoolean(RIGHT_KEY, false))
                .isTrue();
        }

        // The half a roll-up would forget: a pair is one ROW, and a roll-up that walked rows
        // rather than keys would cover the left switch and miss the right.
        @Test
        void letsARollUpReachTheRightHalf() {

            var tree = buildPairedTree(false, false);
            var rollUp = ComponentTreeFixture.findAll(tree, JCheckBox.class).get(0);

            rollUp.doClick();

            assertThat(isLeftOn)
                .isTrue();
            assertThat(isRightOn)
                .isTrue();
        }
    }

    @Nested
    class OfRollUp {

        @Test
        void isTickedOnlyWhileEverySwitchItCoversIsOn() {

            var boxes = ComponentTreeFixture.findAll(buildPairedTree(true, true), JCheckBox.class);

            assertThat(boxes.get(0).isSelected())
                .isTrue();

            boxes.get(1).doClick();

            assertThat(boxes.get(0).isSelected())
                .isFalse();
        }

        // A mixed set is the third state, and from it the roll-up turns everything ON rather
        // than off: what a reader clicking a part-filled box wants is the rest of it.
        @Test
        void turnsEverySwitchOnFromAMixedSet() {

            var boxes = ComponentTreeFixture.findAll(buildPairedTree(true, false), JCheckBox.class);

            boxes.get(0).doClick();

            assertThat(isLeftOn)
                .isTrue();
            assertThat(isRightOn)
                .isTrue();
            assertThat(boxes.get(0).isSelected())
                .isTrue();
        }

        @Test
        void turnsEverySwitchOffFromAFullSet() {

            var boxes = ComponentTreeFixture.findAll(buildPairedTree(true, true), JCheckBox.class);

            boxes.get(0).doClick();

            assertThat(isLeftOn)
                .isFalse();
            assertThat(isRightOn)
                .isFalse();
        }

        @Test
        void runsTheChangeOncePerClick() {

            var boxes = ComponentTreeFixture.findAll(buildPairedTree(true, false), JCheckBox.class);

            boxes.get(0).doClick();

            assertThat(changes)
                .containsExactly("changed");
        }
    }

    @Nested
    class BuildToggleTree {

        @Test
        void runsNoChangeWhileBuilt() {

            buildPairedTree(true, false);

            assertThat(changes)
                .isEmpty();
        }

        @Test
        void hidesEveryRowBeneathAFoldedBranch() {

            var tree = buildNestedTree();
            var boxes = ComponentTreeFixture.findAll(tree, JCheckBox.class);

            ComponentTreeFixture.findAll(tree, JButton.class).get(0).doClick();

            assertThat(boxes.get(1).getParent().isVisible())
                .isFalse();
            assertThat(boxes.get(2).getParent().isVisible())
                .isFalse();
        }

        // A branch unfolded inside a folded parent stays hidden: what a row shows is read off
        // every fold above it, not only the one directly above.
        @Test
        void keepsARowHiddenWhileAnyFoldAboveItIsShut() {

            var tree = buildNestedTree();
            var folds = ComponentTreeFixture.findAll(tree, JButton.class);
            var deepRow = ComponentTreeFixture.findAll(tree, JCheckBox.class).get(2).getParent();

            folds.get(1).doClick();
            folds.get(0).doClick();
            folds.get(1).doClick();

            assertThat(deepRow.isVisible())
                .isFalse();
        }

        @Test
        void remembersAFoldUnderItsRowsOwnKey() {

            var tree = buildNestedTree();

            ComponentTreeFixture.findAll(tree, JButton.class).get(0).doClick();

            assertThat(SavedValues.findSavedValues().getBoolean(BRANCH_KEY + "Unfolded", true))
                .isFalse();
        }

        @Test
        void leavesTheSwitchesAloneWhenFolding() {

            var tree = buildNestedTree();

            ComponentTreeFixture.findAll(tree, JButton.class).get(0).doClick();

            assertThat(changes)
                .isEmpty();
        }
    }

    // A roll-up over a pair, which is the smallest tree in which a pair can be got wrong.
    private Container buildPairedTree(boolean leftFallback, boolean rightFallback) {

        return ToggleTree.buildToggleTree(
            () -> changes.add("changed"),
            ToggleTree.Row.ofRollUp(0, "both", "Both", LEFT_KEY, RIGHT_KEY),
            ToggleTree.Row.ofSwitchPair(
                1,
                new ToggleTree.Switch("Left", new RememberedValue<>(
                    LEFT_KEY, leftFallback, SavedValueFormat.FLAG, isOn -> {
                        applied.add("left=" + isOn);
                        isLeftOn = isOn;
                    })),
                new ToggleTree.Switch("Right", new RememberedValue<>(
                    RIGHT_KEY, rightFallback, SavedValueFormat.FLAG, isOn -> {
                        applied.add("right=" + isOn);
                        isRightOn = isOn;
                    }))));
    }

    // Three levels, each a branch of the one above, so a fold can be shut above another.
    private Container buildNestedTree() {

        return ToggleTree.buildToggleTree(
            () -> changes.add("changed"),
            ToggleTree.Row.ofSwitch(0, buildSwitch(BRANCH_KEY, "Branch")),
            ToggleTree.Row.ofSwitch(1, buildSwitch(LEAF_KEY, "Leaf")),
            ToggleTree.Row.ofSwitch(2, buildSwitch(DEEP_KEY, "Deep")));
    }

    private static ToggleTree.Switch buildSwitch(String key, String title) {

        return new ToggleTree.Switch(
            title,
            new RememberedValue<>(key, true, SavedValueFormat.FLAG, isOn -> { }));
    }
}
