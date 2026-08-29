package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.geometry.RingPath;

import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedRibbonRuns;
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
import java.util.Set;

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
 * <p>The gate is the point, and what it reads is inhabitation rather than this layer's holding. A
 * band reports how a system splits, so a cell nobody lives in has nothing for one to say - and the
 * claim mechanic's counting walks a system's whole market list, so a pass that asked it about every
 * empty cell in the sector would pay for a contest nobody is contesting. Both halves are stated
 * here: the empty cell gets no band, and the planner is not even asked. The switch is stated the
 * same way and for the same reason - it has to take the counting off the rebuild, not merely the
 * bands off the map, since a rebuild already stalls elsewhere and a switched-off feature that still
 * walks the sector's markets is a cost with nothing to show for it.
 *
 * <p>A settled system is offered a band whether or not the active layer's holding accounts for it,
 * which is why no case here poses a holder at all: vanilla's claim walk never sees a hidden market
 * or a player-owned one, and gating on what it resolved would leave every pirate haven and player
 * colony uncounted on the one layer that has nothing else to say about them.
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

    private static final String INHABITED_SYSTEM = "corvus";
    private static final String EMPTY_SYSTEM = "empty";
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
        void bakesABandForACellSomethingLivesIn() {
            // No holder is posed anywhere in this suite, so the band offered here is offered on
            // inhabitation alone - which is what an unclaimed pirate haven has and this layer's
            // holding does not give it.
            assertThat(buildFor(INHABITED_SYSTEM).bands())
                .extracting(RibbonBand::colour)
                .containsExactly(BAND_COLOUR);
        }

        @Test
        void drawsNoBandForACellNothingLivesIn() {
            // Nobody is there, so there are no holdings for a band to report how a system splits
            // between.
            assertThat(buildFor(EMPTY_SYSTEM))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void asksNoPlannerAboutACellNothingLivesIn() {
            // The cost half of the gate: the claim mechanic's count walks every market in a
            // system, and most of the sector is empty space.
            var plannerMock = mock(SystemRibbonPlanner.class);

            buildWith(plannerMock)
                .buildCellRibbon(CELL, EMPTY_SYSTEM, SQUARE_CELL, passTimings);

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
                .buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, timingsMock);

            verify(timingsMock).addPlanNanos(anyLong());
        }

        @Test
        void chargesNothingForACellNothingLivesIn() {
            // The gate's cost half, stated as what a gated-out cell adds to the bake: nothing was
            // counted for it, so nothing is charged for it either.
            var timingsMock = mock(RibbonBakeTimings.class);

            buildWith(system -> ANY_PLAN)
                .buildCellRibbon(CELL, EMPTY_SYSTEM, SQUARE_CELL, timingsMock);

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
        void drawsNoBandForASettledSystemWithNoRecordedSite() {
            // The band starts above the cell's own site. With none there is nowhere to start from,
            // which is the same answer as an empty cell rather than a start point invented for it.
            assertThat(buildFor(SITELESS_SYSTEM))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void drawsNoBandForASettledCellWhileTheBandsAreSwitchedOff() {

            switchBandsOff();

            assertThat(buildFor(INHABITED_SYSTEM))
                .isEqualTo(CellRibbon.NONE);
        }

        @Test
        void handsThePlayersUncontestedAnswerToTheMechanicTheViewCountsBy() {
            // The one place the uncontested knob reaches the counting: it is sampled by the pass
            // and carried in the inputs every planner is built from. Read anywhere else, or not
            // read at all, it would show only as a setting a player moves to no effect. Answered
            // off rather than on, which is not the shipped default, so an answer that ignored the
            // setting could not pass.
            settingsMock
                .when(KmuPoliticalMapSettings::shouldShortenPoliticalMapUncontestedRibbonRuns)
                .thenReturn(false);

            var viewMock = buildViewMock(system -> ANY_PLAN);

            buildThrough(viewMock, new CellRingPathCache());

            var inputsCaptor = ArgumentCaptor.forClass(RibbonPlanInputs.class);

            verify(viewMock)
                .resolveRibbonPlanner(inputsCaptor.capture());
            assertThat(inputsCaptor.getValue().rules().uncontestedRuns())
                .isEqualTo(new UncontestedRibbonRuns(false));
        }

        @Test
        void asksNoPlannerAboutASettledCellWhileTheBandsAreSwitchedOff() {
            // The half of the switch that is invisible either way: with the bands off, a rebuild
            // must not still be counting every settled system's colonies for a readout nothing
            // will draw.
            switchBandsOff();

            var plannerMock = mock(SystemRibbonPlanner.class);

            buildWith(plannerMock)
                .buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, passTimings);

            verify(plannerMock, never())
                .planSystemRibbon(any());
        }

        @Test
        void drawsNoBandForACellWhosePlanIsEmpty() {
            // The single-holder cell: the bloc that painted it is the only one present, so there
            // is nothing a band could report that the fill beneath it has not said already.
            assertThat(buildWith(system -> RibbonPlan.NONE)
                    .buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, passTimings))
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
                .buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, timingsMock);

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

            ribbonSource.buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, timingsMock);
            ribbonSource.buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, timingsMock);

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
                    .buildCellRibbon(CELL, INHABITED_SYSTEM, SQUARE_CELL, passTimings)
                    .bands())
                .isNotEmpty();
        }
    }

    @Nested
    class TraceCellRibbonPath {

        @Test
        void tracesAPathForACellSomethingLivesIn() {

            assertThat(traceFor(INHABITED_SYSTEM).verdict())
                .isEqualTo(RibbonPathVerdict.LAID_AT_PAD);
        }

        @Test
        void tracesNoPathForACellNothingLivesIn() {
            // The overlay covers the cells the band pass considered and no more. Traced for every
            // cell instead, it would ring every piece of empty space in the sector and bury the
            // cells it is looked at to explain.
            assertThat(traceFor(EMPTY_SYSTEM))
                .isEqualTo(CellRibbonPath.NONE);
        }

        @Test
        void tracesNoPathForASettledSystemWithNoRecordedSite() {
            // A path opens above the cell's own site, so a system without one has no start to
            // trace from - the same answer the band pass gives it.
            assertThat(traceFor(SITELESS_SYSTEM))
                .isEqualTo(CellRibbonPath.NONE);
        }

        @Test
        void tracesNoPathForASettledCellWhileTheBandsAreSwitchedOff() {
            // With the bands off there is no layout in play, so the overlay has nothing to report
            // on: a ring drawn from sizes nothing is laid at would be a diagnostic of its own
            // arithmetic.
            switchBandsOff();

            assertThat(traceFor(INHABITED_SYSTEM))
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

    // A pass over two settled systems - one placed, one with no site recorded - and one system
    // nobody lives in, counted through the given planner and keeping its traced rings to itself.
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
            buildSystem(INHABITED_SYSTEM),
            buildSystem(EMPTY_SYSTEM),
            buildSystem(SITELESS_SYSTEM));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return CellRibbonSource.createForPass(
            HolderPass.over(sectorMock, ColonyVisibility.BASE_FOG, HolderGrouping.identity()),
            viewMock,
            new RibbonBakeSurface(
                Set.of(INHABITED_SYSTEM, SITELESS_SYSTEM),
                // Only the placed system has a site; the other settled one is what a band with
                // nowhere to start is posed on.
                Map.of(INHABITED_SYSTEM, new double[] {2000.0, 2000.0}),
                // No names anywhere near these cells: where a name falls is pinned by the builder
                // that lays a band inside one cell, not by which cells are offered a band at all.
                List.of(),
                ringPathCache));
    }

    private static StarSystemAPI buildSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        return systemMock;
    }
}
