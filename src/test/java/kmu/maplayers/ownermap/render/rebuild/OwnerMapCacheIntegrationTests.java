package kmu.maplayers.ownermap.render.rebuild;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.faces.SettledFaces;
import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.labels.LabelFonts;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageCollector;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;
import kmu.maplayers.ownermap.render.clusters.DebugBorderTracingBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuildInputs;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.ownermap.render.ribbon.RibbonBakeSettings;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapLabelSettings;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;
import kmu.settings.KmuOwnerMapGeometrySettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.render.rebuild.OwnerMapCacheReads.readProductionClustersOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Pins the cache's guarantees that hold outside a running game: a rebuild that throws leaves the
 * view that stood before it and is retried on the next frame, a fold that throws owes the next frame
 * the rebuild, releasing the cache drops everything it
 * built for its sector, the debug toggle picks which of the two views a rebuild builds, and a frame
 * owing no rebuild hands the systems marked since to the layer's strategy and owes the next frame a
 * rebuild where it folds nothing. The log says which of those a frame did and why: a batch left
 * unfolded costs a whole rebuild, and the line is the only place its reason lands.
 *
 * <p>The cache runs with its real drawables, ledger and full rebuild, over a sector listing no
 * systems, since what is pinned is how the four agree about a frame. Every builder either view is
 * made by, and the fold the incremental strategy hands a batch to, is stood in for at its static
 * seam: what each of them produces is pinned by its own suite, and what belongs here is which of
 * them a frame reaches. Each case drives one of the two strategy constants, the incremental one
 * unless it says otherwise. What the full rebuild keeps between two rebuilds is
 * {@link FullOwnerMapRebuildTests}'.
 */
final class OwnerMapCacheIntegrationTests {

    // The screen a refresh is driven for. A stand-in rather than one of the two live screens: no
    // guarantee here turns on which panel the frame was prepared for.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    // A view answering every question a decision asks of it with a constant, so two frames of one
    // case sample the same inputs and the second owes a rebuild only where a case makes it.
    private static final OwnerPaintedView VIEW =
        new OwnerPaintedViewFake(new OwnerSourceFake());

    // The owner a filtered map is spotlighting, for the case that a filter defers nothing.
    private static final String HEGEMONY_ID = "hegemony";

    // The system a change marked stale between two frames.
    private static final SystemKey MARKED_CELL = buildCellKey("marked");

    // The shades a build painted the Hegemony in, and the ones a recolour moves them to.
    private static final ColourPair HEGEMONY_PALETTE = new ColourPair(
        new Color(10, 20, 30),
        new Color(40, 50, 60));

    private static final ColourPair RECOLOURED_PALETTE = new ColourPair(
        new Color(70, 80, 90),
        new Color(100, 110, 120));

    // The machinery the throwing cases' cache belongs to, installed on no sector - which is what
    // makes the rebuild throw part way through, those cases being about what a cache leaves
    // drawable when it does.
    private final SectorMapMachinery machinery = new SectorMapMachinery(null);

    // A sector listing no systems, and the machinery installed on it, for the cases whose rebuild
    // has to complete: an empty sector cuts no cells, and every builder past the cut is a seam.
    private final SectorAPI emptySectorMock = StaleOwnerMapFixtures.buildSectorWithSystems();
    private final SectorMapMachinery emptySectorMachinery = new SectorMapMachinery(emptySectorMock);

    // What the stood-in debug builder hands back, held so a case can read the same instance off
    // the cache.
    private final ClusterBorderStageOverlay debugOverlay = new ClusterBorderStageCollector().buildOverlay();

    private final OwnerPaintedView viewMock = mock(OwnerPaintedView.class);

    // The seams a completing rebuild opens, closed after every case; empty for a case that opens
    // none of its own, so closing it then does nothing.
    private final StaticSeams seams = new StaticSeams();

    private MockedStatic<KmuOwnerMapDiagnosticsSettings> debugToggleMock;
    private MockedStatic<DebugBorderTracingBuilder> debugBuilderMock;
    private MockedStatic<OwnerMapBuilder> productionBuilderMock;
    private MockedStatic<LabelFonts> labelFontsMock;
    private MockedStatic<IncrementalOwnerRefresh> incrementalRefreshMock;

    @AfterEach
    void closeSeams() {

        seams.closeEverySeam();
    }

