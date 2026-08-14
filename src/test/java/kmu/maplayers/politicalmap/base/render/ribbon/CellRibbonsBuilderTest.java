package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins which cells are counted at all, what a cell missing part of its identity does, and what the
 * player's switch takes away when it is off.
 *
 * <p>The gate is the point. A band reports what the fill leaves out, so a cell no bloc paints has
 * nothing for one to say - and the claim mechanic's counting walks a system's whole market list,
 * so a pass that asked it about every empty cell in the sector would pay for a contest nobody is
 * contesting. Both halves are stated here: the unpainted cell gets no band, and the planner is not
 * even asked. The switch is stated the same way and for the same reason - it has to take the
 * counting off the rebuild, not merely the bands off the map, since a rebuild already stalls
 * elsewhere and a switched-off feature that still walks the sector's markets is a cost with
 * nothing to show for it.
 */
final class CellRibbonsBuilderTest {

    private static final String PAINTED_SYSTEM = "corvus";
    private static final String UNPAINTED_SYSTEM = "empty";
    private static final String SITELESS_SYSTEM = "unplaced";

    private static final Color BAND_COLOUR = new Color(140, 160, 220);

    // A plan with a run in it, so a cell that reaches the geometry comes back carrying a band and
    // one that is gated out is told apart by drawing none.
    private static final RibbonPlan ANY_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(BAND_COLOUR, 1)));

    // A cell large enough to hold the shipped band clear of its own border.
    private static final List<double[]> SQUARE_CELL = List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0});

    // The player's knobs stand in for the whole settings class here, so the switch under test is
    // read from a stub rather than from a LunaLib the test JVM has no game to load.
    private MockedStatic<KmuPoliticalMapSettings> settingsMock;

    @BeforeEach
    void stubBandSettings() {

        settingsMock = mockStatic(KmuPoliticalMapSettings.class);

        RibbonSettingsFixtures.stubBandsOnAtShippedSizes(settingsMock);
    }

    @AfterEach
    void releaseBandSettings() {
        settingsMock.close();
    }

    @Nested
    class BuildCellRibbon {

        @Test
        void bakesABandForACellSomeBlocPaints() {

            assertThat(buildFor(PAINTED_SYSTEM).bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BAND_COLOUR);
        }

        @Test
        void drawsNoBandForACellNothingPaints() {
            // An uninhabited cell reports no presence, because presence is what a fill is.
            assertThat(buildFor(UNPAINTED_SYSTEM))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void asksNoPlannerAboutACellNothingPaints() {
            // The cost half of the gate: the claim mechanic's count walks every market in a
            // system, and most of the sector is cells nobody paints.
            var plannerMock = mock(SystemRibbonPlanner.class);

            buildWith(plannerMock).buildCellRibbon(UNPAINTED_SYSTEM, SQUARE_CELL);

            verify(plannerMock, never())
                .planSystemRibbon(any());
        }

        @Test
        void drawsNoBandForACellWithNoStarOfItsOwn() {
            // A cell drawn as no system names nowhere anyone could be living, so the null id is
            // answered rather than used as a key.
            assertThat(buildFor(null))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void drawsNoBandForAPaintedSystemWithNoRecordedSite() {
            // The band starts above the cell's own site. With none there is nowhere to start from,
            // which is the same answer as an unpainted cell rather than a start point invented for
            // it.
            assertThat(buildFor(SITELESS_SYSTEM))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void drawsNoBandForAPaintedCellWhileTheBandsAreSwitchedOff() {
            
            switchBandsOff();

            assertThat(buildFor(PAINTED_SYSTEM))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void asksNoPlannerAboutAPaintedCellWhileTheBandsAreSwitchedOff() {
            // The half of the switch that is invisible either way: with the bands off, a rebuild
            // must not still be counting every painted system's colonies for a readout nothing
            // will draw.
            switchBandsOff();

            var plannerMock = mock(SystemRibbonPlanner.class);

            buildWith(plannerMock).buildCellRibbon(PAINTED_SYSTEM, SQUARE_CELL);

            verify(plannerMock, never())
                .planSystemRibbon(any());
        }
    }

    private CellRibbon buildFor(String drawnSystemId) {
        return buildWith(system -> ANY_PLAN).buildCellRibbon(drawnSystemId, SQUARE_CELL);
    }

    private void switchBandsOff() {
        settingsMock
            .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapRibbons)
            .thenReturn(false);
    }

    // A pass over two painted systems - one placed, one with no site recorded - and one system no
    // bloc paints, counted through the given planner.
    private static CellRibbonsBuilder buildWith(SystemRibbonPlanner planner) {

        // The systems are built before the stubbing rather than inside it: each is itself a mock,
        // and building one while another stubbing is open reads to Mockito as an unfinished stub.
        var systems = List.of(
            buildSystem(PAINTED_SYSTEM),
            buildSystem(UNPAINTED_SYSTEM),
            buildSystem(SITELESS_SYSTEM));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.resolveRibbonPlanner(any(), any(), any()))
            .thenReturn(planner);

        var geometryCacheMock = mock(CellGeometryCache.class);

        // Only the placed system has a site; the other painted one is what a band with nowhere to
        // start is posed on.
        when(geometryCacheMock.getSiteBySystemId())
            .thenReturn(Map.of(PAINTED_SYSTEM, new double[] {2000.0, 2000.0}));

        return CellRibbonsBuilder.createForPass(
            sectorMock,
            new ViewGrouping(viewMock, HolderGrouping.identity()),
            Map.of(
                PAINTED_SYSTEM, buildHolder(),
                SITELESS_SYSTEM, buildHolder()),
            geometryCacheMock);
    }

    private static StarSystemAPI buildSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        return systemMock;
    }

    // Any holder: the gate reads whether a system has one, never which.
    private static DominantHolder buildHolder() {
        return new DominantHolder("hegemony", Color.WHITE, Color.GRAY);
    }
}
