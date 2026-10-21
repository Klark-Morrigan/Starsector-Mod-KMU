package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.SavedValues;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link SavedViewerSettings} reading a saved-values file the way the window's rows open on
 * it: each remembered value lands in its field in the field's own units, and with nothing saved
 * every knob opens on the default the settings themselves declare.
 */
final class SavedViewerSettingsIntegrationTests {

    private static final double SHARE_TOLERANCE = 1e-12;

    @TempDir
    private Path folder;

    @Nested
    class ReadSavedSettingsIn {

        // Two declarations of where a setting starts - the knob's fallback and the field's own
        // initial value - and a drift between them is a window that opens on one map and a
        // probe that reads another. The void's inset rule did exactly that.
        @Test
        void opensEveryKnobOnTheFieldsOwnDefault() throws IllegalAccessException {

            var read = SavedViewerSettings.readSavedSettingsIn(folder.resolve("empty.json"));
            var declared = new ViewerSettings();

            for (var field : ViewerSettings.class.getFields()) {

                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                assertThat(field.get(read))
                    .as(field.getName())
                    .isEqualTo(field.get(declared));
            }
        }

        @Test
        void opensTheVoidInsetOnNowhere() {

            var read = SavedViewerSettings.readSavedSettingsIn(folder.resolve("empty.json"));

            assertThat(read.voidInsetRule)
                .isEqualTo(EdgeInsetRule.NOWHERE);
        }

        @Test
        void readsARememberedPickByItsName() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).put("voidInsetRule", "EVERYWHERE");

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).voidInsetRule)
                .isEqualTo(EdgeInsetRule.EVERYWHERE);
        }

        @Test
        void roundsAWholeCountToTheNearest() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).putDouble("roundingSegments", 4.6);

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).roundingSegments)
                .isEqualTo(5);
        }

        @Test
        void roundsAnOpacityToTheNearest() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).putDouble("ownedCellOpacity", 100.7);

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).ownedCellOpacity)
                .isEqualTo(101);
        }

        @Test
        void readsAPercentageAsTheShareItStandsFor() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).putDouble("continentLeastFrontage", 10);

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).continentMinFrontageShare)
                .isCloseTo(0.1, within(SHARE_TOLERANCE));
        }

        @Test
        void readsASteppedReachInCellRadii() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).putDouble("bridgeReachMultiple", 350);

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).bridgeReachMultiple)
                .isEqualTo(3.5);
        }

        @Test
        void readsAPairsHalvesUnderTheirOwnKeys() {

            var file = folder.resolve("saved.json");
            var saved = SavedValues.rememberIn(file);

            saved.putInt("ownedCell fill", 0xff102030);
            saved.putInt("ownedCell outline", 0xff405060);

            var read = SavedViewerSettings.readSavedSettingsIn(file);

            assertThat(read.ownedCellColour)
                .isEqualTo(new Color(0x10, 0x20, 0x30));
            assertThat(read.ownedCellEdge)
                .isEqualTo(new Color(0x40, 0x50, 0x60));
        }

        @Test
        void readsASwitchUnderTheKeySavedWindowsHold() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).putBoolean("showBareVoid", false);

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).showVoidPiecesV4)
                .isFalse();
        }

        @Test
        void readsASectionsMasterSwitch() {

            var file = folder.resolve("saved.json");

            SavedValues.rememberIn(file).putBoolean("continentVoidShown", false);

            assertThat(SavedViewerSettings.readSavedSettingsIn(file).showContinentVoid)
                .isFalse();
        }
    }
}
