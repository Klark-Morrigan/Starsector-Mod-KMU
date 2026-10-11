package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.BodyControlTarget;
import kmu.maplayers.base.theme.ElementStyleAdjustment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the defaults every owner-painted view inherits: no spotlight picker, so the spotlight is opted
 * into, no backdrop of the view's own, no body controls and no hover tooltip.
 */
final class OwnerPaintedViewTests {

    @Nested
    class ResolveOwnerPickerRead {

        @Test
        void defaultsToTheEmptyPickerSoASpotlightIsOptedInto() {
            // The base case is "no spotlight", so a view with nothing to list inherits a whole
            // answer. Pinned on the fake rather than on a real view, since what is under test is the
            // seam's default and not a view's choice to keep it.
            var viewFake = new OwnerPaintedViewFake();

            assertThat(viewFake.resolveOwnerPickerRead(mock(SectorAPI.class)).picker().items())
                .isEmpty();
        }

        @Test
        void defaultsToNoPresenceSoALookupAnswersRatherThanFaults() {
            // The other half of the default read. A view offering no rows can still be asked where an
            // owner is - the ask reaches the seam before any row does - so the default has to answer
            // an empty set rather than leave a null for the lookup to fall over on.
            var viewFake = new OwnerPaintedViewFake();

            assertThat(viewFake.resolveOwnerPickerRead(mock(SectorAPI.class))
                    .presenceIndex()
                    .readPresentSystemKeys("hegemony"))
                .isEmpty();
        }
    }

    @Nested
    class ResolveViewRecedeAdjustment {

        @Test
        void recedesNothingForAViewWithNoBackdropOfItsOwn() {
            // The answer every view inherits: most recede nothing of their own accord, so the bake's
            // view recede is the identity unless a view states a backdrop of its own.
            var viewFake = new OwnerPaintedViewFake();

            assertThat(viewFake.resolveViewRecedeAdjustment(ScreenMemoryScopes.createStandInScreen()))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }
    }

    @Nested
    class GetViewBodyControls {

        @Test
        void contributesNoControlsForAViewWithNoneOfItsOwn() {
            // A view adding nothing to its tab inherits an empty list, so the shared sub-options and
            // the view selector stand alone beneath it rather than beside a gap.
            var viewFake = new OwnerPaintedViewFake();
            var target = new BodyControlTarget(
                mock(MapLayerRefreshBoard.class),
                ScreenMemoryScopes.createStandInScreen());

            assertThat(viewFake.getViewBodyControls(target))
                .isEmpty();
        }
    }

    @Nested
    class ResolveHoverTooltip {

        @Test
        void showsNoTooltipForAViewWithNoneOfItsOwn() {
            // The hover tooltip is opted into as the spotlight is, so a new view shows none until it
            // has one to show.
            assertThat(new OwnerPaintedViewFake().resolveHoverTooltip())
                .isEmpty();
        }
    }
}
