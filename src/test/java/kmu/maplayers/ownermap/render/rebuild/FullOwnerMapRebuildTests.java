package kmu.maplayers.ownermap.render.rebuild;

import kmlib.cache.revisions.RevisionStamp;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.ContentInputsFixtures.createInertInputs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
 * ledger it asks and reports to, the cells it cuts and the owner resolve behind the production view.
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

    // What every rebuild here is keyed by. No case turns on the values, the ledger being stood in for.
    private static final ViewKey VIEW_KEY = new ViewKey(0, "view", RevisionStamp.of(0));
    private static final ContentKey CONTENT_KEY = new ContentKey(VIEW_KEY, createInertInputs());
    private static final OwnersKey OWNERS_KEY = new OwnersKey(VIEW_KEY, null);

    // A decision owing a recut and everything after it.
    private static final StaleHalves RECUT_HALVES = new StaleHalves(
        CELL_CUT,
        CONTENT_KEY,
        OWNERS_KEY,
        Staleness.CUT_AND_CONTENT);

    // A decision owing the content alone, over cells that stand.
    private static final StaleHalves CONTENT_ONLY_HALVES = new StaleHalves(
        CELL_CUT,
        CONTENT_KEY,
        OWNERS_KEY,
        Staleness.CONTENT);

    // Any view: what the rebuild keeps turns on what the ledger answered, never on who is painting.
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
    private final OwnerMapBuildLedger ledgerMock = mock(OwnerMapBuildLedger.class);

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
            ledgerMock);
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
        void paintsOverTheOwnersItKeptWhereTheLedgerLetsThemBeKept() {

            // ARRANGE
            when(ledgerMock.canReuseStandingOwners(any(), eq(true), any()))
                .thenReturn(true);

            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            builderMock.verify(
                () -> OwnerMapBuilder.resolveOwners(any(), any(), any()),
                times(1));

            verify(
                drawablesMock,
                times(2))
                    .rebuildClustersAndBands(any(), same(resolvedOwners));
        }

        // A marked system is one the sector moved under, so the ledger is asked with the marks in
        // hand: owners read before the sector moved cannot say who holds it, however small the pick
        // that owed the rebuild.
        @Test
        void handsTheLedgerTheSystemsMarkedSinceTheLastRebuild() {

            // ARRANGE
            var expectedStaleSystemKeys = Set.of(MARKED_CELL);

            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);
            machinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(ledgerMock).canReuseStandingOwners(
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
            verify(
                ledgerMock,
                never())
                    .canReuseStandingOwners(any(), eq(true), any());
        }

        // The cells and the picks go over as the rebuild holds them, so the view is built over the
        // cut the ledger was told of and under the picks the decision was taken on.
        @Test
        void handsTheProductionViewTheCellsItStandsAtAndThePicksItWasDecidedOn() {

            // ARRANGE
            var expectedCellGeometry = rebuild.getCellGeometry();
            var expectedContentInputs = CONTENT_KEY.contentInputs();
            var rebuildReadingCaptor = ArgumentCaptor.forClass(RebuildReading.class);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(drawablesMock).rebuildClustersAndBands(
                rebuildReadingCaptor.capture(),
                any());

            assertThat(rebuildReadingCaptor.getValue().cellGeometry())
                .isSameAs(expectedCellGeometry);
            assertThat(rebuildReadingCaptor.getValue().contentInputs())
                .isSameAs(expectedContentInputs);
        }

        // The overlay is sampled once beside the picks and handed on, so the view the toggle swaps in
        // fits its names under the same sampling the production view would have.
        @Test
        void handsTheBorderTracingOverlayTheAnchorOverlayItSampled() {

            // ARRANGE
            var rebuildReadingCaptor = ArgumentCaptor.forClass(RebuildReading.class);

            diagnosticsSettingsMock
                .when(KmuOwnerMapDiagnosticsSettings::getOwnerMapShowClusterAnchors)
                .thenReturn(true);

            traceBordersForDebug();

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(drawablesMock)
                .rebuildBorderTracingOverlay(rebuildReadingCaptor.capture());

            assertThat(rebuildReadingCaptor.getValue().isAnchorOverlayShown())
                .isTrue();
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

            when(ledgerMock.canReuseStandingOwners(any(), eq(true), any()))
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

        // The line names what the clusters were built from in full, so a rebuild can be read against
        // the setting, view or pick that owed it.
        @Test
        void tagsTheContentKeyItWasBuiltFrom() {

            // ARRANGE
            var expectedTagPart = "content=ContentKey[viewKey=ViewKey[settingsRevision=0, viewId=view, "
                + "viewContentRevisions=[0]], contentInputs=ContentInputs[selectedOwnerId=null";

            // ACT
            var actualTag = recordRebuildTag(CONTENT_ONLY_HALVES);

            // ASSERT
            assertThat(actualTag)
                .contains(expectedTagPart);
        }

        // A recolour moves no key on the line, so the shade that moved is what says why the map was
        // redrawn.
        @Test
        void tagsTheShadeThatMovedSinceTheLastRebuild() {

            // ARRANGE
            var expectedTagPart = "paletteMoved=owner hegemony from (1,2,3,255 / 4,5,6,255) to nothing";

            when(ledgerMock.describeMovedPalette())
                .thenReturn("owner hegemony from (1,2,3,255 / 4,5,6,255) to nothing");

            // ACT
            var actualTag = recordRebuildTag(CONTENT_ONLY_HALVES);

            // ASSERT
            assertThat(actualTag)
                .contains(expectedTagPart);
        }

        // The shades the view was just painted in are what every later frame compares against, so
        // the rebuild hands the ledger the drawables' own sample of them.
        @Test
        void recordsTheShadesTheBuiltViewWasPaintedIn() {

            // ARRANGE
            var paintedPalettes = PaintedPalettes.sampleFrom(
                OwnerReadingFake.createAnsweringNothing(),
                List.of(),
                null);

            when(drawablesMock.samplePaintedPalettes())
                .thenReturn(paintedPalettes);

            // ACT
            rebuild.rebuildWhatIsStale(CONTENT_ONLY_HALVES, VIEW_FAKE);

            // ASSERT
            verify(ledgerMock)
                .recordPalettesPainted(same(paintedPalettes));
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
            verify(
                ledgerMock,
                never())
                    .canReuseStandingOwners(any(), eq(true), any());
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
