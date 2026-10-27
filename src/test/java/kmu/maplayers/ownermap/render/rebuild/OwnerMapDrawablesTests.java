package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.ui.font.FontAtlas;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.anchor.ClusterAnchorFitInputs;
import kmu.maplayers.base.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.ownermap.render.clusters.DebugBorderTracingBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuildInputs;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.labels.ClusterLabelStylingSnapshot;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.ownermap.render.ribbon.RibbonBakeSettings;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;
import kmu.settings.KmuMapLabelSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static kmu.maplayers.ownermap.ContentInputsFixtures.createInputsSpellingNames;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the drawables keeping the built draw lists answer about them: which base view stands
 * after each build, whether a per-system change can be folded into it, what a rebuild left for the
 * render to paint, and that releasing it leaves nothing behind.
 *
 * <p>Building the production view needs a live sector and is the integration tests'; here its
 * builders are stood in for. The debug overlay's build is held here too, because what it does to the
 * drawables is the whole of its effect: it replaces the other view rather than drawing over it. Its
 * own tracing is stood in for.
 */
final class OwnerMapDrawablesTests {

    // The cut the debug build is driven over. Empty, since what it traces is the builder's own and
    // stood in for here.
    private static final RevisedCellGeometry CELL_GEOMETRY =
        new RevisedCellGeometry(new CellGeometryCache(), 1);

    // An overlay reporting two traced loops, so the count the drawables name is neither zero nor the
    // styled-cell count the production branch would have reported.
    private static final ClusterBorderStageOverlay TRACED_OVERLAY = new ClusterBorderStageOverlay(
        List.of(new float[] {0, 0}, new float[] {1, 1}),
        List.of(),
        List.of());

    // The walk a rebuild hands its stages. Over no sector: every stage reading it is stood in for.
    private static final SectorWalk WALK =
        new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

    // The face the drawables settle their labels on. Any face does: no case here reads one, every
    // stage that would being stood in for.
    private static final Supplier<FontAtlas> LABEL_FACE_SOURCE = () -> StarsectorFont.VANILLA_INSIGNIA_42;

    // The owner a spotlit build is painted under.
    private static final String HEGEMONY_ID = "hegemony";

    private final OwnerPaintedView viewMock = mock(OwnerPaintedView.class);

    @Nested
    class GetBuiltView {

        // The renderer finds a view that draws nothing before the first build, rather than a null
        // it would dereference.
        @Test
        void answersThePlaceholderBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            // ACT
            var actualBuiltView = drawables.getBuiltView();

