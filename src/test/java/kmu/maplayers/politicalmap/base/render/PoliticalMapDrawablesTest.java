package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.render.debug.DebugBorderTracingBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Pins what the holder of the built draw lists answers about them: whether anything stands, whether
 * a per-system change can be folded into it, what a rebuild left for the render to paint, that the
 * two base views are never both standing, and that releasing it leaves nothing behind.
 *
 * <p>Building the production view needs a live sector and is the integration tests'. The debug
 * overlay's build is held here, because what it does to the holder is the whole of its effect: it
 * replaces the other view rather than drawing over it, so a build that left both standing would
 * paint one of them for a reason no reader could name. Its own tracing is stood in for.
 */
final class PoliticalMapDrawablesTest {

    // The cut the debug build is driven over. Empty, since what it traces is the builder's own and
    // stood in for here.
    private static final RevisedCellGeometry CELL_GEOMETRY =
        new RevisedCellGeometry(new CellGeometryCache(), 1);

    // An overlay reporting two traced loops, so the count the holder names is neither zero nor the
    // styled-cell count the production branch would have reported.
    private static final ClusterBorderStageOverlay TRACED_OVERLAY = new ClusterBorderStageOverlay(
        List.of(new float[] {0, 0}, new float[] {1, 1}),
        List.of(),
        List.of());

    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);

    @Nested
    class HasNothingBuilt {

        @Test
        void answersYesBeforeAnythingHasBeenBuilt() {
            // What makes a first frame owe a build whatever the revisions happen to say.
            assertThat(new PoliticalMapDrawables().hasNothingBuilt())
                .isTrue();
        }

        @Test
        void answersNoOnceAPlaceholderStands() {

            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);

            assertThat(drawables.hasNothingBuilt())
                .isFalse();
        }
    }

    @Nested
    class EnsureTerritoriesNonNull {

        @Test
        void installsAnEmptyPlaceholderForTheRenderToFind() {
            // A rebuild that threw before completing leaves the draw lists null, which the renderer
            // would dereference. The placeholder makes the render a harmless no-op until a later
            // frame's retry succeeds.
            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);

            assertThat(drawables.getTerritories())
                .isNotNull();
            assertThat(drawables.getTerritories().getStyledCellByCellKey())
                .isEmpty();
        }

        @Test
        void keepsTheDrawListsAlreadyStanding() {
            // Called on every caught rebuild, so it must not replace a good build with an empty one
            // when a later frame throws.
            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);

            var standing = drawables.getTerritories();

            drawables.ensureTerritoriesNonNull(viewMock);

            assertThat(drawables.getTerritories())
                .isSameAs(standing);
        }
    }

    @Nested
    class IsDebug {

        @Test
        void answersNoWhileTheProductionViewIsTheBuiltOne() {

            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);

            assertThat(drawables.isDebug())
                .isFalse();
        }
    }

    @Nested
    class RebuildBorderTracingOverlay {

        // The classes this arrangement stands in for, closed on the way out: what the overlay
        // traces and the anchor fit that rides along are each pinned where they live.
        private final StaticSeams seams = new StaticSeams();

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        @Test
        void replacesTheProductionViewRatherThanDrawingOverIt() {
            // The invariant the render path picks a base view by: exactly one of the two is
            // non-null once anything has been built. Posed over a production view already
            // standing, since a frame that flips the dev toggle is exactly that frame.
            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);
            traceBordersThrough(drawables);

            assertThat(drawables.getBorderStageOverlay())
                .isSameAs(TRACED_OVERLAY);
            assertThat(drawables.getTerritories())
                .isNull();
            assertThat(drawables.isDebug())
                .isTrue();
        }

        @Test
        void namesTheLoopsItTracedRatherThanAnyStyledCells() {
            // The two views are not one quantity, so the line a rebuild writes names whichever was
            // built - a count alone would read as a styled-cell count on the frames it is not.
            var drawables = new PoliticalMapDrawables();

            traceBordersThrough(drawables);

            assertThat(drawables.describeBuiltCounts())
                .isEqualTo("debugBaseLoops=2");
        }

        // Builds the debug view over a stood-in trace and a neutralised anchor fit, which is all
        // the holder does with either.
        private void traceBordersThrough(PoliticalMapDrawables drawables) {

            seams.openSeam(DebugBorderTracingBuilder.class)
                .when(() -> DebugBorderTracingBuilder.buildDebugDrawables(any(), any(), any()))
                .thenReturn(TRACED_OVERLAY);

            seams.openSeam(ClusterAnchorsBuilder.class);

            drawables.rebuildBorderTracingOverlay(
                CELL_GEOMETRY,
                mock(SectorAPI.class),
                viewMock,
                ContentInputs.createEmpty());
        }
    }

    @Nested
    class CanFoldHolderChanges {

        @Test
        void refusesBeforeAnythingHasBeenBuilt() {
            // There is nothing to patch, so the frame's marks are drained and dropped rather than
            // folded into draw lists that do not exist.
            assertThat(new PoliticalMapDrawables().canFoldHolderChanges())
                .isFalse();
        }

        @Test
        void allowsOverAnUnfilteredProductionBuild() {
            // The incremental re-shape re-derives holders through the normal politics, so it is
            // safe exactly while no spotlight has keyed the cells to something else.
            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);

            assertThat(drawables.canFoldHolderChanges())
                .isTrue();
        }
    }

    @Nested
    class DescribeBuiltCounts {

        @Test
        void namesTheStyledCellsOfTheProductionView() {
            // What the render will paint, so a wrong or empty render can be confirmed against what
            // was built.
            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);

            assertThat(drawables.describeBuiltCounts())
                .isEqualTo("styledCells=0");
        }
    }

    @Nested
    class DisposeAll {

        @Test
        void dropsEverythingBuiltForItsSector() {
            // What a sector removed mid-session leaves behind if this does nothing: the cached
            // names each own a GL buffer, so the drop is what frees them rather than leaving them
            // to LazyLib's finalizer sweep.
            var drawables = new PoliticalMapDrawables();

            drawables.ensureTerritoriesNonNull(viewMock);
            drawables.disposeAll();

            assertThat(drawables.getTerritories())
                .isNull();
            assertThat(drawables.getBorderStageOverlay())
                .isNull();
            assertThat(drawables.getClusterAnchors())
                .isEmpty();
            assertThat(drawables.getFactionLabels())
                .isEmpty();
        }

        @Test
        void isSafeBeforeAnythingHasBeenBuilt() {
            // Reached for a sector installed on with the map never opened, when there are no draw
            // lists and no GL resources to release yet.
            var drawables = new PoliticalMapDrawables();

            drawables.disposeAll();

            assertThat(drawables.getTerritories())
                .isNull();
            assertThat(drawables.getFactionLabels())
                .isEmpty();
        }
    }
}
