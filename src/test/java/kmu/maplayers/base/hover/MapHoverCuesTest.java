package kmu.maplayers.base.hover;

import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;

import kmu.settings.KmuMapSoundSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the map answers a new cell with: the typed tick rather than a button's mouseover, at the
 * level the player set, and nothing at all once that level reaches the bottom of its slider. All
 * three fail where nothing on screen shows them - a wrong id is silence, a wrong level is a map that
 * merely feels loud - so the composition is asserted here rather than left to the ear.
 */
final class MapHoverCuesTest {

    // A level that is none of the shipped defaults, so a composition reaching for a constant instead
    // of the player's own slider could not produce it.
    private static final float SET_CELL_ARRIVAL_VOLUME = 0.35f;

    @Nested
    class ComposeCellArrivalCue {

        @Test
        void composeCellArrivalCueTypesTheMapsOwnTickAtTheLevelThePlayerSet() {
            // The sample is the map's and not the sidebar's: a cell is crossed rather than aimed at,
            // and a button's mouseover under the cursor would claim a control the map has none of.
            try (var settingsMock = mockStatic(KmuMapSoundSettings.class)) {

                settingsMock
                    .when(KmuMapSoundSettings::getMapCellArrivalVolume)
                    .thenReturn(SET_CELL_ARRIVAL_VOLUME);

                assertThat(MapHoverCues.composeCellArrivalCue())
                    .isEqualTo(new UiSoundCue(StarsectorUiSound.TEXT_TYPED, 0.35f));
            }
        }

        @Test
        void composeCellArrivalCueNamesNoCueAtAllWhenItsLevelIsSilenced() {
            // Silence stated by naming no cue rather than by handing one over at nothing, the rule the
            // sidebar's moments answer to as well: a sound asked for at zero is still a sound played,
            // and reads as wiring that half worked rather than as a map deliberately quiet.
            try (var settingsMock = mockStatic(KmuMapSoundSettings.class)) {

                settingsMock
                    .when(KmuMapSoundSettings::getMapCellArrivalVolume)
                    .thenReturn(0f);

                assertThat(MapHoverCues.composeCellArrivalCue())
                    .isNull();
            }
        }
    }
}
