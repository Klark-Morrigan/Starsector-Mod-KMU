package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.PoliticalMapView;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins what the holder of the built draw lists answers about them without a sector to build from:
 * whether anything stands, whether a per-system change can be folded into it, what a rebuild left
 * for the render to paint, and that releasing it leaves nothing behind. Building either view needs
 * a live sector and is covered by the integration tests.
 */
final class PoliticalMapDrawablesTest {

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
