package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what a tab taken off the bar costs: nothing. A hidden layer stays registered - the dialog has
 * to list it to offer it back - and loses its wiring on every sector, which is the half these cases
 * are about.
 *
 * <p>Pins the diff beside it, because the cost of getting it wrong is invisible rather than broken:
 * a walk that acted on every layer whenever the arrangement was written would tear a layer's
 * listeners down and build them again every time the player nudged a tab one place up the column.
 *
 * <p>Every sector, not the loaded one. The arrangement is one preference for every campaign there
 * is, so a sector the walk skipped would keep the wiring of a tab that is no longer on the bar.
 *
 * <p>And the two ends of the range: a layer that states no pair is left alone by both halves, and
 * the switch that takes the whole feature back reaches every layer that was standing whatever the
 * player arranged.
 */
final class MapLayerStandingsTest {

    // The ids the stored arrangement names its layers by, the store holding ids rather than layers.
    private static final String WIRED_LAYER_ID = "wired";
    private static final String OTHER_WIRED_LAYER_ID = "other_wired";
    private static final String PAIRLESS_LAYER_ID = "pairless";

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final SectorAPI otherSectorMock = mock(SectorAPI.class);

    private final MapLayerStanding standingMock = mock(MapLayerStanding.class);
    private final MapLayerStanding otherStandingMock = mock(MapLayerStanding.class);

    private final MapLayer wiredLayerMock = mock(MapLayer.class);
    private final MapLayer otherWiredLayerMock = mock(MapLayer.class);
    private final MapLayer pairlessLayerMock = mock(MapLayer.class);

    @BeforeEach
    void stateWhatEachLayerAnswers() {

        when(wiredLayerMock.getId()).thenReturn(WIRED_LAYER_ID);
        when(wiredLayerMock.resolveStanding()).thenReturn(standingMock);

        when(otherWiredLayerMock.getId()).thenReturn(OTHER_WIRED_LAYER_ID);
        when(otherWiredLayerMock.resolveStanding()).thenReturn(otherStandingMock);

        // The layer with no sector wiring at all, which is what an always-standing tab looks like.
        when(pairlessLayerMock.getId()).thenReturn(PAIRLESS_LAYER_ID);
        when(pairlessLayerMock.resolveStanding()).thenReturn(null);
    }

