package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.geometry.RingPath;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedCellBands;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.SQUARE_CELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
 *
 * <p>The ring cases are the same question asked once more, of the work rather than of the count: a
 * cell is walked when its path is not already standing, once however often it is baked, and never
 * at all where its plan came back empty. That last one is where the gate has to sit to be worth
 * anything - a cell answered after its ring was walked has already paid the cost the gate exists
 * to save.
 *
 * <p>The diagnostic trace is pinned against the same gate rather than against its own, because
 * what makes the overlay worth looking at is that it answers for exactly the cells the band pass
 * considered. A trace reaching wider would ring cells no band was ever going to be laid on;
 * a trace reaching narrower would fall silent on cells the reader is looking at it to explain.
 */
final class CellRibbonSourceTest {

    private static final String PAINTED_SYSTEM = "corvus";
    private static final String UNPAINTED_SYSTEM = "empty";
    private static final String SITELESS_SYSTEM = "unplaced";

    // The cell a case bakes, which is the key its traced ring is kept under. One cell is enough
    // for every case here: what is pinned is whether a given cell is walked at all, never how two
    // of them are told apart.
    private static final String CELL = "cell";

    // A second cell, for the one case that is about the ring being kept per cell rather than per
    // pass: a path standing for one cell is no answer for another's.
    private static final String OTHER_CELL = "other";

    private static final Color BAND_COLOUR = new Color(140, 160, 220);