            // ASSERT
            assertThat(actualBuiltView)
                .isSameAs(BuiltView.PLACEHOLDER);
        }
    }

    @Nested
    class HasNothingBuilt {

        // The builders a production rebuild drives, stood in for: all this asks is what the
        // drawables make of a build that ran.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // What makes a first frame owe a build whatever the revisions happen to say.
        @Test
        void answersYesBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            // ACT
            var actualHasNothingBuilt = drawables.hasNothingBuilt();

            // ASSERT
            assertThat(actualHasNothingBuilt)
                .isTrue();
        }

        @Test
        void answersNoOnceTheProductionViewStands() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            buildProductionThrough(
                seams,
                drawables,
                OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()),
                buildRebuildReading(false));

            // ACT
            var actualHasNothingBuilt = drawables.hasNothingBuilt();

            // ASSERT
            assertThat(actualHasNothingBuilt)
                .isFalse();
        }

        // Released drawables are back to the first frame's state, so a machinery rebuilt on them
        // would owe a build.
        @Test
        void answersYesOnceEverythingBuiltIsReleased() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            buildProductionThrough(
                seams,
                drawables,
                OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()),
                buildRebuildReading(false));

            drawables.disposeAll();

            // ACT
            var actualHasNothingBuilt = drawables.hasNothingBuilt();

            // ASSERT
            assertThat(actualHasNothingBuilt)
                .isTrue();
        }
    }

    @Nested
    class RebuildBorderTracingOverlay {

        // The classes this arrangement stands in for, closed on the way out: what the overlay
        // traces and the anchor fit that rides along are each pinned where they live.
        private final StaticSeams seams = new StaticSeams();

        // The anchor fit, stood in for so a case can read what the drawables handed it.
        private MockedStatic<ClusterAnchorsBuilder> anchorsMock;

        @BeforeEach
        void openAnchorsSeam() {
            anchorsMock = seams.openSeam(ClusterAnchorsBuilder.class);
        }

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // The view the render path paints is the overlay the trace answered, in place of whatever
        // stood before.
        @Test
        void standsOnTheOverlayItTraced() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var expectedBuiltView = new BuiltView.Debug(TRACED_OVERLAY);

            // ACT
            traceBordersThrough(drawables);

            // ASSERT
            assertThat(drawables.getBuiltView())
                .isEqualTo(expectedBuiltView);
        }

        // The overlay traces the borders the map would draw, so it asks the layer for owners
        // through the very source the production build would have - over the rebuild's walk, and
        // with no pick, since a spotlight's footprint is not a border the map draws.
        @Test
        void tracesTheOwnersTheViewsOwnSourceResolvesWithNobodySpotlit() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var owners = ResolvedOwners.createEmpty();
            var sourceFake = OwnerSourceFake.createAnswering(owners);

            // ACT
            var builderMock = traceBordersThrough(drawables, sourceFake, false);

            // ASSERT
            assertThat(sourceFake.readOwnerWalks())
                .singleElement()
                .isSameAs(WALK);

            assertThat(sourceFake.readSpotlitOwnerIdsAsked())
                .containsExactly((String) null);

            builderMock.verify(() -> DebugBorderTracingBuilder.buildDebugDrawables(
                any(),
                same(owners),
                any()));
        }

        // One reading of the theme for both halves, so the names over the overlay cannot be styled
        // under knobs its outlines were not traced under.
        @Test
        void tracesTheOutlinesAndNamesTheOwnersUnderOneStyling() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var stylingCaptor = ArgumentCaptor.forClass(MapStyling.class);

            try (var snapshotMock = mockStatic(ClusterLabelStylingSnapshot.class, CALLS_REAL_METHODS)) {

                // ACT
                var builderMock = traceBordersThrough(drawables, new OwnerSourceFake(), false);

                // ASSERT
                builderMock.verify(() -> DebugBorderTracingBuilder.buildDebugDrawables(
                    any(),
                    any(),
                    stylingCaptor.capture()));

                snapshotMock.verify(() -> ClusterLabelStylingSnapshot.resolveForTracing(
                    any(),
                    same(stylingCaptor.getValue()),
                    any(),
                    any(),
                    anyBoolean(),
                    any()));
            }
        }

        // The view is a look at the anchors, so the overlay as the rebuild sampled it is what holds
        // the fit open, with the names off as they are here.
        @Test
        void wantsTheNamesFittedWhileTheSampledOverlayIsShown() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var fitInputsCaptor = ArgumentCaptor.forClass(ClusterAnchorFitInputs.class);

            // ACT
            traceBordersThrough(drawables, new OwnerSourceFake(), true);

            // ASSERT
            anchorsMock.verify(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                any(),
                any(),
                fitInputsCaptor.capture()));

            assertThat(fitInputsCaptor.getValue().isFitWanted())
                .isTrue();
        }

        // The other half of the case above: with the sampled overlay off the view fits nothing, so
        // an overlay handed on as a constant cannot pass both.
        @Test
        void wantsNoNamesFittedWhileTheSampledOverlayIsOff() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var fitInputsCaptor = ArgumentCaptor.forClass(ClusterAnchorFitInputs.class);

            // ACT
            traceBordersThrough(drawables, new OwnerSourceFake(), false);

            // ASSERT
            anchorsMock.verify(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                any(),
                any(),
                fitInputsCaptor.capture()));

            assertThat(fitInputsCaptor.getValue().isFitWanted())
                .isFalse();
        }

        // The two views are not one quantity, so the line a rebuild writes names whichever was
        // built - a count alone would read as a styled-cell count on the frames it is not.
        @Test
        void namesTheLoopsItTracedRatherThanAnyStyledCells() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var expectedCounts = "debugBaseLoops=2";

            traceBordersThrough(drawables);

            // ACT
            var actualCounts = drawables.describeBuiltCounts();

            // ASSERT
            assertThat(actualCounts)
                .isEqualTo(expectedCounts);
        }

        // Builds the debug view over a stood-in trace and a neutralised anchor fit, which is all
        // the drawables do with either.
        private void traceBordersThrough(OwnerMapDrawables drawables) {
            traceBordersThrough(drawables, new OwnerSourceFake(), false);
        }

        // The same over a stated source and sampled overlay, handing back the stood-in trace for a
        // case reading what it was asked.
        private MockedStatic<DebugBorderTracingBuilder> traceBordersThrough(
                OwnerMapDrawables drawables,
                OwnerSource ownerSource,
                boolean isAnchorOverlayShown) {

            var builderMock = seams.openSeam(DebugBorderTracingBuilder.class);
            builderMock
                .when(() -> DebugBorderTracingBuilder.buildDebugDrawables(any(), any(), any()))
                .thenReturn(TRACED_OVERLAY);

            openStylingSeams(seams);

            drawables.rebuildBorderTracingOverlay(buildRebuildReading(
                new ViewReading(viewMock, OwnerReadingFake.createAnsweringNothing(), ownerSource),
                isAnchorOverlayShown));

            return builderMock;
        }
    }

    @Nested
    class FindFoldableStandingMap {

        // The builders a rebuild drives, stood in for: what each builds is pinned where it lives,
        // and all this asks is what the drawables make of a build that ran.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // There is nothing to patch, so the frame's marks are drained and dropped rather than
        // folded into draw lists that do not exist.
        @Test
        void findsNothingBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            // ACT
            var actualStandingMap = drawables.findFoldableStandingMap(CELL_GEOMETRY);

            // ASSERT
            assertThat(actualStandingMap)
                .isEmpty();
        }

        // A build that ran resolved the theme, the categories and the reading a marked system is
        // styled against, which is all a fold needs. The map handed out is over that build's own
        // draw lists and the cells it was shaped from.
        @Test
        void findsTheProductionBuildOverTheGivenCells() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

            buildProductionThrough(
                seams,
                drawables,
                clusters,
                buildRebuildReading(false));

            // ACT
            var actualStandingMap = drawables.findFoldableStandingMap(CELL_GEOMETRY);

            // ASSERT
            assertThat(actualStandingMap)
                .hasValueSatisfying(standingMap -> {

                    assertThat(standingMap.getClusters())
                        .isSameAs(clusters);
                    assertThat(standingMap.getCellGeometry())
                        .isSameAs(CELL_GEOMETRY);
                });
        }

        // A spotlight keys the cells to something other than their plain owners, and the fold still
        // takes it: the batch re-derives each marked system under the spotlight the build was
        // painted under.
        @Test
        void findsASpotlitProductionBuild() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            buildProductionThrough(
                seams,
                drawables,
                OwnerMapClusterFixtures.createClustersSpotlighting(
                    HEGEMONY_ID,
                    Map.of(),
                    Set.of(),
                    Set.of()),
                buildRebuildReading(false));

            // ACT
            var actualStandingMap = drawables.findFoldableStandingMap(CELL_GEOMETRY);

            // ASSERT
            assertThat(actualStandingMap)
                .isPresent();
        }

        // The overlay replaced the draw lists a fold would patch, so a marked system has nothing to
        // fold into and the frame owes the rebuild.
        @Test
        void findsNothingUnderTheDebugOverlay() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            openStylingSeams(seams);

            seams.openSeam(ClusterAnchorsBuilder.class);
            seams
                .openSeam(DebugBorderTracingBuilder.class)
                .when(() -> DebugBorderTracingBuilder.buildDebugDrawables(any(), any(), any()))
                .thenReturn(TRACED_OVERLAY);

            drawables.rebuildBorderTracingOverlay(buildRebuildReading(false));

            // ACT
            var actualStandingMap = drawables.findFoldableStandingMap(CELL_GEOMETRY);

            // ASSERT
            assertThat(actualStandingMap)
                .isEmpty();
        }
    }

    @Nested
    class RebuildClustersAndBands {

        // The builders a production rebuild drives, stood in for: what each builds is pinned where
        // it lives, and all this asks is what the drawables hand them.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // The view the render path paints is the draw lists the build answered, in place of whatever
        // stood before.
        @Test
        void standsOnTheDrawListsItBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            var expectedBuiltView = new BuiltView.Production(clusters);

            // ACT
            buildProductionThrough(
                seams,
                drawables,
                clusters,
                buildRebuildReading(false));

            // ASSERT
            assertThat(drawables.getBuiltView())
                .isEqualTo(expectedBuiltView);
        }

        // The build keeps the overlay as the rebuild sampled it, so a batch folding into the build
        // later fits its names under what the build was baked under rather than under a live read.
        @Test
        void handsTheBuildTheSampledOverlay() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            var buildInputsCaptor = ArgumentCaptor.forClass(OwnerMapBuildInputs.class);

            // ACT
            var builderMock = buildProductionThrough(
                seams,
                drawables,
                clusters,
                buildRebuildReading(true));

            // ASSERT
            builderMock.verify(() -> OwnerMapBuilder.buildClusters(
                any(),
                buildInputsCaptor.capture(),
                any()));

            assertThat(buildInputsCaptor.getValue().isAnchorOverlayShown())
                .isTrue();
        }

        // The view's reading and the picks are the rebuild's one sampling of each, so the owners the
        // build is handed were resolved under the very reading and spotlight it paints by.
        @Test
        void handsTheBuildTheRebuildsOwnViewReadingAndPicks() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            var buildInputsCaptor = ArgumentCaptor.forClass(OwnerMapBuildInputs.class);
            var rebuildReading = buildRebuildReading(false);

            // ACT
            var builderMock = buildProductionThrough(
                seams,
                drawables,
                clusters,
                rebuildReading);

            // ASSERT
            builderMock.verify(() -> OwnerMapBuilder.buildClusters(
                any(),
                buildInputsCaptor.capture(),
                any()));

            assertThat(buildInputsCaptor.getValue().getViewReading())
                .isSameAs(rebuildReading.viewReading());
            assertThat(buildInputsCaptor.getValue().getContentInputs())
                .isSameAs(rebuildReading.contentInputs());
        }
    }

    @Nested
    class DescribeBuiltCounts {

        // The builders a production rebuild drives, stood in for: all this asks is what the
        // drawables say of a build that ran.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // What the render will paint, so a wrong or empty render can be confirmed against what was
        // built.
        @Test
        void namesTheStyledCellsOfTheProductionView() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var expectedCounts = "styledCells=0";

            buildProductionThrough(
                seams,
                drawables,
                OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()),
                buildRebuildReading(false));

            // ACT
            var actualCounts = drawables.describeBuiltCounts();

            // ASSERT
            assertThat(actualCounts)
                .isEqualTo(expectedCounts);
        }

        // The first render can fire before any build completes, and its line says so rather than
        // reporting a count of nothing.
        @Test
        void namesThePlaceholderBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var expectedCounts = "nothingBuilt";

            // ACT
            var actualCounts = drawables.describeBuiltCounts();

            // ASSERT
            assertThat(actualCounts)
                .isEqualTo(expectedCounts);
        }
    }

    @Nested
    class DisposeAll {

        // The builders a production rebuild drives, stood in for: all this asks is what the
        // drawables keep of a build once released.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // What a sector removed mid-session leaves behind if this does nothing: the cached names each
        // own a GL buffer, so the drop is what frees them rather than leaving them to LazyLib's
        // finalizer sweep.
        @Test
        void dropsEverythingBuiltForItsSector() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            buildProductionThrough(
                seams,
                drawables,
                OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()),
                buildRebuildReading(false));

            // ACT
            drawables.disposeAll();

            // ASSERT
            assertThat(drawables.getBuiltView())
                .isSameAs(BuiltView.PLACEHOLDER);
            assertThat(drawables.getClusterAnchors())
                .isEmpty();
            assertThat(drawables.getOwnerLabels())
                .isEmpty();
        }

        // Reached for a sector installed on with the map never opened, when there are no draw lists
        // and no GL resources to release yet.
        @Test
        void isSafeBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            // ACT
            drawables.disposeAll();

            // ASSERT
            assertThat(drawables.getBuiltView())
                .isSameAs(BuiltView.PLACEHOLDER);
            assertThat(drawables.getOwnerLabels())
                .isEmpty();
        }
    }

    // A rebuild's reading over the empty cut and the stood-in walk, with the empty picks and the
    // given view reading and sampled overlay.
    private static RebuildReading buildRebuildReading(ViewReading viewReading, boolean isAnchorOverlayShown) {

        return new RebuildReading(
            CELL_GEOMETRY,
            WALK,
            viewReading,
            createInputsSpellingNames(OwnerNameFormatChoice.FULL),
            isAnchorOverlayShown);
    }

    // The same over a view reading answering nothing of note, for a case about what the drawables do
    // with the reading rather than what the view answers.
    private RebuildReading buildRebuildReading(boolean isAnchorOverlayShown) {

        return buildRebuildReading(
            new ViewReading(viewMock, OwnerReadingFake.createAnsweringNothing(), new OwnerSourceFake()),
            isAnchorOverlayShown);
    }

    // Builds the production view over stood-in builders, the one handing back the given clusters,
    // and hands back the build's seam for a case reading what it was asked.
    private static MockedStatic<OwnerMapBuilder> buildProductionThrough(
            StaticSeams seams,
            OwnerMapDrawables drawables,
            OwnerMapClusters clusters,
            RebuildReading rebuildReading) {

        openStylingSeams(seams);

        var builderMock = seams.openSeam(OwnerMapBuilder.class);
        builderMock
            .when(() -> OwnerMapBuilder.buildClusters(any(), any(), any()))
            .thenReturn(clusters);

        seams.openSeam(ClusterAnchorsBuilder.class);

        // The bake's settings are read live by the drawables, from knobs no test JVM holds; the
        // bake they are handed to is stood in for, so nothing reads them back.
        seams
            .openSeam(RibbonBakeSettings.class);
        seams
            .openSeam(CellRibbonsBaker.class)
            .when(() -> CellRibbonsBaker.createForPass(any(), any(), any(), any(), any()))
            .thenReturn(mock(CellRibbonsBaker.class));

        drawables.rebuildClustersAndBands(rebuildReading, ResolvedOwners.createEmpty());

        return builderMock;
    }

    // The live reads behind a rebuild's one styling read, stood in for: the theme and the two
    // border-trace knobs, none of which a test JVM answers. The styling itself runs for real, so a
    // case reading what a builder was handed reads the value the drawables built.
    private static void openStylingSeams(StaticSeams seams) {

        seams
            .openSeam(RenderStyleReader.class)
            .when(() -> RenderStyleReader.readRenderStyle(any(), any()))
            .thenReturn(OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                OwnerMapClusterFixtures.createInertCategoryStyle()));
        seams
            .openSeam(KmuMapLabelSettings.class);
    }
}