    @AfterEach
    void restoreTheRosterThisCaseReplaced() {
        // The registry is static, so a roster of stand-ins would otherwise outlive its case.
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @AfterEach
    void forgetTheArrangementThisCaseMade() {
        // The holder is static too, so a bar arranged here would otherwise reorder every later row.
        MapLayerArrangements.forgetTheArrangement();
    }

    @AfterEach
    void clearEveryInstallationThisCaseMade() {
        // The index is process-wide, so a sector installed here would otherwise be one every later
        // walk over every installed sector acted on.
        MapLayerInstallations.disposeEveryInstallation();
    }

    @Nested
    class ApplyArrangementTo {

        @Test
        void applyArrangementToStandsALayerOnTheBarUpOnTheLoadedSector() {

            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            MapLayerInstallations.installMachineryOn(sectorMock);

            MapLayerStandings.applyArrangementTo(sectorMock);

            verify(standingMock).standLayerUpOn(sectorMock);
        }

        @Test
        void applyArrangementToNeverStandsALayerHiddenInTheStoreUp() {
            // The whole point of the hidden set having teeth: a tab the player took off the bar in a
            // past session does not start listening again at the next load, having nowhere to be
            // looked at from.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of(WIRED_LAYER_ID));
            MapLayerInstallations.installMachineryOn(sectorMock);

            MapLayerStandings.applyArrangementTo(sectorMock);

            verify(standingMock, never()).standLayerUpOn(sectorMock);
        }

        @Test
        void applyArrangementToStandsALayerUpOnceForOneSector() {
            // Asked twice over an unmoved bar - a settings change, a second load path - and the
            // second ask is worth nothing. Standing up what already stands is what would drop a
            // layer's caches and re-register its listeners for no reason the player could see.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            MapLayerInstallations.installMachineryOn(sectorMock);

            MapLayerStandings.applyArrangementTo(sectorMock);
            MapLayerStandings.applyArrangementTo(sectorMock);

            verify(standingMock, times(1)).standLayerUpOn(sectorMock);
        }

        @Test
        void applyArrangementToLeavesALayerThatStatesNoStandingPairAlone() {
            // A layer with no sector wiring is simply always standing. There is no half to run in
            // either direction, and a walk that assumed one would take every layer after it in the
            // row down with the exception.
            MapLayerRosters.replaceRosterWith(pairlessLayerMock, wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of(PAIRLESS_LAYER_ID));
            MapLayerInstallations.installMachineryOn(sectorMock);

            MapLayerStandings.applyArrangementTo(sectorMock);

            verify(standingMock).standLayerUpOn(sectorMock);
        }

        @Test
        void applyArrangementToLeavesASectorWithNothingInstalledAlone() {
            // The switch can be flipped with no game loaded, and a load with the overlay off installs
            // nothing. There is no sector to stand up on, and a standing recorded against the holder
            // every sector-less caller shares would be inherited by the next real one.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());

            MapLayerStandings.applyArrangementTo(sectorMock);

            verify(standingMock, never()).standLayerUpOn(sectorMock);
        }

        @Test
        void applyArrangementToStandsTheRestOfTheRowUpPastALayerThatThrows() {
            // An install carrying another mod's layer calls a stranger's code here, and a layer that
            // throws on the way up is not a reason for the tabs after it to go unwired.
            doThrow(new IllegalStateException("stand-up refused"))
                .when(standingMock).standLayerUpOn(sectorMock);

            MapLayerRosters.replaceRosterWith(wiredLayerMock, otherWiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            MapLayerInstallations.installMachineryOn(sectorMock);

            assertThatCode(() -> MapLayerStandings.applyArrangementTo(sectorMock))
                .doesNotThrowAnyException();

            verify(otherStandingMock).standLayerUpOn(sectorMock);
        }
    }

    @Nested
    class ApplyArrangementWhereverInstalled {

        @Test
        void applyArrangementWhereverInstalledStandsAHiddenLayerDownOnEverySector() {
            // The bar is one preference for every campaign, so a tab taken off it stops costing
            // wherever the player goes rather than only on the sector they were looking at.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            MapLayerInstallations.installMachineryOn(sectorMock);
            MapLayerInstallations.installMachineryOn(otherSectorMock);

            MapLayerStandings.applyArrangementWhereverInstalled();
            MapLayerArrangements.arrangeBarWith(List.of(), List.of(WIRED_LAYER_ID));
            MapLayerStandings.applyArrangementWhereverInstalled();

            verify(standingMock).standLayerDownFrom(sectorMock);
            verify(standingMock).standLayerDownFrom(otherSectorMock);
        }

        @Test
        void applyArrangementWhereverInstalledStandsALayerPutBackOnTheBarUpAgain() {
            // The way back has to work, the dialog being the only place a hidden tab returns from.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of(WIRED_LAYER_ID));
            MapLayerInstallations.installMachineryOn(sectorMock);

            MapLayerStandings.applyArrangementWhereverInstalled();
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            MapLayerStandings.applyArrangementWhereverInstalled();

            verify(standingMock).standLayerUpOn(sectorMock);
        }

        @Test
        void applyArrangementWhereverInstalledStandsNothingUpOrDownForAReorder() {
            // A move writes the whole arrangement back, so the second walk sees the same hidden set
            // in a different order. Nothing has moved between shown and hidden, and a layer torn
            // down and rebuilt by a drag is the cost this diff exists to refuse.
            MapLayerRosters.replaceRosterWith(wiredLayerMock, otherWiredLayerMock);
            MapLayerArrangements.arrangeBarWith(
                List.of(WIRED_LAYER_ID, OTHER_WIRED_LAYER_ID),
                List.of());
            MapLayerInstallations.installMachineryOn(sectorMock);

            MapLayerStandings.applyArrangementWhereverInstalled();
            MapLayerArrangements.arrangeBarWith(
                List.of(OTHER_WIRED_LAYER_ID, WIRED_LAYER_ID),
                List.of());
            MapLayerStandings.applyArrangementWhereverInstalled();

            verify(standingMock, times(1)).standLayerUpOn(sectorMock);
            verify(standingMock, never()).standLayerDownFrom(sectorMock);
        }
    }

    @Nested
    class StandEveryLayerDownFrom {

        @Test
        void standEveryLayerDownFromTakesAStandingLayerDownWhateverTheBarSays() {
            // The switch that turns the map layers off decides nothing about the bar: it ends every
            // layer at once, and it is the path a player uninstalls the mod from a save through.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of());
            MapLayerInstallations.installMachineryOn(sectorMock);
            MapLayerStandings.applyArrangementTo(sectorMock);

            MapLayerStandings.standEveryLayerDownFrom(sectorMock);

            verify(standingMock).standLayerDownFrom(sectorMock);
        }

        @Test
        void standEveryLayerDownFromLeavesALayerThatNeverStoodAlone() {
            // Nothing was registered for a hidden layer, so there is nothing to take back - and a
            // stand-down aimed at it would have a layer's own idempotence carrying the mistake.
            MapLayerRosters.replaceRosterWith(wiredLayerMock);
            MapLayerArrangements.arrangeBarWith(List.of(), List.of(WIRED_LAYER_ID));
            MapLayerInstallations.installMachineryOn(sectorMock);
            MapLayerStandings.applyArrangementTo(sectorMock);

            MapLayerStandings.standEveryLayerDownFrom(sectorMock);

            verify(standingMock, never()).standLayerDownFrom(sectorMock);
        }
    }
}