    // A plan with a run in it, so a cell that reaches the geometry comes back carrying a band and
    // one that is gated out is told apart by drawing none.
    private static final RibbonPlan ANY_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(BAND_COLOUR, 1)));

    // Where a pass charges what its cells cost it. Handed in and never read back here: which
    // cells are offered a band at all is what these cases pin, not what one takes to bake.
    private final RibbonBakeTimings passTimings = new RibbonBakeTimings();

    // The player's knobs stand in for the whole settings class here, so the switch under test is
    // read from a stub rather than from a LunaLib the test JVM has no game to load.
    private MockedStatic<KmuPoliticalMapSettings> settingsMock;

    // The map-layer knobs stand in for the same reason, the bake opening a pass that samples the
    // dev reveal off them. No case here turns on the reveal, so the stub's own false is the answer.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    @BeforeEach
    void stubBandSettings() {

        settingsMock = mockStatic(KmuPoliticalMapSettings.class);
        mapLayerSettingsMock = mockStatic(KmuMapLayerSettings.class);

        RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);
    }

    @AfterEach
    void releaseBandSettings() {
        mapLayerSettingsMock.close();
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

            buildWith(plannerMock)
                .buildCellRibbon(CELL, UNPAINTED_SYSTEM, SQUARE_CELL, passTimings);

            verify(plannerMock, never())
                .planSystemRibbon(any());
        }

        @Test
        void chargesTheCountToThePassApartFromTheGeometry() {
            // The one phase of a bake that grows with what the systems hold rather than with the
            // cells - the claim mechanic walks a system's whole market list - so it is worth its
            // own number only if it is charged where the count happens rather than swept into the
            // ring work that follows it.
            var timingsMock = mock(RibbonBakeTimings.class);

            buildWith(system -> ANY_PLAN)
                .buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, timingsMock);

            verify(timingsMock).addPlanNanos(anyLong());
        }

        @Test
        void chargesNothingForACellNothingPaints() {
            // The gate's cost half, stated as what a gated-out cell adds to the bake: nothing was
            // counted for it, so nothing is charged for it either.
            var timingsMock = mock(RibbonBakeTimings.class);

            buildWith(system -> ANY_PLAN)
                .buildCellRibbon(CELL, UNPAINTED_SYSTEM, SQUARE_CELL, timingsMock);

            verifyNoInteractions(timingsMock);
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
        void handsThePlayersUncontestedAnswerToTheMechanicTheViewCountsBy() {
            // The one place the two uncontested knobs reach the counting: they are sampled by the
            // pass and carried in the inputs every planner is built from. Read anywhere else, or
            // not read at all, they would show only as a setting a player moves to no effect.
            settingsMock
                .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapUncontestedRibbons)
                .thenReturn(true);
            settingsMock
                .when(KmuPoliticalMapSettings::shouldShortenPoliticalMapUncontestedRibbonRuns)
                .thenReturn(false);

            var viewMock = buildViewMock(system -> ANY_PLAN);

            buildThrough(viewMock, new CellRingPathCache());

            var inputsCaptor = ArgumentCaptor.forClass(RibbonPlanInputs.class);

            verify(viewMock)
                .resolveRibbonPlanner(inputsCaptor.capture());
            assertThat(inputsCaptor.getValue().rules().uncontestedBands())
                .isEqualTo(new UncontestedCellBands(true, false));
        }

        @Test
        void asksNoPlannerAboutAPaintedCellWhileTheBandsAreSwitchedOff() {
            // The half of the switch that is invisible either way: with the bands off, a rebuild
            // must not still be counting every painted system's colonies for a readout nothing
            // will draw.
            switchBandsOff();

            var plannerMock = mock(SystemRibbonPlanner.class);

            buildWith(plannerMock)
                .buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, passTimings);

            verify(plannerMock, never())
                .planSystemRibbon(any());
        }

        @Test
        void drawsNoBandForACellWhosePlanIsEmpty() {
            // The single-holder cell: the bloc that painted it is the only one present, so there
            // is nothing a band could report that the fill beneath it has not said already.
            assertThat(buildWith(system -> RibbonPlan.NONE)
                    .buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, passTimings))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void walksNoRingForACellWhosePlanIsEmpty() {
            // The cost half of that gate, and why it is answered here rather than by the geometry.
            // Most of the sector is single-holder cells, and each of them walking a ring for a
            // band nothing would be laid on is a rebuild's worth of inset and arc-length work
            // spent on nothing - so no ring is walked and none is kept.
            var ringPathCache = new CellRingPathCache();
            var timingsMock = mock(RibbonBakeTimings.class);

            buildCachingInto(system -> RibbonPlan.NONE, ringPathCache)
                .buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, timingsMock);

            verify(timingsMock, never())
                .addTraceNanos(anyLong());
            assertThat(ringPathCache.findRingPathOf(CELL))
                .isNull();
        }

        @Test
        void walksACellsRingOnceHoweverOftenItIsBaked() {
            // What the kept ring is for. A bake runs whenever a cluster name may have moved, which
            // is every colony flip, while the ring a band runs along moves only when its cell is
            // re-shaped - so the second bake of an untouched cell must walk nothing.
            var ringPathCache = new CellRingPathCache();
            var timingsMock = mock(RibbonBakeTimings.class);
            var ribbonSource = buildCachingInto(system -> ANY_PLAN, ringPathCache);

            ribbonSource.buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, timingsMock);
            ribbonSource.buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, timingsMock);

            verify(timingsMock, times(1))
                .addTraceNanos(anyLong());
        }

        @Test
        void walksTheRingOfACellWithNoPathStandingOfItsOwn() {
            // A path describes one cell's shape, so it is kept per cell and not per pass: a cell
            // reached while another's path stands is walked, and comes back with the band its own
            // ring holds rather than with whatever the neighbour's said.
            var ringPathCache = new CellRingPathCache();

            ringPathCache.putRingPath(OTHER_CELL, RingPath.nothingLeftToTrace());

            assertThat(buildCachingInto(system -> ANY_PLAN, ringPathCache)
                    .buildCellRibbon(CELL, PAINTED_SYSTEM, SQUARE_CELL, passTimings)
                    .bands())
                .isNotEmpty();
        }
    }

    @Nested
    class TraceCellRibbonPath {

        @Test
        void tracesAPathForACellSomeBlocPaints() {

            assertThat(traceFor(PAINTED_SYSTEM).verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
        }

        @Test
        void tracesNoPathForACellNothingPaints() {
            // The overlay covers the cells the band pass considered and no more. Traced for every
            // cell instead, it would ring every piece of empty space in the sector and bury the
            // cells it is looked at to explain.
            assertThat(traceFor(UNPAINTED_SYSTEM))
                .isEqualTo(CellRibbonPath.NONE);
        }

        @Test
        void tracesNoPathForAPaintedSystemWithNoRecordedSite() {
            // A path opens above the cell's own site, so a system without one has no start to
            // trace from - the same answer the band pass gives it.
            assertThat(traceFor(SITELESS_SYSTEM))
                .isEqualTo(CellRibbonPath.NONE);
        }

        @Test
        void tracesNoPathForAPaintedCellWhileTheBandsAreSwitchedOff() {
            // With the bands off there is no layout in play, so the overlay has nothing to report
            // on: a ring drawn from sizes nothing is laid at would be a diagnostic of its own
            // arithmetic.
            switchBandsOff();

            assertThat(traceFor(PAINTED_SYSTEM))
                .isEqualTo(CellRibbonPath.NONE);
        }
    }

    private CellRibbon buildFor(String drawnSystemId) {
        return buildWith(system -> ANY_PLAN)
            .buildCellRibbon(CELL, drawnSystemId, SQUARE_CELL, passTimings);
    }

    private CellRibbonPath traceFor(String drawnSystemId) {
        return buildWith(system -> ANY_PLAN).traceCellRibbonPath(drawnSystemId, SQUARE_CELL);
    }

    private void switchBandsOff() {
        settingsMock
            .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapRibbons)
            .thenReturn(false);
    }

    // A pass over two painted systems - one placed, one with no site recorded - and one system no
    // bloc paints, counted through the given planner and keeping its traced rings to itself.
    private static CellRibbonSource buildWith(SystemRibbonPlanner planner) {
        return buildCachingInto(planner, new CellRingPathCache());
    }

    // The same pass writing its traced rings into a store the case holds, for the cases about
    // which cells are walked at all.
    private static CellRibbonSource buildCachingInto(
            SystemRibbonPlanner planner,
            CellRingPathCache ringPathCache) {

        return buildThrough(buildViewMock(planner), ringPathCache);
    }

    // The view the pass asks for its mechanic, answering with the given planner. Built apart from
    // the pass so a case can hold on to it and read what it was handed.
    private static PoliticalMapView buildViewMock(SystemRibbonPlanner planner) {

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.resolveRibbonPlanner(any()))
            .thenReturn(planner);

        return viewMock;
    }

    private static CellRibbonSource buildThrough(
            PoliticalMapView viewMock,
            CellRingPathCache ringPathCache) {

        // The systems are built before the stubbing rather than inside it: each is itself a mock,
        // and building one while another stubbing is open reads to Mockito as an unfinished stub.
        var systems = List.of(
            buildSystem(PAINTED_SYSTEM),
            buildSystem(UNPAINTED_SYSTEM),
            buildSystem(SITELESS_SYSTEM));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return CellRibbonSource.createForPass(
            sectorMock,
            new ViewGrouping(viewMock, HolderGrouping.identity()),
            Map.of(
                PAINTED_SYSTEM, buildHolder(),
                SITELESS_SYSTEM, buildHolder()),
            // Only the placed system has a site; the other painted one is what a band with
            // nowhere to start is posed on.
            Map.of(PAINTED_SYSTEM, new double[] {2000.0, 2000.0}),
            // No names anywhere near these cells: where a name falls is pinned by the builder
            // that lays a band inside one cell, not by which cells are offered a band at all.
            List.of(),
            ringPathCache);
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
