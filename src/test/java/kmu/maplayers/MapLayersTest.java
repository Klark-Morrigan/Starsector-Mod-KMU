package kmu.maplayers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;

import kmu.maplayers.politicalmap.alliances.AlliancesView;
import kmu.maplayers.politicalmap.factions.FactionsView;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the composition root's soft-dependency gate: the alliances view joins the political-map view
 * roster only when Nexerelin is present, and the faction view leads the roster either way. This is
 * the one place a Nex absence must keep the Alliances segment - and the class behind it - off the
 * radio, so the gate is pinned here rather than left to the in-game test alone.
 */
final class MapLayersTest {
    private static final String NEXERELIN_MOD_ID = "nexerelin";

    @Nested
    class SelectPoliticalMapViews {

        @Test
        void selectPoliticalMapViewsAppendsTheAlliancesViewWhenNexIsPresent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, true);

                assertThat(MapLayers.selectPoliticalMapViews())
                        .containsExactly(FactionsView.INSTANCE, AlliancesView.INSTANCE);
            }
        }

        @Test
        void selectPoliticalMapViewsIsFactionOnlyWhenNexIsAbsent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, false);

                assertThat(MapLayers.selectPoliticalMapViews())
                        .containsExactly(FactionsView.INSTANCE);
            }
        }
    }

    private static void stubNexEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {
        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);
        globalMock.when(Global::getSettings).thenReturn(settingsMock);
        when(settingsMock.getModManager()).thenReturn(modManagerMock);
        when(modManagerMock.isModEnabled(NEXERELIN_MOD_ID)).thenReturn(isEnabled);
    }
}
