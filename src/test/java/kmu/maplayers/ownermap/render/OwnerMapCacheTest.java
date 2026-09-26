package kmu.maplayers.ownermap.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageCollector;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.owners.holders.HolderProviderFake;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveFake;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveSource;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;
import kmu.maplayers.ownermap.render.clusters.DebugBorderTracingBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.labels.ClusterAnchorsBuilder;
import kmu.maplayers.ownermap.render.labels.ClusterLabelStylingSnapshot;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonsBaker;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;
import kmu.settings.KmuOwnerMapGeometrySettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Pins the cache's guarantees that hold outside a running game: a rebuild that throws leaves
 * something safe to draw, releasing the cache drops everything it built for its sector, the debug
 * toggle picks which of the two views a rebuild builds, and a frame owing no rebuild either folds
 * the systems marked since into the standing map or drops them for the next rebuild to read.
 *
 * <p>The last two are the dispatch this class owns, so every builder either view is made by and
 * the fold itself are stood in for at their static seams: what each of them produces is pinned by
 * its own suite, and what belongs here is which of them a frame reaches. The rebuilds against a
 * live sector, where those builders run for real, are the integration tests'.
 */
final class OwnerMapCacheTest {

    // The screen a refresh is driven for. A stand-in rather than one of the two live screens: no
    // guarantee here turns on which panel the frame was prepared for.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    // A view answering every question a decision asks of it with a constant, so two frames of one
    // case sample the same inputs and the second owes a rebuild only where a case makes it.
    private static final OwnerPaintedView VIEW =
        new OwnerPaintedViewFake(Map.of(), HolderProviderFake.createHoldingNothing());

    // The bloc a filtered map is spotlighting, for the case about what a filter defers.
    private static final String HEGEMONY_ID = "hegemony";

    // The system a colony event marked stale between two frames.
    private static final SystemKey MARKED_CELL = buildCellKey("marked");

    // The machinery the throwing cases' cache belongs to, installed on no sector - which is what
    // makes the rebuild throw part way through, those cases being about what a cache leaves
    // drawable when it does.
    private final SectorMapMachinery machinery = new SectorMapMachinery(null);

    // A sector listing no systems, and the machinery installed on it, for the cases whose rebuild
    // has to complete: an empty sector cuts no cells, and every builder past the cut is a seam.
    private final SectorAPI emptySectorMock = StaleOwnerMapFixtures.buildSectorWithSystems();
    private final SectorMapMachinery emptySectorMachinery = new SectorMapMachinery(emptySectorMock);

    // Where the fold opens its holder read, held so a case can say the fold was handed this one.
    private final SystemHolderResolveSource holderResolveSource =
        SystemHolderResolveFake.createSourceHoldingNothing();

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
    private MockedStatic<IncrementalOwnerRefresh> incrementalRefreshMock;

    @AfterEach
    void closeSeams() {

        seams.closeEverySeam();
    }

    @Nested
    class RefreshDrawLists {

        @Test
        void refreshDrawListsInstallsAnEmptyPlaceholderWhenTheRebuildThrows() {
            // No sector, so the rebuild throws part way through. The renderer must still find a draw
            // list rather than dereference a null one, and the next frame retries.
            var cache = buildCacheOver(machinery);

            // Every settings class the refresh reads is stubbed inert: the revision counter is the
            // framework's, the cell seed inputs and the debug gate this layer's.
            try (var settingsMock = mockStatic(KmuLunaSettings.class);
                    var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuOwnerMapDiagnosticsSettings.class)) {

                cache.refreshDrawLists(viewMock, SCREEN);
            }

            assertThat(cache.getClusters())
                .isNotNull();
            assertThat(cache.getClusters().getStyledCellByCellKey())
                .isEmpty();
        }

        @Test
        void refreshDrawListsBuildsTheBorderTracingOverlayInPlaceOfTheDrawListsUnderTheDebugToggle() {
            // The overlay replaces the production view outright rather than drawing over it, so
            // the builder of the other view is never asked and its draw lists are left null.
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            assertThat(cache.isDebug())
                .isTrue();
            assertThat(cache.getBorderStageOverlay())
                .isSameAs(debugOverlay);
            assertThat(cache.getClusters())
                .isNull();

            productionBuilderMock.verifyNoInteractions();
        }

