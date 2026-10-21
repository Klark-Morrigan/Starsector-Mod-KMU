package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.SavedValues;
import kmu.desktop.ui.swing.ComponentTreeFixture;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JSlider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link SettingRows} binds a row to its refresh, over a real {@link SavedValues} file:
 * a change a reader makes refreshes once, building a row refreshes nothing, and a slider runs an
 * expensive refresh when it settles but a cheap one on every movement.
 */
final class SettingRowsIntegrationTests {

    private static final SwitchKnob SWITCH = new SwitchKnob(
        "showCellNames",
        "Cell names",
        false,
        (settings, isOn) -> settings.showCellNames = isOn);

    private static final SliderKnob SLIDER = new SliderKnob(
        "roundingRadius",
        "Corner rounding radius",
        new SliderRows.SliderRange(0, 1000),
        200,
        (settings, radius) -> settings.roundingRadius = radius);

    @TempDir
    private Path folder;

    private final List<String> refreshes = new ArrayList<>();

    private final ViewerSettings settings = new ViewerSettings();

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(folder.resolve("viewer.json"));
    }

    @Nested
    class BuildToggle {

        @Test
        void refreshesNothingWhileBuilt() {

            buildSettlingRows().buildToggle(SWITCH);

            assertThat(refreshes)
                .isEmpty();
        }

        @Test
        void refreshesOncePerClick() {

            var row = buildSettlingRows().buildToggle(SWITCH);

            ComponentTreeFixture.findOnly(row, JCheckBox.class).doClick();

            assertThat(refreshes)
                .containsExactly("refreshed");
            assertThat(settings.showCellNames)
                .isTrue();
        }
    }

    @Nested
    class BuildSlider {

        @Test
        void runsASettlingRefreshOnlyOnceTheHandleIsLetGo() {

            var slider = ComponentTreeFixture.findOnly(buildSettlingRows().buildSlider(SLIDER), JSlider.class);

            slider.setValueIsAdjusting(true);
            slider.setValue(500);

            assertThat(refreshes)
                .isEmpty();

            slider.setValueIsAdjusting(false);

            assertThat(refreshes)
                .containsExactly("refreshed");
            assertThat(settings.roundingRadius)
                .isEqualTo(500);
        }

        @Test
        void runsARepaintingRefreshOnEveryMovement() {

            var slider = ComponentTreeFixture.findOnly(buildRepaintingRows().buildSlider(SLIDER), JSlider.class);

            slider.setValueIsAdjusting(true);
            refreshes.clear();
            slider.setValue(500);
            slider.setValue(600);

            assertThat(refreshes)
                .containsExactly("refreshed", "refreshed");
        }
    }

    private SettingRows buildSettlingRows() {
        return SettingRows.createSettlingRows(settings, () -> refreshes.add("refreshed"));
    }

    private SettingRows buildRepaintingRows() {
        return SettingRows.createRepaintingRows(settings, () -> refreshes.add("refreshed"));
    }
}
