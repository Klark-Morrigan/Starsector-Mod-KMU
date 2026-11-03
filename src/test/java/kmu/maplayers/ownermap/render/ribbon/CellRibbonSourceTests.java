package kmu.maplayers.ownermap.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.profiling.IterationScope;
import kmlib.profiling.SilentProfiler;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.ribbon.CellRibbon;
import kmu.maplayers.base.ribbon.CellRingPathCache;
import kmu.maplayers.base.ribbon.RibbonBakePhases;
import kmu.maplayers.base.ribbon.RibbonBakeTally;
import kmu.maplayers.base.ribbon.RibbonBand;
import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegment;
import kmu.maplayers.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.base.ribbon.overlay.RibbonPathVerdict;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.SectorWalk;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.SQUARE_CELL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins which cells are counted at all, and what a cell missing part of its identity does.
 *
 * <p>The gate is the point, and what it reads is inhabitation rather than this layer's holding. A
 * band reports how a system splits, so a cell nobody lives in has nothing for one to say - and the
 * counting of a mechanic that weighs a system's economy walks every settlement in it, so a pass that asked
 * it about every empty cell in the sector would pay for a contest nobody is contesting. Both halves
 * are stated here: the empty cell gets no band, and the planner is not even asked. The player's
 * switch is the baker's to answer, since a pass with the bands off builds no source at all.
 *
 * <p>A settled system is offered a band whether or not the active layer's holding accounts for it,
 * which is why no case here poses an owner at all: an owner rule that admits only some settlements
 * leaves settled systems unheld, and gating on what it resolved would leave them uncounted on the
 * one layer that has nothing else to say about them.
 *
 * <p>The pass cases are about the pass rather than about any one cell: the mechanic a band is
 * counted by is asked of the owner source once, before the first cell is counted, so two cells in
 * one bake can never be counted against two samplings of it.
 *
 * <p>The diagnostic trace is pinned against the same gate rather than against its own, because
 * what makes the overlay worth looking at is that it answers for exactly the cells the band pass
 * considered. A trace reaching wider would ring cells no band was ever going to be laid on;
 * a trace reaching narrower would fall silent on cells the reader is looking at it to explain.
 */
final class CellRibbonSourceTests {

    private static final String INHABITED_SYSTEM = "corvus";
    private static final String EMPTY_SYSTEM = "empty";
    private static final String SITELESS_SYSTEM = "unplaced";

    // A settled, placed system the sector no longer lists - what a cell keeps standing for between a
    // system leaving the sector and the next rebuild cutting the cells again.
    private static final String DELISTED_SYSTEM = "delisted";

    // A settled system the sector states an anchor about, for the one case about the address the
    // gate is asked in: its key carries an arm the inhabited set knows nothing of, so a gate read
    // under the key alone would refuse it.
    private static final String ANCHORED_SYSTEM_ID = "deep space";
    private static final String ANCHORED_SYSTEM_ANCHOR_ID = "8b3";
    private static final SystemKey ANCHORED_SYSTEM =
        new SystemKey(ANCHORED_SYSTEM_ID, null, ANCHORED_SYSTEM_ANCHOR_ID);

    // The cell a case bakes, which is the key its traced ring is kept under. One cell is enough
    // for every case here: what is pinned is whether a given cell is walked at all, never how two
    // of them are told apart.
    private static final SystemKey CELL = buildCellKey("cell");

    // A second cell, for the one case that is about the ring being kept per cell rather than per
    // pass: a path standing for one cell is no answer for another's.
    private static final SystemKey OTHER_CELL = buildCellKey("other");

    private static final Color BAND_COLOUR = new Color(140, 160, 220);

