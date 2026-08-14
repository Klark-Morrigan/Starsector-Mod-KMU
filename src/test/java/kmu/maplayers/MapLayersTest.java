package kmu.maplayers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.tooltip.PoliticalMapCellTooltip;
import kmu.maplayers.politicalmap.claims.ClaimsView;
import kmu.maplayers.politicalmap.dominance.alliances.AlliancesView;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the composition root's soft-dependency gate: the alliances view joins the political-map view
 * roster only when Nexerelin is present, while the faction view leads and the claims view closes the
 * roster either way. This is the one place a Nex absence must keep the Alliances segment - and the
 * class behind it - off the radio without dropping the vanilla claims segment, so the gate is pinned
 * here rather than left to the in-game test alone.
 *
 * <p>The roster is also where what every view owes the player can be held over all of them at once,
 * which is why the hover box each one injects is asserted here rather than view by view: a view added
 * later joins this list, so it is held to the same terms without a case being written for it.
 */
final class MapLayersTest {
    private static final String NEXERELIN_MOD_ID = "nexerelin";

    @Nested
    class SelectPoliticalMapViews {

        @Test
        void selectPoliticalMapViewsPutsTheAlliancesViewBetweenFactionsAndClaimsWhenNexIsPresent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, true);

                // The claims view always closes the roster, so with Nex present it follows the
                // alliances segment rather than displacing it.
                assertThat(MapLayers.selectPoliticalMapViews()).containsExactly(
                        FactionsView.INSTANCE, AlliancesView.INSTANCE, ClaimsView.INSTANCE);
            }
        }

        @Test
        void selectPoliticalMapViewsOffersOnlyViewsWhoseHoverBoxSitsOnTheLayersOwnBase() {
            // What no compiler catches: a view injects its hover box rather than inheriting one, so a
            // view added later could inject a box built straight on the framework's shape. It would
            // then head with the system name alone while the tab beside it names the faction holding
            // the system by decree - one hover answered two ways, a keystroke apart. Read off the
            // roster rather than off a list of views written here, so a view added tomorrow is held
            // to this without anyone remembering to name it.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, true);

                var hoverBoxes = MapLayers.selectPoliticalMapViews()
                    .stream()
                    .map(PoliticalMapView::resolveHoverTooltip)
                    .flatMap(Optional::stream)
                    .toList();

                // A view showing no box at all is legitimate - it heads nothing, so it cannot head it
                // differently - but every box that does exist has to be one of the layer's.
                assertThat(hoverBoxes)
                    .isNotEmpty()
                    .hasOnlyElementsOfType(PoliticalMapCellTooltip.class);
            }
        }

        @Test
        void selectPoliticalMapViewsIsFactionThenClaimsWhenNexIsAbsent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubNexEnabled(globalMock, false);

                // The claims view is vanilla, so it stays on the roster with no Nex; only the
                // alliances segment drops, and claims falls in directly after factions.
                assertThat(MapLayers.selectPoliticalMapViews())
                        .containsExactly(FactionsView.INSTANCE, ClaimsView.INSTANCE);
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