        @Test
        void refreshDrawListsBuildsTheDrawListsAndNoOverlayWithTheDebugToggleOff() {

            var builtClusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            openEverySeamARebuildReaches(builtClusters);

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            assertThat(cache.isDebug())
                .isFalse();
            assertThat(cache.getClusters())
                .isSameAs(builtClusters);
            assertThat(cache.getBorderStageOverlay())
                .isNull();

            debugBuilderMock.verifyNoInteractions();
        }

        @Test
        void refreshDrawListsFoldsAMarkedSystemIntoTheStandingMapOnAFrameOwingNoRebuild() {
            // The colony event's path: nothing a decision reads moved, so no rebuild is owed, and
            // the one system marked since is handed to the fold rather than left for a rebuild
            // nothing is going to run.
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);
            cache.refreshDrawLists(VIEW, SCREEN);

            incrementalRefreshMock.verify(() -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(
                same(emptySectorMock),
                any(),
                eq(Set.of(MARKED_CELL)),
                same(holderResolveSource)));
        }

        @Test
        void refreshDrawListsFoldsNothingOnAFrameWithNoSystemMarked() {
            // The frame the map spends nearly all of its life on: nothing to rebuild and nothing
            // marked, so the fold is not so much as asked.
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            cache.refreshDrawLists(VIEW, SCREEN);

            incrementalRefreshMock.verifyNoInteractions();
        }

        @Test
        void refreshDrawListsDefersAMarkedSystemToTheNextRebuildWhileTheMapIsFiltered() {
            // The fold re-derives holders through the unfiltered holding, which would overwrite the
            // spotlight's synthetic keys - so a filtered map drops the mark rather than folding it,
            // and leaves nothing on the board for the next frame to find either.
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersSpotlighting(
                HEGEMONY_ID,
                Map.of(),
                Set.of(),
                Set.of()));

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);
            cache.refreshDrawLists(VIEW, SCREEN);

            incrementalRefreshMock.verify(
                () -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(any(), any(), any(), any()),
                never());

            assertThat(emptySectorMachinery.resolveRefreshBoard().drainStaleGroupingSystemKeys())
                .isEmpty();
        }

        @Test
        void refreshDrawListsDropsAMarkedSystemWhileTheBorderTracingOverlayIsBuilt() {
            // The overlay builds no draw lists for a fold to patch, so the mark is dropped and the
            // overlay picks the change up on its next full rebuild.
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);
            emptySectorMachinery.resolveRefreshBoard().markSystemGroupingStale(MARKED_CELL);
            cache.refreshDrawLists(VIEW, SCREEN);

            incrementalRefreshMock.verify(
                () -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(any(), any(), any(), any()),
                never());

            assertThat(emptySectorMachinery.resolveRefreshBoard().drainStaleGroupingSystemKeys())
                .isEmpty();
        }
    }

    @Nested
    class ResolveHoverTargets {

        @Test
        void resolveHoverTargetsIsTheBuiltDrawLists() {
            // The cursor is tested against the shapes the frame paints, so the targets are the very
            // draw lists the rebuild left rather than a copy that could drift from them.
            var builtClusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            openEverySeamARebuildReaches(builtClusters);

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            assertThat(cache.resolveHoverTargets())
                .isSameAs(builtClusters);
        }

        @Test
        void resolveHoverTargetsIsNullWhileTheBorderTracingOverlayReplacesTheDrawLists() {
            // Nothing painted is a cell under the debug overlay, and a null is what the cursor read
            // parks on rather than resolving a hover into shapes nobody drew.
            openEverySeamARebuildReaches(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            traceBordersForDebug();

            var cache = buildCacheOver(emptySectorMachinery);

            cache.refreshDrawLists(VIEW, SCREEN);

            assertThat(cache.resolveHoverTargets())
                .isNull();
        }
    }

    @Nested
    class DisposeCachedState {

        @Test
        void disposeCachedStateDropsTheDrawListsBuiltForItsSector() {
            // What a sector removed mid-session leaves behind if this does nothing: the cached names
            // each own a GL buffer, so the drop is what frees them rather than leaving them to
            // LazyLib's finalizer sweep.
            var cache = buildCacheOver(machinery);

            try (var settingsMock = mockStatic(KmuLunaSettings.class);
                    var geometrySettingsMock = mockStatic(KmuOwnerMapGeometrySettings.class);
                    var diagnosticsSettingsMock = mockStatic(KmuOwnerMapDiagnosticsSettings.class)) {

                cache.refreshDrawLists(viewMock, SCREEN);
            }

            assertThat(cache.getClusters())
                .isNotNull();

            cache.disposeCachedState();

            assertThat(cache.getClusters())
                .isNull();
            assertThat(cache.getBorderStageOverlay())
                .isNull();
            assertThat(cache.getClusterAnchors())
                .isEmpty();
            assertThat(cache.getFactionLabels())
                .isEmpty();
        }

        @Test
        void disposeCachedStateIsSafeBeforeAnythingHasBeenBuilt() {
            // Reached for a sector installed on with the map never opened, when there are no draw
            // lists and no GL resources to release yet.
            var cache = buildCacheOver(machinery);

            cache.disposeCachedState();

            assertThat(cache.getClusters())
                .isNull();
            assertThat(cache.getFactionLabels())
                .isEmpty();
        }
    }

    // A cache over the given machinery, drawing under the layer's test keys and reading holding
    // from sources that hold nothing.
    private OwnerMapCache buildCacheOver(SectorMapMachinery cacheMachinery) {
        return new OwnerMapCache(
            cacheMachinery,
            OwnerMapBodyPreferencesFixtures.createUnderTestKeys(),
            HolderProviderFake.createHoldingNothing(),
            holderResolveSource);
    }

    // Stands in for everything a rebuild reaches past its cut of an empty sector, so the frame
    // completes: the per-save pick and the diagnostics toggles no test JVM holds, every builder
    // either view is made by, the label and name passes that ride along, and the fold a frame
    // owing nothing hands a marked system to. The production builder hands back the given draw
    // lists, which is what decides whether a later frame may fold into them.
    private void openEverySeamARebuildReaches(OwnerMapClusters builtClusters) {

        seams.openSeam(FilterSelection.class);
        seams.openSeam(ClusterAnchorsBuilder.class);
        seams.openSeam(ClusterLabelStylingSnapshot.class);
        seams.openSeam(LabelsBuilder.class);

        debugToggleMock = seams.openSeam(KmuOwnerMapDiagnosticsSettings.class);
        incrementalRefreshMock = seams.openSeam(IncrementalOwnerRefresh.class);

        debugBuilderMock = seams.openSeam(DebugBorderTracingBuilder.class);
        debugBuilderMock
            .when(() -> DebugBorderTracingBuilder.buildDebugDrawables(any(), any(), any(), any()))
            .thenReturn(debugOverlay);

        productionBuilderMock = seams.openSeam(OwnerMapBuilder.class);
        productionBuilderMock
            .when(() -> OwnerMapBuilder.buildClusters(any(), any(), any(), any(), any()))
            .thenReturn(builtClusters);

        // The bake is asked for by the production view on every rebuild, so it has to hand back
        // something to bake on rather than the seam's null.
        var ribbonsBakerMock = mock(CellRibbonsBaker.class);
        var ribbonsBakerSeamMock = seams.openSeam(CellRibbonsBaker.class);

        ribbonsBakerSeamMock
            .when(() -> CellRibbonsBaker.createForPass(any(), any(), any(), any()))
            .thenReturn(ribbonsBakerMock);
    }

    // Switches the debug border-tracing toggle on, which is what swaps the view a rebuild builds.
    private void traceBordersForDebug() {
        debugToggleMock
            .when(KmuOwnerMapDiagnosticsSettings::shouldTraceBordersForDebug)
            .thenReturn(true);
    }
}
