package kmu.maplayers.ownermap.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.ribbon.RibbonPathInset;
import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegment;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.SQUARE_CELL;
import static kmu.maplayers.base.ribbon.RibbonCellFixtures.SQUARE_CELL_SITE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the pass does that no single cell can be asked about: which cells it walks over, and
 * where the rings it walks are kept.
 *
 * <p>Which cells is really the question of which of the clusters' two sets the bake hands the
 * source - what the sector holds, or what this layer painted - and the two are equal on an
 * ordinary map, so only a settled system no owner holds can tell them apart. One case poses that
 * deliberately; every other here would pass either way.
 *
 * <p>The store is the point, and it is the one thing here that cannot be seen one level down. A
 * band's own suite bakes through one source and would pass just as well if every pass kept its
 * rings to itself - the cost of that shows only as a rebuild that got slower, never as a wrong
 * map. So the two cases that matter are stated against the cells' own store: a pass writes the
 * rings it walks into the store the cells hold, and a later pass over those same cells lays its
 * bands along the very same rings rather than walking them again.
 *
 * <p>Identity, not equality, is what says a ring was not walked again: a fresh walk of the same
 * outline produces an equal path, so only the same instance coming back distinguishes a pass that
 * read the store from one that quietly re-derived what it found there.
 *
 * <p>A bake per bake rather than one reused, because that is how a rebuild drives it - each one
 * is handed the settings its caller just read and samples the names afresh - so a case that reused
 * one would be posing an arrangement production never has. The walk of the sector each is handed is opened here for
 * the same reason it is opened by a rebuild: whose walk it is belongs to the caller, and every
 * case here is about what the bake does with the cells rather than about how current its counts
 * are.
 *
 * <p>The switched-off cases are the bake's because the switch is: a pass with the bands off builds
 * no source at all, so it has to be seen to ask the owner source nothing, walk no ring, and leave
 * every cell bare - the bands a pass with them on laid included.
 */
final class CellRibbonsBakerTests {

    private static final String BANDED_SYSTEM_ID = "corvus";
    private static final String OTHER_BANDED_SYSTEM_ID = "askonia";
    private static final SystemKey BANDED_CELL = buildCellKey(BANDED_SYSTEM_ID);
    private static final SystemKey OTHER_BANDED_CELL = buildCellKey(OTHER_BANDED_SYSTEM_ID);

    // A settled system this layer's holding gives to nobody. Held apart from the two above because
    // it is the one cell whose band depends on which of the pass's two sets the bake gates on.
    private static final String UNHELD_SETTLED_SYSTEM_ID = "hybrasil";
    private static final SystemKey UNHELD_SETTLED_CELL = buildCellKey(UNHELD_SETTLED_SYSTEM_ID);

    // A cell the clusters were never told about, which is what an incremental re-bake names
    // when a marked change takes the last thing standing in a system.
    private static final SystemKey UNDRAWN_CELL = buildCellKey("vanished");

    private static final Color BAND_COLOUR = new Color(140, 160, 220);

    // The row a bake lands on.
    private static final String BAKE_SECTION = "mapLayer.bakeRibbons";