    // A plan with a segment in it, so a cell that reaches the geometry comes back carrying a band and
    // one that is gated out is told apart by drawing none.
    private static final RibbonPlan ANY_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(BAND_COLOUR, 1)));

    // The sizes and laying rules the pass is handed: sizes a band draws at, which is all any case
    // here needs of them.
    private static final RibbonBakeSettings SETTINGS = RibbonSettingsFixtures.createSettingsDrawingBands();

    // Where a pass marks what its cells cost it. Handed in and never read back here: which cells
    // are offered a band at all is what these cases pin, not what one takes to bake. The silent
    // profiler's scope rather than a mock, since nothing here is asked of it.
    private final IterationScope passScope =
        SilentProfiler.INSTANCE.openIterations(RibbonBakePhases.BAKE_SECTION);

    // The bake call's counts, read back by the case about which cells reach them.
    private final RibbonBakeTally bakeTally = new RibbonBakeTally();

    @Nested
    class BuildCellRibbon {

        // No owner is posed anywhere in this suite, so the band offered here is offered on
        // inhabitation alone - which is what a settled system has even where this layer's
        // holding gives it to nobody.
        @Test
        void bakesABandForACellSomethingLivesIn() {

            // ARRANGE
            var expectedColour = BAND_COLOUR;

            // ACT
            var actualRibbon = buildFor(INHABITED_SYSTEM);

            // ASSERT
            assertThat(actualRibbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(expectedColour);
        }

        // The join with the holding: the cell's system arrives keyed and the inhabited set
        // names a system by ID, so a system whose key states an anchor arm still passes the
        // gate and is offered a band.
        @Test
        void narrowsTheDrawnSystemToTheIdTheInhabitedSetIsKeyedBy() {

            // ARRANGE
            var ribbonSource = buildWith(system -> ANY_PLAN);
            var expectedColour = BAND_COLOUR;

            // ACT
            var actualRibbon = ribbonSource.buildCellRibbon(
                CELL,
                ANCHORED_SYSTEM,
                SQUARE_CELL,
                passScope,
                bakeTally);

            // ASSERT
            assertThat(actualRibbon.bands())
                .extracting(RibbonBand::colour)
                .containsExactly(expectedColour);
        }

        // Nobody is there, so there are no stakes for a band to report how a system splits
        // between.
        @Test
        void drawsNoBandForACellNothingLivesIn() {

            // ARRANGE
            var expectedRibbon = CellRibbon.NONE;

            // ACT
            var actualRibbon = buildFor(EMPTY_SYSTEM);

            // ASSERT
            assertThat(actualRibbon)
                .isEqualTo(expectedRibbon);
        }

        // The cost half of the gate: a mechanic that weighs a system's economy walks every
        // settlement in it, and most of the sector is empty space.
        @Test
        void asksNoPlannerAboutACellNothingLivesIn() {

            // ARRANGE
            var plannerMock = mock(SystemRibbonPlanner.class);
            var ribbonSource = buildWith(plannerMock);

            // ACT
            ribbonSource.buildCellRibbon(CELL, buildCellKey(EMPTY_SYSTEM), SQUARE_CELL, passScope, bakeTally);

            // ASSERT
            verify(plannerMock, never())
                .planSystemRibbon(any());
        }

        // The one phase of a bake that grows with what the systems hold rather than with the
        // cells - a mechanic that weighs a system's economy walks every settlement in it - so it is worth
        // its own number only if it is marked where the count ends rather than swept into the ring
        // work that follows it.
        @Test
        void marksTheCountOnThePassApartFromTheGeometry() {

            // ARRANGE
            var bakeScopeMock = mock(IterationScope.class);
            var ribbonSource = buildWith(system -> ANY_PLAN);
            var expectedPhase = RibbonBakePhases.PLAN_PHASE;

            // ACT
            ribbonSource.buildCellRibbon(CELL, buildCellKey(INHABITED_SYSTEM), SQUARE_CELL, bakeScopeMock, bakeTally);

            // ASSERT
            verify(bakeScopeMock).markPhase(expectedPhase);
        }

        // The gate's cost half, stated as what a gated-out cell adds to the bake: nothing was
        // counted for it, so no phase of it is marked either.
        @Test
        void marksNothingForACellNothingLivesIn() {

            // ARRANGE
            var bakeScopeMock = mock(IterationScope.class);
            var ribbonSource = buildWith(system -> ANY_PLAN);

            // ACT
            ribbonSource.buildCellRibbon(CELL, buildCellKey(EMPTY_SYSTEM), SQUARE_CELL, bakeScopeMock, bakeTally);

            // ASSERT
            verifyNoInteractions(bakeScopeMock);
        }

        // A cell refused at the gate was never offered a band, so it is none of the band's counts:
        // counted as unplanned it would read as a layer finding nothing in a system nobody lives in.
        @Test
        void leavesACellNothingLivesInOutOfTheTally() {

            // ARRANGE
            var expectedTally = "banded=0 traced=0 reused=0 unplanned=0 refusedForRoom=0";

            // ACT
            buildFor(EMPTY_SYSTEM);

            // ASSERT
            var actualTally = bakeTally.describeTally();

            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }

        // A cell drawn as no system names nowhere anyone could be living, so the null ID is
        // answered rather than used as a key.
        @Test
        void drawsNoBandForACellWithNoStarOfItsOwn() {

            // ARRANGE
            var expectedRibbon = CellRibbon.NONE;

            // ACT
            var actualRibbon = buildFor(null);

            // ASSERT
            assertThat(actualRibbon)
                .isEqualTo(expectedRibbon);
        }

        // The band starts above the cell's own site. With none there is nowhere to start from,
        // which is the same answer as an empty cell rather than a start point invented for it.
        @Test
        void drawsNoBandForASettledSystemWithNoRecordedSite() {

            // ARRANGE
            var expectedRibbon = CellRibbon.NONE;

            // ACT
            var actualRibbon = buildFor(SITELESS_SYSTEM);

            // ASSERT
            assertThat(actualRibbon)
                .isEqualTo(expectedRibbon);
        }

        // Nothing is left to count, so the cell takes the unpainted cell's answer rather than a
        // band counted off a stand-in for the system that went.
        @Test
        void drawsNoBandForASettledSystemTheSectorNoLongerLists() {

            // ARRANGE
            var expectedRibbon = CellRibbon.NONE;

            // ACT
            var actualRibbon = buildFor(DELISTED_SYSTEM);

            // ASSERT
            assertThat(actualRibbon)
                .isEqualTo(expectedRibbon);
        }

        // A planner handed a system the sector has let go of would count a footprint nothing
        // else on the map still reads, so the refusal comes before the count.
        @Test
        void asksNoPlannerAboutASettledSystemTheSectorNoLongerLists() {

            // ARRANGE
            var plannerMock = mock(SystemRibbonPlanner.class);
            var ribbonSource = buildWith(plannerMock);

            // ACT
            ribbonSource.buildCellRibbon(CELL, buildCellKey(DELISTED_SYSTEM), SQUARE_CELL, passScope, bakeTally);

            // ASSERT
            verify(plannerMock, never())
                .planSystemRibbon(any());
        }

        // A settled cell where nothing the layer counts is present, so there is no footprint
        // for a band to report.
        @Test
        void drawsNoBandForACellWhosePlanIsEmpty() {

            // ARRANGE
            var ribbonSource = buildWith(system -> RibbonPlan.NONE);
            var expectedRibbon = CellRibbon.NONE;

            // ACT
            var actualRibbon = ribbonSource.buildCellRibbon(
                CELL,
                buildCellKey(INHABITED_SYSTEM),
                SQUARE_CELL,
                passScope,
                bakeTally);

            // ASSERT
            assertThat(actualRibbon)
                .isEqualTo(expectedRibbon);
        }
    }

    @Nested
    class TraceCellRibbonPath {

        @Test
        void tracesAPathForACellSomethingLivesIn() {

            // ARRANGE
            var expectedVerdict = RibbonPathVerdict.LAID_AT_PAD;

            // ACT
            var actualPath = traceFor(INHABITED_SYSTEM);

            // ASSERT
            assertThat(actualPath.verdict())
                .isEqualTo(expectedVerdict);
        }

        // The overlay covers the cells the band pass considered and no more. Traced for every
        // cell instead, it would ring every piece of empty space in the sector and bury the
        // cells it is looked at to explain.
        @Test
        void tracesNoPathForACellNothingLivesIn() {

            // ARRANGE
            var expectedPath = CellRibbonPath.NONE;

            // ACT
            var actualPath = traceFor(EMPTY_SYSTEM);

            // ASSERT
            assertThat(actualPath)
                .isEqualTo(expectedPath);
        }

        // A path opens above the cell's own site, so a system without one has no start to
        // trace from - the same answer the band pass gives it.
        @Test
        void tracesNoPathForASettledSystemWithNoRecordedSite() {

            // ARRANGE
            var expectedPath = CellRibbonPath.NONE;

            // ACT
            var actualPath = traceFor(SITELESS_SYSTEM);

            // ASSERT
            assertThat(actualPath)
                .isEqualTo(expectedPath);
        }
    }

    @Nested
    class CreateForPass {

        // The one place the pass's laying rules reach the counting: they are carried into the
        // planner every cell is counted by. Handed anything else, the uncontested knob would show
        // only as a setting a player moves to no effect.
        @Test
        void handsThePassesOwnPlanRulesToTheSourcesMechanic() {

            // ARRANGE
            var sourceFake = buildSourcePlanning(system -> ANY_PLAN);
            var expectedPlanRules = SETTINGS.planRules();

            // ACT
            buildThrough(sourceFake);

            // ASSERT
            var actualPlanRules = sourceFake.readRibbonPlanRules();

            assertThat(actualPlanRules)
                .singleElement()
                .isSameAs(expectedPlanRules);
        }

        // The bake counts off the walk its caller opened - the rebuild's own in the same frame,
        // a batch's on its own cadence - so a planner asked over any other would count a sector
        // the cells were not cut from.
        @Test
        void asksTheSourceForItsMechanicOverTheHandedWalk() {

            // ARRANGE
            var sourceFake = buildSourcePlanning(system -> ANY_PLAN);
            var expectedWalk = buildWalkOverTheStandingSystems();

            // ACT
            CellRibbonSource.createForPass(
                expectedWalk,
                sourceFake,
                buildSurface(),
                SETTINGS.ribbonStyle(),
                SETTINGS.planRules());

            // ASSERT
            var actualPlannerWalks = sourceFake.readRibbonPlannerWalks();

            assertThat(actualPlannerWalks)
                .singleElement()
                .isSameAs(expectedWalk);
        }

        // Sampled with the sizes and the laying rules, and for the same reason: a mechanic
        // whose inputs moved while a bake was running would otherwise leave one cell banded as a
        // contest between two owners and the cell beside it banded as their joint holding.
        @Test
        void asksTheSourceForItsMechanicOnceHoweverManyCellsAreBaked() {

            // ARRANGE
            var sourceFake = buildSourcePlanning(system -> ANY_PLAN);
            var ribbonSource = buildThrough(sourceFake);
            var expectedPlannerWalkCount = 1;

            // ACT
            ribbonSource.buildCellRibbon(
                CELL,
                buildCellKey(INHABITED_SYSTEM),
                SQUARE_CELL,
                passScope,
                bakeTally);

            ribbonSource.buildCellRibbon(
                OTHER_CELL,
                buildCellKey(INHABITED_SYSTEM),
                SQUARE_CELL,
                passScope,
                bakeTally);

            // ASSERT
            var actualPlannerWalks = sourceFake.readRibbonPlannerWalks();

            assertThat(actualPlannerWalks)
                .hasSize(expectedPlannerWalkCount);
        }
    }

    private CellRibbon buildFor(String drawnSystemId) {

        return buildWith(system -> ANY_PLAN)
            .buildCellRibbon(CELL, buildCellKey(drawnSystemId), SQUARE_CELL, passScope, bakeTally);
    }

    private CellRibbonPath traceFor(String drawnSystemId) {

        return buildWith(system -> ANY_PLAN)
            .traceCellRibbonPath(CELL, buildCellKey(drawnSystemId), SQUARE_CELL);
    }

    // The standing pass counted through the given planner, keeping its traced rings to itself.
    private static CellRibbonSource buildWith(SystemRibbonPlanner planner) {
        return buildThrough(buildSourcePlanning(planner));
    }

    // The source the pass asks for its mechanic, answering with the given planner. Built apart from
    // the pass so a case can hold on to it and read what it was asked.
    private static OwnerSourceFake buildSourcePlanning(SystemRibbonPlanner planner) {

        var sourceFake = new OwnerSourceFake();

        sourceFake.answerRibbonPlanner(planner);

        return sourceFake;
    }

    private static CellRibbonSource buildThrough(OwnerSource ownerSource) {

        return CellRibbonSource.createForPass(
            buildWalkOverTheStandingSystems(),
            ownerSource,
            buildSurface(),
            SETTINGS.ribbonStyle(),
            SETTINGS.planRules());
    }

    // Two settled systems - one placed, one with no site recorded - and one system nobody lives in.
    // The delisted system is left out on purpose: the surface still holds it, the sector does not.
    private static SectorWalk buildWalkOverTheStandingSystems() {

        // The systems are built before the stubbing rather than inside it: each is itself a mock,
        // and building one while another stubbing is open reads to Mockito as an unfinished stub.
        var systems = List.of(
            buildSystem(INHABITED_SYSTEM),
            buildSystem(EMPTY_SYSTEM),
            buildSystem(SITELESS_SYSTEM),
            StarSystemFixture.buildKeyedSystem(
                ANCHORED_SYSTEM_ID,
                null,
                ANCHORED_SYSTEM_ANCHOR_ID));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return new SectorWalk(new SectorPassIndex(sectorMock), MapVisibilityRules.BASE);
    }

    private static RibbonBakeSurface buildSurface() {

        // The settled set and the sites are stated the one way the build addresses a system, so
        // the gate and the site read answer under the same key.
        var siteBySystemKey = new LinkedHashMap<SystemKey, double[]>(
            buildKeyedValues(Map.of(
                INHABITED_SYSTEM, new double[] {2000.0, 2000.0},
                DELISTED_SYSTEM, new double[] {2000.0, 2000.0})));

        siteBySystemKey.put(ANCHORED_SYSTEM, new double[] {3000.0, 3000.0});

        return new RibbonBakeSurface(
            Set.of(
                buildCellKey(INHABITED_SYSTEM),
                buildCellKey(SITELESS_SYSTEM),
                buildCellKey(DELISTED_SYSTEM),
                ANCHORED_SYSTEM),
            // Only the placed systems have a site; the settled one without is what a band with
            // nowhere to start is posed on.
            siteBySystemKey,
            // No names anywhere near these cells: where a name falls is pinned by the builder that
            // lays a band inside one cell, not by which cells are offered a band at all.
            List.of(),
            new CellRingPathCache());
    }

    private static StarSystemAPI buildSystem(String systemId) {
        return StarSystemFixture.buildSystem(systemId);
    }
}