    @Nested
    class RefreshDrawLists {

        // No sector, so the first rebuild throws part way through. The placeholder still stands, so
        // the renderer finds a view that draws nothing, and the next frame retries.
        @Test
        void leavesThePlaceholderStandingWhenTheFirstRebuildThrows() {

            // ARRANGE
            var cache = buildCacheOver(machinery);

            // ACT
            // Every settings class the refresh reads is stubbed inert: the revision counter is the
            // framework's, the cell seed inputs and the debug gate this layer's.
            try (var settingsMock = mockStatic(KmuLunaSettings.class);
                    var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuOwnerMapDiagnosticsSettings.class)) {

                cache.refreshDrawLists(viewMock, SCREEN);
            }

            // ASSERT
            assertThat(cache.getBuiltView())
                .isSameAs(BuiltView.PLACEHOLDER);
        }

        // A thrown rebuild advances no baseline, so the frame after it builds again rather than
        // leaving the placeholder on screen until something unrelated moves.
        @Test
        void retriesAThrownRebuildOnTheNextFrame() {

            // ARRANGE
            var builtClusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

            openEverySeamARebuildReaches(builtClusters);
            throwFromTheFirstProductionBuild(builtClusters);

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(readProductionClustersOf(cache))
                .isSameAs(builtClusters);
        }

