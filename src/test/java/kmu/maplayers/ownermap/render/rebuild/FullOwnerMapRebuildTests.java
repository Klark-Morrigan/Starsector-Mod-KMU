package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the full rebuild keeps for itself across rebuilds: the number of the cut its cells stand
 * at, the owners it may paint over again, and the line it writes about each rebuild.
 *
 * <p>A unit test, so what the rebuild drives is stood in for: the drawables it builds into, the
 * decider it asks and reports to, the cells it cuts and the owner resolve behind the production view.
 * What each of them does is pinned by its own suite; what belongs here is what the rebuild hands each
 * and what it keeps between two rebuilds.
 */
final class FullOwnerMapRebuildTests {

    // The row every rebuild's line is written on.
    private static final String REBUILD_DRAWABLES_SECTION_NAME = "ownerMap.rebuildDrawables";

    // What every rebuild here is cut under. No case turns on the values, the cut being stood in for.
    private static final CellCutInputs CELL_CUT = new CellCutInputs(
        0,
        new CellSeedInputs(8, 1000.0),
        MapVisibilityRules.BASE);

    // A decision owing a recut and everything after it.
    private static final StaleHalves RECUT_HALVES = new StaleHalves(
        CELL_CUT,
        true,
        ContentInputs.createEmpty(),
        0,
        0,
        true);

    // A decision owing the content alone, over cells that stand.
    private static final StaleHalves CONTENT_ONLY_HALVES = new StaleHalves(
        CELL_CUT,
        false,
        ContentInputs.createEmpty(),
        0,
        0,
        true);

    // Any view: what the rebuild keeps turns on what the decider answered, never on who is painting.
    private static final OwnerPaintedView VIEW_FAKE = new OwnerPaintedViewFake(new OwnerSourceFake());

    // The system a change marked stale between two rebuilds.
    private static final SystemKey MARKED_CELL = buildCellKey("marked");

    // The classes this arrangement stands in for, closed after every case.
    private final StaticSeams seams = new StaticSeams();

    // The machinery the rebuild draws, over a sector listing no systems: every stage that would read
    // one is stood in for, and the board it drains is the one a case marks.
    private final SectorMapMachinery machinery =
        new SectorMapMachinery(StaleOwnerMapFixtures.buildSectorWithSystems());

    private final OwnerMapDrawables drawablesMock = mock(OwnerMapDrawables.class);
    private final OwnerMapRebuildDecider deciderMock = mock(OwnerMapRebuildDecider.class);

    // What every owner resolve answers, held so a case can tell the owners a build was handed apart
    // from a fresh read.
    private final ResolvedOwners resolvedOwners = ResolvedOwners.createEmpty();

    // The cells the rebuild makes for itself, stood in for as it constructs them.
    private MockedConstruction<CellGeometryCache> cellsMock;

    private MockedStatic<KmuOwnerMapDiagnosticsSettings> diagnosticsSettingsMock;
    private MockedStatic<OwnerMapBuilder> builderMock;

    private FullOwnerMapRebuild rebuild;

    @BeforeEach
    void openSeamsAndBuildTheRebuild() {

        cellsMock = mockConstruction(CellGeometryCache.class);
        diagnosticsSettingsMock = seams.openSeam(KmuOwnerMapDiagnosticsSettings.class);

        builderMock = seams.openSeam(OwnerMapBuilder.class);
        builderMock
            .when(() -> OwnerMapBuilder.resolveOwners(any(), any(), any()))
            .thenReturn(resolvedOwners);

        rebuild = new FullOwnerMapRebuild(
            machinery,
            CellSeedRule.SEED_DRAWN_SYSTEMS,
            drawablesMock,
            deciderMock);
    }

    @AfterEach
    void closeSeams() {

        cellsMock.close();
        seams.closeEverySeam();
    }

    @Nested
    class GetCellGeometry {

        // The first cut takes the next number, so a number held from before any cut cannot read as
        // derived from it.
        @Test
        void standsAtTheFirstCutNumberBeforeAnythingIsCut() {

            // ARRANGE
            var expectedRevision = 0;

            // ACT
            var actualRevision = rebuild
                .getCellGeometry()
                .revision();

            // ASSERT
            assertThat(actualRevision)
                .isEqualTo(expectedRevision);
        }
    }

    @Nested
    class RebuildWhatIsStale {

        // Work fitted inside the cells is keyed by the cut it was fitted against, so every recut
        // takes a number no earlier cut held.
        @Test
        void movesTheCutNumberOnByOneWithEachRecut() {

            // ARRANGE
            var expectedRevision = 2;

            rebuild.rebuildWhatIsStale(RECUT_HALVES, VIEW_FAKE);

            // ACT
            rebuild.rebuildWhatIsStale(RECUT_HALVES, VIEW_FAKE);

            var actualRevision = rebuild
                .getCellGeometry()
                .revision();

            // ASSERT
            assertThat(actualRevision)
                .isEqualTo(expectedRevision);
        }

        // A rebuild that cut nothing leaves the cells as they were, so the work fitted inside them
        // still stands against the number they carry.
        @Test
        void keepsTheCutNumberThroughAContentOnlyRebuild() {

            // ARRANGE
            var expectedRevision = 1;

            rebuild.rebuildWhatIsStale(RECUT_HALVES, VIEW_FAKE);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            var actualRevision = rebuild
                .getCellGeometry()
                .revision();

            // ASSERT
            assertThat(actualRevision)
                .isEqualTo(expectedRevision);
        }

