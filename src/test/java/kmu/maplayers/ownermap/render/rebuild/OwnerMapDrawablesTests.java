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
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.render.clusters.DebugBorderTracingBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the drawables keeping the built draw lists answer about them: whether anything stands, whether
 * a per-system change can be folded into it, what a rebuild left for the render to paint, that the
 * two base views are never both standing, and that releasing it leaves nothing behind.
 *
 * <p>Building the production view needs a live sector and is the integration tests'. The debug
 * overlay's build is held here, because what it does to the drawables is the whole of its effect: it
 * replaces the other view rather than drawing over it, so a build that left both standing would
 * paint one of them for a reason no reader could name. Its own tracing is stood in for.
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
    class HasNothingBuilt {

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
        void answersNoOnceAPlaceholderStands() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            drawables.ensureClustersNonNull(viewMock);

            // ACT
            var actualHasNothingBuilt = drawables.hasNothingBuilt();

            // ASSERT
            assertThat(actualHasNothingBuilt)
                .isFalse();
        }
    }

    @Nested
    class EnsureClustersNonNull {

        // A rebuild that threw before completing leaves the draw lists null, which the renderer
        // would dereference. The placeholder makes the render a harmless no-op until a later
        // frame's retry succeeds.
        @Test
        void installsAnEmptyPlaceholderForTheRenderToFind() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            // ACT
            drawables.ensureClustersNonNull(viewMock);

            // ASSERT
            assertThat(drawables.getClusters())
                .isNotNull();
            assertThat(drawables.getClusters().getStyledCellByCellKey())
                .isEmpty();
        }

        // Called on every caught rebuild, so it must not replace a good build with an empty one
        // when a later frame throws.
        @Test
        void keepsTheDrawListsAlreadyStanding() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            drawables.ensureClustersNonNull(viewMock);

            var standingClusters = drawables.getClusters();

            // ACT
            drawables.ensureClustersNonNull(viewMock);

            // ASSERT
            assertThat(drawables.getClusters())
                .isSameAs(standingClusters);
        }
    }

    @Nested
    class IsDebug {

        @Test
        void answersNoWhileTheProductionViewIsTheBuiltOne() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            drawables.ensureClustersNonNull(viewMock);

            // ACT
            var actualIsDebug = drawables.isDebug();

            // ASSERT
            assertThat(actualIsDebug)
                .isFalse();
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

        // The invariant the render path picks a base view by: exactly one of the two is non-null
        // once anything has been built. Posed over a production view already standing, since a
        // frame that flips the dev toggle is exactly that frame.
        @Test
        void replacesTheProductionViewRatherThanDrawingOverIt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            drawables.ensureClustersNonNull(viewMock);

            // ACT
            traceBordersThrough(drawables);

            // ASSERT
            assertThat(drawables.getBorderStageOverlay())
                .isSameAs(TRACED_OVERLAY);
            assertThat(drawables.getClusters())
                .isNull();
            assertThat(drawables.isDebug())
                .isTrue();
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

            drawables.rebuildBorderTracingOverlay(
                CELL_GEOMETRY,
                WALK,
                new ViewReading(viewMock, OwnerReadingFake.createAnsweringNothing(), ownerSource),
                ContentInputs.createEmpty(),
                isAnchorOverlayShown);

            return builderMock;
        }
    }

    @Nested
    class CanFoldOwnerChanges {

        // The builders a production rebuild drives, stood in for: what each builds is pinned where
        // it lives, and all this asks is what the drawables make of a build that ran.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        // There is nothing to patch, so the frame's marks are drained and dropped rather than
        // folded into draw lists that do not exist.
        @Test
        void refusesBeforeAnythingHasBeenBuilt() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            // ACT
            var actualCanFold = drawables.canFoldOwnerChanges();

            // ASSERT
            assertThat(actualCanFold)
                .isFalse();
        }

        // A build that ran resolved the theme, the categories and the reading a marked system is
        // styled against, which is all a fold needs.
        @Test
        void allowsOverAProductionBuild() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            buildProductionThrough(
                seams,
                drawables,
                OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()),
                false);

            // ACT
            var actualCanFold = drawables.canFoldOwnerChanges();

            // ASSERT
            assertThat(actualCanFold)
                .isTrue();
        }

        // A spotlight keys the cells to something other than their plain owners, and the fold still
        // takes it: the batch re-derives each marked system under the spotlight the build was
        // painted under.
        @Test
        void allowsOverASpotlitProductionBuild() {

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
                false);

            // ACT
            var actualCanFold = drawables.canFoldOwnerChanges();

            // ASSERT
            assertThat(actualCanFold)
                .isTrue();
        }

        // The placeholder resolved no theme, no categories and no reading, so a marked system
        // folded into it would be styled against nothing at all.
        @Test
        void refusesThePlaceholderAFailedBuildStandsBehind() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            drawables.ensureClustersNonNull(viewMock);

            // ACT
            var actualCanFold = drawables.canFoldOwnerChanges();

            // ASSERT
            assertThat(actualCanFold)
                .isFalse();
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

        // The build keeps the overlay as the rebuild sampled it, so a batch folding into the build
        // later fits its names under what the build was baked under rather than under a live read.
        @Test
        void handsTheBuildTheSampledOverlay() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

            // ACT
            var builderMock = buildProductionThrough(
                seams,
                drawables,
                clusters,
                true);

            // ASSERT
            builderMock.verify(() -> OwnerMapBuilder.buildClusters(
                any(),
                any(),
                any(),
                eq(true),
                any(),
                any()));
        }
    }

    @Nested
    class DescribeBuiltCounts {

        // What the render will paint, so a wrong or empty render can be confirmed against what was
        // built.
        @Test
        void namesTheStyledCellsOfTheProductionView() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);
            var expectedCounts = "styledCells=0";

            drawables.ensureClustersNonNull(viewMock);

            // ACT
            var actualCounts = drawables.describeBuiltCounts();

            // ASSERT
            assertThat(actualCounts)
                .isEqualTo(expectedCounts);
        }
    }

    @Nested
    class DisposeAll {

        // What a sector removed mid-session leaves behind if this does nothing: the cached names each
        // own a GL buffer, so the drop is what frees them rather than leaving them to LazyLib's
        // finalizer sweep.
        @Test
        void dropsEverythingBuiltForItsSector() {

            // ARRANGE
            var drawables = new OwnerMapDrawables(LABEL_FACE_SOURCE);

            drawables.ensureClustersNonNull(viewMock);

            // ACT
            drawables.disposeAll();

            // ASSERT
            assertThat(drawables.getClusters())
                .isNull();
            assertThat(drawables.getBorderStageOverlay())
                .isNull();
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
            assertThat(drawables.getClusters())
                .isNull();
            assertThat(drawables.getOwnerLabels())
                .isEmpty();
        }
    }

    // Builds the production view over stood-in builders, the one handing back the given clusters,
    // and hands back the build's seam for a case reading what it was asked.
    private MockedStatic<OwnerMapBuilder> buildProductionThrough(
            StaticSeams seams,
            OwnerMapDrawables drawables,
            OwnerMapClusters clusters,
            boolean isAnchorOverlayShown) {

        openStylingSeams(seams);

        var builderMock = seams.openSeam(OwnerMapBuilder.class);
        builderMock
            .when(() -> OwnerMapBuilder.buildClusters(any(), any(), any(), anyBoolean(), any(), any()))
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

        drawables.rebuildClustersAndBands(
            CELL_GEOMETRY,
            WALK,
            new ViewReading(viewMock, OwnerReadingFake.createAnsweringNothing(), new OwnerSourceFake()),
            ContentInputs.createEmpty(),
            isAnchorOverlayShown,
            ResolvedOwners.createEmpty());

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