        // The placeholder a failed first build leaves holds nothing a fold could patch, so a mark over it
        // owes the rebuild whatever the strategy: the strategy that would fold it is never asked, and
        // the rebuild reads the mark for itself.
        @Test
        void owesTheRebuildForAMarkOverAFailedBuildWithoutAskingTheStrategy() {

            // ARRANGE
            var builtClusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

            openEverySeamARebuildReaches(builtClusters);
            throwFromTheFirstProductionBuild(builtClusters);

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            incrementalRefreshMock.verifyNoInteractions();
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.buildClusters(any(), any(), any()),
                times(2));
        }

        // The fold's batch is drained and the standing owners dropped before it runs, and no
        // revision moves for either, so a fold that throws leaves the thrown frame as the only
        // record: without the debt it owes, the map keeps the old owner until something unrelated
        // moves.
        @Test
        void rebuildsOnTheFrameAfterAThrownFold() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            throwFromEveryFold();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.buildClusters(any(), any(), any()),
                times(2));
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.resolveOwners(any(), any(), any()),
                times(2));
        }

        // The overlay replaces the production view outright rather than drawing over it, so the
        // builder of the other view is never asked.
        @Test
        void buildsTheBorderTracingOverlayInPlaceOfTheDrawListsUnderTheDebugToggle() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);
            var expectedBuiltView = new BuiltView.Debug(debugOverlay);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(cache.getBuiltView())
                .isEqualTo(expectedBuiltView);

            productionBuilderMock.verifyNoInteractions();
        }

        @Test
        void buildsTheDrawListsAndNoOverlayWithTheDebugToggleOff() {

            // ARRANGE
            var builtClusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

            openEverySeamARebuildReaches(builtClusters);

            var cache = buildCacheOver(emptySectorMachinery);
            var expectedBuiltView = new BuiltView.Production(builtClusters);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(cache.getBuiltView())
                .isEqualTo(expectedBuiltView);

            debugBuilderMock.verifyNoInteractions();
        }

        // Each sector settles its own faces - two sectors can name different owners - so a cache
        // reading another machinery's faces would size its names for text it never draws.
        @Test
        void settlesTheLabelFaceAgainstTheFacesOfItsOwnSector() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);
            var ownFaces = SettledFaces.resolveFacesIn(emptySectorMachinery);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            // Asked by each pass the face reaches, every ask after the first answered from the sector's memo.
            labelFontsMock
                .verify(() -> LabelFonts.settleMapLabelFace(same(ownFaces)), atLeastOnce());
            labelFontsMock
                .verify(() -> LabelFonts.settleMapLabelFace(not(same(ownFaces))), never());
        }

        // The overlay is sampled with the rebuild and handed to the build, so a batch folding into
        // that build later fits under what it was baked under rather than reading the setting live.
        @Test
        void handsTheBuildTheAnchorOverlayItSampled() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            showClusterAnchors();

            var cache = buildCacheOver(emptySectorMachinery);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            productionBuilderMock.verify(() -> OwnerMapBuilder.buildClusters(
                any(),
                argThat(OwnerMapBuildInputs::isAnchorOverlayShown),
                any()));
        }

        // The marked change's path: nothing a decision reads moved, so no rebuild is owed, and the
        // one system marked since is folded over a walk of this cache's own sector rather than left
        // for a rebuild nothing is going to run.
        @Test
        void foldsAMarkedSystemOverAWalkOfItsOwnSectorOnAFrameOwingNoRebuild() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            incrementalRefreshMock.verify(() -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(
                argThat(walk -> walk.sector() == emptySectorMock),
                any(),
                eq(Set.of(MARKED_CELL))));
        }

        // A spotlight keeps no batch from the strategy: the per-system resolver answers a marked
        // system as the spotlit build painted it, so the fold runs like any other.
        @Test
        void foldsAFilteredMapsMarkedSystem() {

            // ARRANGE
            openEverySeamARebuildReaches(createSpotlitClusters());

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            incrementalRefreshMock.verify(() -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(
                any(),
                any(),
                eq(Set.of(MARKED_CELL))));
        }

        // What the hover box keeps its composed reading against: a box read before the rebuild
        // describes a map that is no longer drawn.
        @Test
        void announcesARebuildOfWhatItDraws() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(readDrawListsRevision())
                .isEqualTo(1);
        }

        // A fold redraws only the marked systems, and the hovered one may be among them.
        @Test
        void announcesAFoldOfAMarkedSystem() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(readDrawListsRevision())
                .isEqualTo(2);
        }

        // Nearly every frame: a raise here would drop the hover box's reading every frame, which is
        // the per-frame read the box keeps it to avoid.
        @Test
        void announcesNothingOnAFrameOwingNothing() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(readDrawListsRevision())
                .isEqualTo(1);
        }

        // The frame the map spends nearly all of its life on: nothing to rebuild and nothing marked,
        // so nothing is folded.
        @Test
        void foldsNothingOnAFrameWithNoSystemMarked() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            incrementalRefreshMock.verifyNoInteractions();
        }

        // The batch is drained before the strategy is asked, so the full-rebuild strategy, which
        // folds nothing, leaves no mark for the next frame to find: the owed rebuild is the only
        // record.
        @Test
        void leavesNothingOnTheBoardForABatchTheFullRebuildStrategyOwedARebuild() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery, OwnerMapRefreshStrategy.FULL_REBUILD);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(emptySectorMachinery.resolveRefreshBoard().drainStaleGroupingSystemKeys())
                .isEmpty();
        }

        // The owed rebuild is the only record that a system changed hands, so the frame after it
        // rebuilds and reads the owners afresh - or the map shows the old owner until something
        // unrelated moves.
        @Test
        void rebuildsOnTheFrameAfterTheFullRebuildStrategyOwedARebuild() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery, OwnerMapRefreshStrategy.FULL_REBUILD);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.buildClusters(any(), any(), any()),
                times(2));
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.resolveOwners(any(), any(), any()),
                times(2));
            incrementalRefreshMock.verifyNoInteractions();
        }

        // Nothing on screen moved, so the hover box keeps its reading until the rebuild lands.
        @Test
        void announcesNothingForABatchTheFullRebuildStrategyOwedARebuild() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery, OwnerMapRefreshStrategy.FULL_REBUILD);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            assertThat(readDrawListsRevision())
                .isEqualTo(1);
        }

        // The rebuild pays the debt, so the frames after it go back to owing nothing.
        @Test
        void owesNothingMoreOnceTheOwedRebuildHasRun() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery, OwnerMapRefreshStrategy.FULL_REBUILD);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.buildClusters(any(), any(), any()),
                times(2));
        }

        // The overlay builds no draw lists for a fold to patch, so the mark owes the rebuild even
        // under the incremental strategy, and the next frame rebuilds the overlay to show it.
        @Test
        void rebuildsTheBorderTracingOverlayOnTheFrameAfterAMarkedSystemWithoutFolding() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            incrementalRefreshMock.verifyNoInteractions();
            debugBuilderMock.verify(
                () -> DebugBorderTracingBuilder.buildDebugDrawables(any(), any(), any()),
                times(2));
        }

        // A batch left unfolded costs the next frame a whole rebuild, so the line has to say which
        // of the reasons it was: here the layer chose the full walk.
        @Test
        void namesTheStrategyOnTheUnfoldedLineWhenTheStrategyOwedTheRebuild() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery, OwnerMapRefreshStrategy.FULL_REBUILD);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            var logFake = captureTheFrameLogOf(cache);

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(
                    "Owner map left 1 marked system(s) unfolded; reason=strategy FULL_REBUILD; next frame rebuilds");
        }

        // The overlay leaves nothing to fold into whatever the strategy, which the line names so the
        // rebuilds under the debug toggle are not read as the strategy's.
        @Test
        void namesTheDebugOverlayOnTheUnfoldedLineWhileItStands() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            var logFake = captureTheFrameLogOf(cache);

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(
                    "Owner map left 1 marked system(s) unfolded; reason=debugOverlay standing; next frame rebuilds");
        }

        // The batch a thrown fold dropped is gone from the board, so its warning is the only place
        // that says how many systems the next frame's rebuild has to show.
        @Test
        void warnsHowManyMarkedSystemsAThrownFoldDropped() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            throwFromEveryFold();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            var logFake = captureTheFrameLogOf(cache);

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(
                    "Owner map fold failed; dropped 1 marked system(s), next frame owes the full rebuild",
                    "Owner map left 1 marked system(s) unfolded; reason=fold threw; next frame rebuilds");

            assertThat(logFake.getThrowables())
                .singleElement()
                .extracting(Throwable::getMessage)
                .isEqualTo("fold fails");
        }

        // An owner's shades are baked into what the frames draw and move no key, so a recolour is
        // asked of the build's reading each frame. The shades are asked before the batch folds: a
        // fold ahead of the rebuild would repaint the marked cell in the new shade beside the same
        // owner's other cells still in the old one, so the rebuild takes the mark instead, and reads
        // the owners afresh rather than painting over ones carrying the old shades.
        @Test
        void redrawsARecolouredOwnerOnTheNextFrameFoldingTheMarkedSystemIntoTheRebuild() {

            // ARRANGE
            var paletteByOwnerId = new HashMap<>(Map.of(HEGEMONY_ID, HEGEMONY_PALETTE));

            openEverySeamARebuildReaches(createClustersPaintingHegemonyIn(paletteByOwnerId));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            paletteByOwnerId.put(HEGEMONY_ID, RECOLOURED_PALETTE);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.buildClusters(any(), any(), any()),
                times(2));

            productionBuilderMock.verify(
                () -> OwnerMapBuilder.resolveOwners(any(), any(), any()),
                times(2));

            incrementalRefreshMock.verifyNoInteractions();

            assertThat(emptySectorMachinery.resolveRefreshBoard().drainStaleGroupingSystemKeys())
                .isEmpty();
        }

        // The rebuild records the shades it painted in, so the recolour costs one rebuild rather
        // than one on every frame after it.
        @Test
        void owesNothingMoreOnceTheRecolourIsRedrawn() {

            // ARRANGE
            var paletteByOwnerId = new HashMap<>(Map.of(HEGEMONY_ID, HEGEMONY_PALETTE));

            openEverySeamARebuildReaches(createClustersPaintingHegemonyIn(paletteByOwnerId));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            paletteByOwnerId.put(HEGEMONY_ID, RECOLOURED_PALETTE);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.refreshDrawLists(VIEW, SCREEN);
            cache.refreshDrawLists(VIEW, SCREEN);

            // ASSERT
            productionBuilderMock.verify(
                () -> OwnerMapBuilder.buildClusters(any(), any(), any()),
                times(2));
        }
    }

    @Nested
    class Constructor {

        // The strategy decides what every later batch costs, and nothing else in the log states it.
        @Test
        void statesItsStrategyWhenBuilt() {

            // ARRANGE
            var expectedLine = "Owner map cache built; strategy=FULL_REBUILD";

            // ACT
            var logFake = LogAppenderFake.captureLogOf(
                OwnerMapCache.class,
                () -> buildCacheOver(emptySectorMachinery, OwnerMapRefreshStrategy.FULL_REBUILD));

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(expectedLine);
        }
    }

    @Nested
    class ResolveHoverTargets {

        // The cursor is tested against the shapes the frame paints, so the targets are the very draw
        // lists the rebuild left rather than a copy that could drift from them.
        @Test
        void isTheBuiltDrawLists() {

            // ARRANGE
            var builtClusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            openEverySeamARebuildReaches(builtClusters);

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            var actualHoverTargets = cache.resolveHoverTargets();

            // ASSERT
            assertThat(actualHoverTargets)
                .isSameAs(builtClusters);
        }

        // Nothing painted is a cell under the debug overlay, and a null is what the cursor read parks
        // on rather than resolving a hover into shapes nobody drew.
        @Test
        void isNullWhileTheBorderTracingOverlayReplacesTheDrawLists() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            var actualHoverTargets = cache.resolveHoverTargets();

            // ASSERT
            assertThat(actualHoverTargets)
                .isNull();
        }
    }

    @Nested
    class DisposeCachedState {

        // What a sector removed mid-session leaves behind if this does nothing: the cached names each
        // own a GL buffer, so the drop is what frees them rather than leaving them to LazyLib's
        // finalizer sweep.
        @Test
        void dropsTheDrawListsBuiltForItsSector() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            cache.disposeCachedState();

            // ASSERT
            assertThat(cache.getBuiltView())
                .isSameAs(BuiltView.PLACEHOLDER);
            assertThat(cache.getClusterAnchors())
                .isEmpty();
            assertThat(cache.getOwnerLabels())
                .isEmpty();
        }

        // Reached for a sector installed on with the map never opened, when there are no draw lists
        // and no GL resources to release yet.
        @Test
        void isSafeBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var cache = buildCacheOver(machinery);

            // ACT
            cache.disposeCachedState();

            // ASSERT
            assertThat(cache.getBuiltView())
                .isSameAs(BuiltView.PLACEHOLDER);
            assertThat(cache.getOwnerLabels())
                .isEmpty();
        }

        // The release is the last thing a sector's map does, so its line is what says the sector's
        // draw lists and GL buffers went with it - counted before they go.
        @Test
        void writesWhatItReleased() {

            // ARRANGE
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);
            var expectedLine = "Owner map cache released; view=production styledCells=0 labels=0 anchors=0 cells=0";

            cache.refreshDrawLists(VIEW, SCREEN);

            // ACT
            var logFake = LogAppenderFake.captureLogOf(OwnerMapCache.class, cache::disposeCachedState);

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(expectedLine);
        }
    }

    // A cache over the given machinery, drawing under the layer's test keys, seeding a cell for every
    // system the map draws and folding marked systems by the default strategy.
    private OwnerMapCache buildCacheOver(SectorMapMachinery cacheMachinery) {

        return buildCacheOver(cacheMachinery, OwnerMapRefreshStrategy.INCREMENTAL);
    }

    // The same cache, taking marked systems by the given strategy.
    private OwnerMapCache buildCacheOver(
            SectorMapMachinery cacheMachinery,
            OwnerMapRefreshStrategy refreshStrategy) {

        return new OwnerMapCache(
            cacheMachinery,
            OwnerMapBodyPreferencesFixtures.createUnderTestKeys(),
            CellSeedRule.SEED_DRAWN_SYSTEMS,
            refreshStrategy);
    }

    // Draw lists built under a spotlight, for the case that a spotlight keeps no batch from the
    // strategy.
    private static OwnerMapClusters createSpotlitClusters() {

        return OwnerMapClusterFixtures.createClustersSpotlighting(
            HEGEMONY_ID,
            Map.of(),
            Set.of(),
            Set.of());
    }

    // Draw lists holding one Hegemony system, painted by a reading whose shades are read off the
    // given map, so a case recolours the Hegemony by writing to it.
    private static OwnerMapClusters createClustersPaintingHegemonyIn(Map<String, ColourPair> paletteByOwnerId) {

        return OwnerMapClusterFixtures.createClustersReadBy(
            Map.of("held", new SystemOwner(HEGEMONY_ID, HEGEMONY_PALETTE)),
            OwnerReadingFake
                .createAnsweringNothing()
                .withLooks(paletteByOwnerId, Map.of()));
    }

    // What the cache wrote over one frame, the frame a case acts on.
    private static LogAppenderFake captureTheFrameLogOf(OwnerMapCache cache) {

        return LogAppenderFake.captureLogOf(
            OwnerMapCache.class,
            () -> cache.refreshDrawLists(VIEW, SCREEN));
    }

    // How many times the cache over the empty sector announced a rebuild of what it draws.
    private int readDrawListsRevision() {

        return emptySectorMachinery
            .resolveRefreshBoard()
            .getRevision(MapLayerCommonRefreshSignal.DRAW_LISTS);
    }

    // Has the first production build throw part way, as a fault in any stage past the cut would, and
    // every build after it hand back the given draw lists.
    private void throwFromTheFirstProductionBuild(OwnerMapClusters builtClusters) {

        productionBuilderMock
            .when(() -> OwnerMapBuilder.buildClusters(any(), any(), any()))
            .thenThrow(new IllegalStateException("first build fails"))
            .thenReturn(builtClusters);
    }

    // Has every fold throw part way, as a fault in any of its stages would.
    private void throwFromEveryFold() {

        incrementalRefreshMock
            .when(() -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(any(), any(), any()))
            .thenThrow(new IllegalStateException("fold fails"));
    }

    // Stands in for everything a rebuild reaches past its cut of an empty sector, so the frame
    // completes: the per-save pick and the diagnostics toggles no test JVM holds, every builder
    // either view is made by, the label and name passes that ride along, and the fold the
    // incremental strategy hands a marked system to. The production builder hands back the given
    // draw lists, which is what decides whether a later frame may fold into them.
    private void openEverySeamARebuildReaches(OwnerMapClusters builtClusters) {

        seams.openSeam(FilterSelection.class);
        seams.openSeam(ClusterAnchorsBuilder.class);
        seams.openSeam(LabelsBuilder.class);

        // The label styling snapshot is left real: it is a value read off the stood-in build, and
        // the fit it is handed to is stood in for, so nothing past it resolves a font or a name.

        // Either view's styling reads its theme and its border trace off settings no test JVM
        // holds; the styling itself runs for real over what these answer.
        seams.openSeam(RenderStyleReader.class)
            .when(() -> RenderStyleReader.readRenderStyle(any(), any()))
            .thenReturn(OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                OwnerMapClusterFixtures.createInertCategoryStyle()));

        seams.openSeam(KmuMapLabelSettings.class);

        // The label face is settled against the installed atlases, which no test JVM has.
        labelFontsMock = seams.openSeam(LabelFonts.class);

        debugToggleMock = seams.openSeam(KmuOwnerMapDiagnosticsSettings.class);

        debugBuilderMock = seams.openSeam(DebugBorderTracingBuilder.class);
        debugBuilderMock
            .when(() -> DebugBorderTracingBuilder.buildDebugDrawables(any(), any(), any()))
            .thenReturn(debugOverlay);

        productionBuilderMock = seams.openSeam(OwnerMapBuilder.class);
        productionBuilderMock
            .when(() -> OwnerMapBuilder.buildClusters(any(), any(), any()))
            .thenReturn(builtClusters);

        // A holding rather than the seam's null, since a null is what the cache reads as nothing
        // standing to keep.
        productionBuilderMock
            .when(() -> OwnerMapBuilder.resolveOwners(any(), any(), any()))
            .thenReturn(ResolvedOwners.createEmpty());

        // The bake's settings are read live on every production rebuild, from knobs no test JVM
        // holds; the bake they are handed to is stood in for below, so nothing reads them back.
        seams.openSeam(RibbonBakeSettings.class);

        // The bake is asked for by the production view on every rebuild, so it has to hand back
        // something to bake on rather than the seam's null.
        var ribbonsBakerMock = mock(CellRibbonsBaker.class);
        var ribbonsBakerSeamMock = seams.openSeam(CellRibbonsBaker.class);

        ribbonsBakerSeamMock
            .when(() -> CellRibbonsBaker.createForPass(any(), any(), any(), any(), any()))
            .thenReturn(ribbonsBakerMock);

        // What the fold does with a batch is its own suite's; here only whether a frame reaches it.
        incrementalRefreshMock = seams.openSeam(IncrementalOwnerRefresh.class);
    }

    // Switches the debug border-tracing toggle on, which is what swaps the view a rebuild builds.
    private void traceBordersForDebug() {

        debugToggleMock
            .when(KmuOwnerMapDiagnosticsSettings::shouldTraceBordersForDebug)
            .thenReturn(true);
    }

    // Switches the anchor overlay on, which a rebuild samples beside the picks.
    private void showClusterAnchors() {

        debugToggleMock
            .when(KmuOwnerMapDiagnosticsSettings::getOwnerMapShowClusterAnchors)
            .thenReturn(true);
    }
}
