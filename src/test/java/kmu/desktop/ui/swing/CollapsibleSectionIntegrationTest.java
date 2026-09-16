package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a switched-off section does to the controls under it: the switches grey, the folds
 * do not, and a fold inside one still opens the rows beneath it.
 *
 * <p>The fault guarded is a reader who cannot look. Greying a section's body is one walk that
 * cannot tell a setting from a view, so it is free to take the fold of every tree row inside
 * along with the switches - leaving a branch that can only be opened by switching its overlay
 * on, which is the opposite of what a panel of diagnostics is for.
 *
 * <p>Over a real section holding a real tree rather than a stand-in, because what breaks is the
 * meeting of the two: neither class is wrong on its own.
 */
final class CollapsibleSectionIntegrationTest {

    private static final String SECTION_NAME = "pockets";

    private static final String BRANCH_KEY = "showBranch";
    private static final String LEAF_KEY = "showLeaf";

    // What ToggleTree remembers a row's folded state under, which the suite has to write
    // directly to open on a folded branch.
    private static final String BRANCH_FOLD_KEY = BRANCH_KEY + "Unfolded";

    @TempDir
    private Path store;

    private JPanel body;

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(store.resolve("collapsible-section.json"));
    }

    @Nested
    class BuildSection {

        @Test
        void buildSectionGreysEverySwitchUnderASectionThatIsOff() {

            buildSwitchedOffSection();

            assertThat(ComponentTreeFixture.findAll(body, JCheckBox.class))
                .hasSize(2)
                .allMatch(box -> !box.isEnabled());
        }

        @Test
        void buildSectionLeavesTheFoldsUnderASectionThatIsOffLive() {

            buildSwitchedOffSection();

            assertThat(ComponentTreeFixture.findAll(body, JButton.class))
                .hasSize(1)
                .allMatch(FoldControls::isFoldControl)
                .allMatch(JButton::isEnabled);
        }

        // The point of the whole step: a branch inside a section that is off can still be
        // opened and read. Started folded, so that what the click has to achieve is the thing
        // a reader wants - seeing a row that was not on screen.
        @Test
        void buildSectionLetsAFoldOpenARowWhileTheSectionIsOff() {

            SavedValues.findSavedValues().putBoolean(BRANCH_FOLD_KEY, false);

            buildSwitchedOffSection();

            var leafRow = ComponentTreeFixture.findAll(body, JCheckBox.class).get(1).getParent();

            assertThat(leafRow.isVisible())
                .isFalse();

            ComponentTreeFixture.findOnly(body, JButton.class).doClick();

            assertThat(leafRow.isVisible())
                .isTrue();
        }

        // Sparing the folds is a branch taken mid-walk, and a walk that returns early is one
        // that can stop walking. So the ordinary case is pinned beside it: switching the
        // section back on has to reach every switch under it, fold or no fold.
        @Test
        void buildSectionWakesEverySwitchWhenTheSectionIsSwitchedBackOn() {

            var section = buildSwitchedOffSection();

            ComponentTreeFixture.findAll(section, JCheckBox.class).get(0).doClick();

            assertThat(ComponentTreeFixture.findAll(body, JCheckBox.class))
                .hasSize(2)
                .allMatch(JCheckBox::isEnabled);
        }
    }

    // A section switched off, holding the smallest tree with a fold in it: a branch row and the
    // leaf that row folds away.
    private JPanel buildSwitchedOffSection() {

        body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(ToggleTree.buildToggleTree(
            () -> { },
            ToggleTree.Row.ofSwitch(
                0, new ToggleTree.Switch(BRANCH_KEY, "Branch", true, on -> { })),
            ToggleTree.Row.ofSwitch(
                1, new ToggleTree.Switch(LEAF_KEY, "Leaf", true, on -> { }))));

        return CollapsibleSection.buildSection(
            CollapsibleSection.SectionKeys.forSection(SECTION_NAME),
            "Pockets",
            new CollapsibleSection.MasterSwitch(false, on -> { }, () -> { }),
            body);
    }
}