    // A plan with a run in it, so a cell that reaches the geometry comes back carrying a band and
    // one the pass never reached is told apart by drawing none.
    private static final RibbonPlan ANY_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(BAND_COLOUR, 1)));

    @Nested
    class BakeAllCellRibbons {

        @Test
        void bandsEveryCellTheMapDrew() {

            var clusters = buildTwoDrawnCells();

            bakeEveryCellThrough(clusters);

            assertThat(clusters.getPaintedCells().getRibbonStore().getRibbonByCellKey())
                .containsOnlyKeys(BANDED_CELL, OTHER_BANDED_CELL);
        }

        @Test
        void bandsASettledCellThisLayersHoldingGivesToNobody() {
            // The wiring the whole step turns on, and the one case that can fail if the bake reads
            // the wrong set: the pass gates on what the sector holds, not on what this layer
            // painted, so a haven no owner holds is offered a band like any settled cell. Handed
            // the owner map instead, this cell would be skipped and every other case here would
            // still pass.
            var clusters = buildHeldAndUnheldDrawnCells();

            bakeEveryCellThrough(clusters);

            assertThat(clusters.getPaintedCells().getRibbonStore().getRibbonByCellKey())
                .containsOnlyKeys(BANDED_CELL, UNHELD_SETTLED_CELL);
        }

        @Test
        void reportsThePassAsOneRowRunningATurnPerCell() {
            // What the readout has to answer is what a cell costs, and that is a turn of the bake
            // rather than a call of it: a section per phase would report what a whole pass spent
            // and leave the number a rebuild scales with to be divided out by hand.
            // The pass is posed before anything is recording. Posing it reads the sector, and
            // those reads count what they walked onto the reserved row; in play the same reads
            // happen inside the refresh that mints the pass, on that beat's own row.
            var baker = bakeThrough(buildTwoDrawnCells());
            var bakeRow = RecordedCapture
                .recordWhile(baker::bakeAllCellRibbons)
                .findNode(BAKE_SECTION);

            assertThat(bakeRow.getTiming().getCount())
                .isEqualTo(1);
            assertThat(bakeRow.getIterations().getCount())
                .isEqualTo(2);
            assertThat(bakeRow.getIterations().getPhaseTotals())
                .extracting(phaseTotal -> phaseTotal.getPhase().getName())
                .containsExactly("plan", "trace", "carve", "stroke");
        }

        @Test
        void countsTheCellsItWasAskedToBand() {
            // The number the pass's duration is read against, reported where the profiler sees
            // it. What became of them is on the call's tag instead, being a fact about one call
            // rather than a volume of work.
            var baker = bakeThrough(buildTwoDrawnCells());

            var bakeRow = RecordedCapture
                .recordWhile(baker::bakeAllCellRibbons)
                .findNode(BAKE_SECTION);

            assertThat(bakeRow.findCount(MapBuildCounters.CELLS).getTotals().getTotal())
                .isEqualTo(2);
            assertThat(bakeRow.getWorstCall().getTag())
                .isEqualTo("banded=2 traced=2 reused=0 unplanned=0 refusedForRoom=0");
        }

        // The kept rings' saving, stated on the call a later bake writes: both cells laid along
        // the rings the first bake walked, and none walked again.
        @Test
        void tagsALaterPassWithTheRingsItFoundStanding() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();
            var expectedTag = "banded=2 traced=0 reused=2 unplanned=0 refusedForRoom=0";

            bakeEveryCellThrough(clusters);

            var baker = bakeThrough(clusters);

            // ACT
            var actualTag = RecordedCapture
                .recordWhile(baker::bakeAllCellRibbons)
                .findNode(BAKE_SECTION)
                .getWorstCall()
                .getTag();

            // ASSERT
            assertThat(actualTag)
                .isEqualTo(expectedTag);
        }

        // The wiring this suite exists for. A pass handed a store of its own would bake an
        // identical map and throw every ring away at the end of it, which nothing about the
        // bands can show - so what is asserted is that the rings landed where the cells keep
        // them, which is also where the write that re-shapes a cell will drop them.
        @Test
        void keepsEachWalkedRingInTheStoreTheCellsThemselvesHold() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();

            // ACT
            bakeEveryCellThrough(clusters);

            // ASSERT
            var actualBandedRing = findPaddedRingOf(clusters, BANDED_CELL);
            var actualOtherBandedRing = findPaddedRingOf(clusters, OTHER_BANDED_CELL);

            assertThat(actualBandedRing)
                .isNotNull();
            assertThat(actualOtherBandedRing)
                .isNotNull();
        }

        // What the store is for. A bake runs whenever a cluster name may have moved, which is
        // every flip the layer marks, while a cell's ring moves only when the cell is cut
        // again - so the second pass over an untouched cell must walk nothing and lay its band
        // along the ring already standing.
        @Test
        void laysALaterPassesBandsAlongTheRingsTheFirstPassWalked() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();

            bakeEveryCellThrough(clusters);

            var expectedRing = findPaddedRingOf(clusters, BANDED_CELL);

            // Stated before the identity below: two passes that both kept their rings to
            // themselves leave null standing either side of the second bake, and "the same
            // nothing" would read as a ring carried over.
            assertThat(expectedRing)
                .isNotNull();

            // ACT
            bakeEveryCellThrough(clusters);

            // ASSERT
            var actualRing = findPaddedRingOf(clusters, BANDED_CELL);

            assertThat(actualRing)
                .isSameAs(expectedRing);
        }

        // The other half of that, and why the store needs no key: the write that records a
        // cell's new shape drops the ring walked inside its old one, so the next pass walks a
        // ring for the shape the cell holds now rather than serving one traced round a cell
        // that is no longer there.
        @Test
        void walksACellsRingAgainOnceItHasBeenReshaped() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();

            bakeEveryCellThrough(clusters);

            var ringBeforeReshape = findPaddedRingOf(clusters, BANDED_CELL);

            clusters.getPaintedCells().putPaintedCell(
                BANDED_CELL,
                OwnerMapClusterFixtures.createPlaceholderPaintedCellOn(SQUARE_CELL));

            // ACT
            bakeEveryCellThrough(clusters);

            // ASSERT
            var actualRing = findPaddedRingOf(clusters, BANDED_CELL);

            assertThat(actualRing)
                .isNotSameAs(ringBeforeReshape);
        }

        @Test
        void tracesAPathForEveryCellWhileTheOverlayIsOn() {
            // The overlay rides the same loop as the bands, so every cell the pass bands is a cell
            // it traces: a path missing beside a band would leave the one cell a reader looked at
            // with nothing to compare its band against.
            var clusters = buildTwoDrawnCells();
            var expectedCellKeys = Set.of(BANDED_CELL, OTHER_BANDED_CELL);

            bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsTracingPaths());

            var actualCellKeys = clusters
                .getPaintedCells()
                .getRibbonStore()
                .getRibbonPathByCellKey()
                .keySet();

            assertThat(actualCellKeys)
                .isEqualTo(expectedCellKeys);
        }

        @Test
        void clearsThePathsAPassTakenWithTheOverlayOnLeftBehind() {
            // Nothing else empties them: a settings change rebuilds every cell, and the pass that
            // runs with the overlay off has to be what takes the old paths down.
            var clusters = buildTwoDrawnCells();

            bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsTracingPaths());
            bakeEveryCellThrough(clusters);

            var actualRibbonPaths = clusters
                .getPaintedCells()
                .getRibbonStore()
                .getRibbonPathByCellKey();

            assertThat(actualRibbonPaths)
                .isEmpty();
        }

        // The switch takes the bands off a map that already carries them, so a pass with it off
        // has to clear the bands an earlier pass laid rather than only lay none of its own.
        @Test
        void bakesEveryCellBareWhileTheBandsAreSwitchedOff() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();

            bakeEveryCellThrough(clusters);

            // ACT
            bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsWithBandsOff());

            // ASSERT
            var actualRibbons = clusters
                .getPaintedCells()
                .getRibbonStore()
                .getRibbonByCellKey();

            assertThat(actualRibbons)
                .isEmpty();
        }

        // The half of the switch a player cannot see and the half that costs: with the bands off,
        // a rebuild must not still be counting what every settled system holds for a readout
        // nothing will draw.
        @Test
        void asksTheSourceNothingWhileTheBandsAreSwitchedOff() {

            // ARRANGE
            var sourceFake = buildSourcePlanningEveryCell();
            var clusters = buildTwoDrawnCellsCountedBy(sourceFake);

            // ACT
            bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsWithBandsOff());

            // ASSERT
            var actualPlannerWalks = sourceFake.readRibbonPlannerWalks();

            assertThat(actualPlannerWalks)
                .isEmpty();
        }

        // Nothing is laid with the bands off, so no cell's ring is worth walking.
        @Test
        void walksNoRingWhileTheBandsAreSwitchedOff() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();

            // ACT
            bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsWithBandsOff());

            // ASSERT
            var actualRingPath = findPaddedRingOf(clusters, BANDED_CELL);

            assertThat(actualRingPath)
                .isNull();
        }

        // With the bands off no layout is in play, so the overlay has nothing to report on even
        // while it is on: a ring drawn from sizes nothing is laid at would be a diagnostic of its
        // own arithmetic.
        @Test
        void tracesNoPathWhileTheBandsAreSwitchedOff() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();

            // ACT
            bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsWithBandsOff());

            // ASSERT
            var actualRibbonPaths = clusters
                .getPaintedCells()
                .getRibbonStore()
                .getRibbonPathByCellKey();

            assertThat(actualRibbonPaths)
                .isEmpty();
        }
    }

    @Nested
    class BakeCellRibbonsOf {

        @Test
        void bandsOnlyTheCellsItWasNamed() {
            // The incremental path: a marked change disturbs a handful of cells, and the rest of
            // the map keeps the bands it already carries rather than being re-baked around them.
            var clusters = buildTwoDrawnCells();

            bakeThrough(clusters).bakeCellRibbonsOf(List.of(BANDED_CELL));

            assertThat(clusters.getPaintedCells().getRibbonStore().getRibbonByCellKey())
                .containsOnlyKeys(BANDED_CELL);
        }

        @Test
        void passesOverACellTheMapNeverDrew() {
            // A caller names the cells something happened to, and one of them losing the last
            // thing standing in it is one of the things that can have happened - so a cell with no
            // shape to bake inside is skipped rather than being an error the refresh has to guard
            // against.
            var clusters = buildTwoDrawnCells();

            assertThatCode(() -> bakeThrough(clusters).bakeCellRibbonsOf(List.of(UNDRAWN_CELL)))
                .doesNotThrowAnyException();

            assertThat(clusters.getPaintedCells().getRibbonStore().getRibbonByCellKey())
                .isEmpty();
        }
    }

    @Nested
    class CreateForPass {

        // A band that looks wrong is first read against what it was laid at, so the pass states
        // every part of its settings once.
        @Test
        void statesThePassesSettingsOnItsDebugLine() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();
            var expectedLine = "Band bake settings;"
                + " width=120.0 pad=60.0 alwaysDrawn=false"
                + " contestedRuns=3/1 uncontestedRuns=3/1"
                + " keptClearOfNames=true nameClearance=FITTED_BOX pathsShown=false";

            // ACT
            var logFake = LogAppenderFake.captureLogOf(
                CellRibbonsBaker.class,
                () -> bakeThrough(clusters));

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(expectedLine);
        }

        // With the bands off none of the rest is read, so the line says only that.
        @Test
        void statesOnlyThatTheBandsAreOffWhileTheyAreSwitchedOff() {

            // ARRANGE
            var clusters = buildTwoDrawnCells();
            var expectedLine = "Band bake with the bands switched off; every cell bakes bare";

            // ACT
            var logFake = LogAppenderFake.captureLogOf(
                CellRibbonsBaker.class,
                () -> bakeThrough(clusters, RibbonSettingsFixtures.createSettingsWithBandsOff()));

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(expectedLine);
        }
    }

    // Two settled, placed, band-sized cells, each drawing as a system of its own - the ordinary
    // arrangement a pass runs over, and the smallest one that can show a bake reaching every cell
    // rather than only the first.
    private static OwnerMapClusters buildTwoDrawnCells() {
        return buildTwoDrawnCellsCountedBy(buildSourcePlanningEveryCell());
    }

    // The same two cells, counted through a source the case holds, for the case reading what the
    // source was asked.
    private static OwnerMapClusters buildTwoDrawnCellsCountedBy(OwnerSourceFake sourceFake) {

        return drawCells(
            OwnerMapClusterFixtures.createClustersOwnedBy(
                Map.of(
                    BANDED_SYSTEM_ID, buildOwner(),
                    OTHER_BANDED_SYSTEM_ID, buildOwner()),
                sourceFake),
            BANDED_CELL,
            OTHER_BANDED_CELL);
    }

    // One held cell beside one the holding does not account for, which is the only arrangement
    // that tells the pass's two sets apart: under createClustersOwnedBy they are equal, so a
    // bake gating on the holding would answer every other case here correctly.
    private static OwnerMapClusters buildHeldAndUnheldDrawnCells() {

        return drawCells(
            OwnerMapClusterFixtures.createClustersSettledIn(
                Map.of(BANDED_SYSTEM_ID, buildOwner()),
                Set.of(BANDED_SYSTEM_ID, UNHELD_SETTLED_SYSTEM_ID),
                buildSourcePlanningEveryCell()),
            BANDED_CELL,
            UNHELD_SETTLED_CELL);
    }

    // The source the clusters retain, answering with the planner every case counts through - the
    // source the build's owners came from being where a bake reads its mechanic, so a planner
    // stated anywhere else would leave the pass counting through one that plans nothing.
    private static OwnerSourceFake buildSourcePlanningEveryCell() {

        var sourceFake = new OwnerSourceFake();

        sourceFake.answerRibbonPlanner(system -> ANY_PLAN);

        return sourceFake;
    }

    // Records each named cell as drawn, at the one band-sized shape this suite poses.
    private static OwnerMapClusters drawCells(
            OwnerMapClusters clusters,
            SystemKey... cellKeys) {

        for (var cellKey : cellKeys) {

            clusters.getPaintedCells().putPaintedCell(
                cellKey,
                OwnerMapClusterFixtures.createPlaceholderPaintedCellOn(SQUARE_CELL));
        }

        return clusters;
    }

    private static void bakeEveryCellThrough(OwnerMapClusters clusters) {
        bakeEveryCellThrough(clusters, RibbonSettingsFixtures.createSettingsDrawingBands());
    }

    private static void bakeEveryCellThrough(
            OwnerMapClusters clusters,
            RibbonBakeSettings settings) {

        bakeThrough(clusters, settings).bakeAllCellRibbons();
    }

    // A fresh bake over the given clusters with the bands on and the overlay off, which is what
    // every case not about either switch poses.
    private static CellRibbonsBaker bakeThrough(OwnerMapClusters clusters) {
        return bakeThrough(clusters, RibbonSettingsFixtures.createSettingsDrawingBands());
    }

    // A fresh bake over the given clusters, as a rebuild mints one per bake, over a walk of the
    // sector opened here as its caller opens one.
    //
    // The geometry and the sector are derived from the cells the clusters actually drew rather
    // than named here, so a case adding a cell gets it placed and listed without a second fixture
    // to keep in step - which is what let the settled-but-unheld case below be posed at all.
    private static CellRibbonsBaker bakeThrough(
            OwnerMapClusters clusters,
            RibbonBakeSettings settings) {

        var drawnCellKeys = clusters.getFillPolygonByCellKey().keySet();

        return CellRibbonsBaker.createForPass(
            clusters,
            buildGeometryPlacing(drawnCellKeys),
            new SectorWalk(
                new SectorPassIndex(buildSectorOf(drawnCellKeys)),
                MapVisibilityRules.BASE),
            // No names placed, since where a name falls is pinned by the builder that lays a band
            // inside one cell rather than by which cells a pass reaches.
            new StandingClusterAnchors(),
            settings);
    }

    // The ring a band was laid along in one cell, as the cells' own store keeps it. The padded ring,
    // since every cell this suite draws has room for the pad.
    private static RingPath findPaddedRingOf(OwnerMapClusters clusters, SystemKey cellKey) {

        return clusters
            .getPaintedCells()
            .getRibbonStore()
            .getRingPathCache()
            .findRingPathOf(cellKey, RibbonPathInset.PADDED);
    }

    // Each cell drawing as the system of its own name, each system placed at the same site: the
    // cells are told apart by their keys here, never by where they sit.
    private static CellGeometryCache buildGeometryPlacing(Set<SystemKey> cellKeys) {

        var systemKeyByCellKey = new LinkedHashMap<SystemKey, SystemKey>();
        var siteBySystemKey = new LinkedHashMap<SystemKey, double[]>();

        for (var cellKey : cellKeys) {

            systemKeyByCellKey.put(cellKey, cellKey);
            siteBySystemKey.put(cellKey, SQUARE_CELL_SITE);
        }

        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(systemKeyByCellKey);
        when(geometryCacheMock.getSiteBySystemKey())
            .thenReturn(siteBySystemKey);

        return geometryCacheMock;
    }

    // A sector listing the system each cell draws as, posed by the ID its key states - which is
    // the key the pass reads back off it, the systems carrying no centre and no anchor.
    private static SectorAPI buildSectorOf(Set<SystemKey> systemKeys) {

        // The systems are built before the stubbing rather than inside it: each is itself a mock,
        // and building one while another stubbing is open reads to Mockito as an unfinished stub.
        var systems = new ArrayList<StarSystemAPI>(systemKeys.size());

        for (var systemKey : systemKeys) {
            systems.add(buildSystem(systemKey.systemId()));
        }

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return sectorMock;
    }

    private static StarSystemAPI buildSystem(String systemId) {
        return StarSystemFixture.buildSystem(systemId);
    }

    // Any owner, which the fixture also counts as making its system inhabited - the set the band
    // pass gates on. Which owner holds it decides nothing here.
    private static SystemOwner buildOwner() {

        return new SystemOwner(
            "hegemony",
            new ColourPair(Color.WHITE, Color.GRAY));
    }
}
