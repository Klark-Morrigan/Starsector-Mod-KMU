package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.holding.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the shared per-bloc style decision the fills and the cluster-name labels both read: the
 * filter-mode adjustment a bloc takes (spotlight untouched, everyone else receding by the union
 * of the view's recede and the shared recede) and the whole decision it feeds.
 */
final class OwnerStyleResolverTest {

    // A view stub answering both per-bloc style seams with fixed values, so a test can prove
    // whether the decision consulted the view (off filter) or bypassed it (under filter).
    private static OwnerPaintedView buildViewMockDeciding(
            boolean usesIndependentStyle,
            ElementStyleAdjustment adjustment) {

        var viewMock = mock(OwnerPaintedView.class);

        when(viewMock.shouldUseIndependentStyle(any(), any(), any()))
            .thenReturn(usesIndependentStyle);
        when(viewMock.resolveBlocStyleAdjustment(any(), any(), any()))
            .thenReturn(adjustment);

        return viewMock;
    }

    @Nested
    class ResolveFilterAdjustment {

        @Test
        void resolveFilterAdjustmentLeavesTheSpotlightedBlocUntouched() {
            // The spotlighted bloc draws at full strength however the recede is set, so it stands
            // out against the muted background - neither the view's own adjustment nor the shared
            // recede touches it.
            assertThat(OwnerStyleResolver.resolveFilterAdjustment(
                    true,
                    new ElementStyleAdjustment(0.9, false),
                    new ElementStyleAdjustment(0.3, true)))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void resolveFilterAdjustmentRecedesEveryOtherBlocByTheSharedRecede() {
            // A non-spotlighted bloc the view does not adjust takes the pass's shared recede whole,
            // so the sector fades to a muted background the spotlight reads against.
            var recede = new ElementStyleAdjustment(0.3, true);

            assertThat(OwnerStyleResolver.resolveFilterAdjustment(
                    false,
                    ElementStyleAdjustment.NONE,
                    recede))
                .isEqualTo(recede);
        }

        @Test
        void resolveFilterAdjustmentUnionsTheViewRecedeWithTheSharedRecede() {
            // A bloc the view already recedes of its own accord and the filter also recedes takes
            // the union - strongest mute, either desaturate - applied once,
            // so it never mutes twice by compounding the two multipliers.
            var viewRecede = new ElementStyleAdjustment(0.5, false);
            var sharedRecede = new ElementStyleAdjustment(0.3, true);

            assertThat(OwnerStyleResolver.resolveFilterAdjustment(false, viewRecede, sharedRecede))
                .isEqualTo(new ElementStyleAdjustment(0.3, true));
        }
    }

    @Nested
    class ResolveBlocStyleDecision {

        @Test
        void resolveBlocStyleDecisionKeepsTheViewsIndependentStyleForANonSpotlitBlocUnderFilter() {
            // The single decision the fills and the labels both read, so pinning it here pins both.
            // Under a filter only the ADJUSTMENT is filter-driven; the base-style decision stays the
            // view's, so independent-held space keeps its independent style rather than snapping to
            // the faction style when a filter turns on. The stub says independent, so a true result
            // proves the filter left the view's base-style call live.
            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                "independent",
                buildViewMockDeciding(true, ElementStyleAdjustment.NONE),
                HolderGrouping.identity(),
                ContentInputsFixtures.createInputsRecedingBehind("tritachyon", new ElementStyleAdjustment(0.3, true)));

            assertThat(decision.usesIndependentStyle())
                .isTrue();
        }

        @Test
        void resolveBlocStyleDecisionUnionsTheViewRecedeWithTheSharedRecedeUnderFilter() {
            // A non-spotlit bloc the view already recedes of its own accord and the filter recedes
            // too takes the union - strongest mute, either desaturate -
            // once, so a receded name still cannot drift from its receded fill and neither mutes
            // twice by compounding the two multipliers.
            var viewRecede = new ElementStyleAdjustment(0.5, false);
            var sharedRecede = new ElementStyleAdjustment(0.3, true);
            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                "hegemony",
                buildViewMockDeciding(false, viewRecede),
                HolderGrouping.identity(),
                ContentInputsFixtures.createInputsRecedingBehind("tritachyon", sharedRecede));

            assertThat(decision.usesIndependentStyle())
                .isFalse();
            assertThat(decision.adjustment())
                .isEqualTo(new ElementStyleAdjustment(0.3, true));
        }

        @Test
        void resolveBlocStyleDecisionOffersTheUnionedRecedeToTheViewsStyleTestUnderFilter() {
            // The style test sees the union the bloc actually paints under, not the view's own recede
            // alone, so a view keying its bundle off desaturation cannot disagree with the palette -
            // which resolves from this same adjustment. Here only the shared recede desaturates: the
            // view's style test must still be offered a desaturating adjustment, or a bloc would paint
            // in the desaturation palette while keeping the faction bundle.
            var viewMock = buildViewMockDeciding(false, new ElementStyleAdjustment(0.5, false));

            OwnerStyleResolver.resolveBlocStyleDecision(
                "hegemony",
                viewMock,
                HolderGrouping.identity(),
                ContentInputsFixtures.createInputsRecedingBehind("tritachyon", new ElementStyleAdjustment(0.3, true)));

            verify(viewMock)
                .shouldUseIndependentStyle(
                    "hegemony",
                    HolderGrouping.identity(),
                    new ElementStyleAdjustment(0.3, true));
        }

        @Test
        void resolveBlocStyleDecisionOffersTheUntouchedAdjustmentToTheViewsStyleTestOffFilter() {
            // Off filter the view's own adjustment is what the bloc paints under, so that is what its
            // style test reads - the same "bundle and palette agree" rule, with nothing to union in.
            var adjustment = new ElementStyleAdjustment(0.5, true);
            var viewMock = buildViewMockDeciding(true, adjustment);

            OwnerStyleResolver.resolveBlocStyleDecision(
                "pirates",
                viewMock,
                HolderGrouping.identity(),
                ContentInputsFixtures.createInputsRecedingBehind(null, ElementStyleAdjustment.NONE));

            verify(viewMock)
                .shouldUseIndependentStyle(
                    "pirates",
                    HolderGrouping.identity(),
                    adjustment);
        }

        @Test
        void resolveBlocStyleDecisionDelegatesToTheViewOffFilter() {
            // Off filter the decision is the active view's own call, unchanged: its independent-
            // recede test and its per-bloc adjustment, so a normal pass styles exactly as before.
            var adjustment = new ElementStyleAdjustment(0.5, true);
            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                "pirates",
                buildViewMockDeciding(true, adjustment),
                HolderGrouping.identity(),
                ContentInputsFixtures.createInputsRecedingBehind(null, ElementStyleAdjustment.NONE));

            assertThat(decision.usesIndependentStyle())
                .isTrue();
            assertThat(decision.adjustment())
                .isSameAs(adjustment);
        }
    }
}