        // A style pick reaches the paint and nothing before it, so the rebuild it owes paints over
        // the owners the last one read rather than asking the sector the same question.
        @Test
        void paintsOverTheOwnersItKeptWhereTheDeciderLetsThemBeKept() {

            // ARRANGE
            when(deciderMock.canReuseStandingOwners(any(), eq(true), any()))
                .thenReturn(true);

            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            builderMock.verify(
                () -> OwnerMapBuilder.resolveOwners(any(), any(), any()),
                times(1));

            verify(drawablesMock, times(2)).rebuildClustersAndBands(
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                same(resolvedOwners));
        }

        // A marked system is one the sector moved under, so the decider is asked with the marks in
        // hand: owners read before the sector moved cannot say who holds it, however small the pick
        // that owed the rebuild.
        @Test
        void handsTheDeciderTheSystemsMarkedSinceTheLastRebuild() {

            // ARRANGE
            var expectedStaleSystemKeys = Set.of(MARKED_CELL);

            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);
            machinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(deciderMock).canReuseStandingOwners(
                any(),
                eq(true),
                eq(expectedStaleSystemKeys));
        }

        // The overlay reads owners for itself and drops what was marked, so the owners standing from
        // before it cannot be trusted by the production rebuild that follows it.
        @Test
        void keepsNoOwnersThroughTheBorderTracingOverlay() {

            // ARRANGE
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            traceBordersForDebug();
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);
            stopTracingBordersForDebug();

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(deciderMock, never()).canReuseStandingOwners(
                any(),
                eq(true),
                any());
        }

        // The line is what tells a rebuild that recut the cells from one that only repainted them.
        @Test
        void tagsARecutAsTheGeometryRebuilt() {

            // ARRANGE
            var expectedTagPart = "geometryRebuilt=true";

            // ACT
            var actualTag = recordRebuildTag(RECUT_HALVES);

            // ASSERT
            assertThat(actualTag)
                .contains(expectedTagPart);
        }

        // The other half of the case above, so a part written as a constant cannot pass both.
        @Test
        void tagsAContentOnlyRebuildAsNoGeometryRebuilt() {

            // ARRANGE
            var expectedTagPart = "geometryRebuilt=false";

            // ACT
            var actualTag = recordRebuildTag(CONTENT_ONLY_HALVES);

            // ASSERT
            assertThat(actualTag)
                .contains(expectedTagPart);
        }

        // A rebuild that kept its owners cost what the paint costs, and the line says so without the
        // scan rows beneath it.
        @Test
        void tagsKeptOwnersAsReused() {

            // ARRANGE
            var expectedTagPart = "ownersReused=true";

            when(deciderMock.canReuseStandingOwners(any(), eq(true), any()))
                .thenReturn(true);

            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ACT
            var actualTag = recordRebuildTag(CONTENT_ONLY_HALVES);

            // ASSERT
            assertThat(actualTag)
                .contains(expectedTagPart);
        }

        // The other half of the case above: owners read afresh are not reused.
        @Test
        void tagsFreshlyReadOwnersAsNotReused() {

            // ARRANGE
            var expectedTagPart = "ownersReused=false";

            // ACT
            var actualTag = recordRebuildTag(CONTENT_ONLY_HALVES);

            // ASSERT
            assertThat(actualTag)
                .contains(expectedTagPart);
        }
    }

    @Nested
    class DropStandingOwners {

        // A batch folds a mark into the standing map rather than the kept owners, so once a frame
        // has drained one the owners no longer match the sector and the next rebuild reads afresh.
        @Test
        void leavesTheNextRebuildNothingStandingToKeep() {

            // ARRANGE
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ACT
            rebuild.dropStandingOwners();

            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(deciderMock, never()).canReuseStandingOwners(
                any(),
                eq(true),
                any());
        }
    }

    @Nested
    class DiscardCells {

        // The machinery holding the cache is going, and the cells are the largest thing it holds.
        @Test
        void releasesTheCellsItCut() {

            // ARRANGE
            rebuild.rebuildWhatIsStale(RECUT_HALVES, VIEW_FAKE);

            // ACT
            rebuild.discardCells();

            // ASSERT
            verify(cellsMock.constructed().get(0))
                .clearCachedCells();
        }
    }

    // The line one rebuild under the given decision wrote on its row.
    private String recordRebuildTag(StaleHalves staleHalves) {

        return RecordedCapture
            .recordWhile(() -> rebuild.rebuildWhatIsStale(staleHalves, VIEW_FAKE))
            .findNode(REBUILD_DRAWABLES_SECTION_NAME)
            .getWorstCall()
            .getTag();
    }

    // Switches the debug border-tracing toggle on, which is what swaps the view a rebuild builds.
    private void traceBordersForDebug() {

        diagnosticsSettingsMock
            .when(KmuOwnerMapDiagnosticsSettings::shouldTraceBordersForDebug)
            .thenReturn(true);
    }

    // Switches the toggle back off, so the next rebuild builds the production view again.
    private void stopTracingBordersForDebug() {

        diagnosticsSettingsMock
            .when(KmuOwnerMapDiagnosticsSettings::shouldTraceBordersForDebug)
            .thenReturn(false);
    }
}
