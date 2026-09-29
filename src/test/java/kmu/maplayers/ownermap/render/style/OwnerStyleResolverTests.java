package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the shared per-owner style decision the fills and the cluster-name labels both read: the
 * filter-mode adjustment an owner takes (spotlight untouched, everyone else receding by the union
 * of the reading's recede and the shared recede) and the whole decision it feeds - the category the
 * reading names, except for the spotlighted owner, which the layer's full-strength category holds.
 */
final class OwnerStyleResolverTests {

    // A reading answering both per-owner style questions with fixed values, so a case can prove
    // whether the decision consulted the reading or bypassed it.
    private static OwnerReading buildReadingMockDeciding(
            OwnerMapCategory category,
            ElementStyleAdjustment adjustment) {

        var readingMock = mock(OwnerReading.class);

        when(readingMock.resolveCategory(any(), any()))
            .thenReturn(category);
        when(readingMock.resolveStyleAdjustment(any(), any()))
            .thenReturn(adjustment);

        return readingMock;
    }

    @Nested
    class ResolveFilterAdjustment {

        @Test
        void leavesTheSpotlightedOwnerUntouched() {
            // The spotlighted owner draws at full strength however the recede is set, so it stands
            // out against the muted background - neither the reading's own adjustment nor the shared
            // recede touches it.
            assertThat(OwnerStyleResolver.resolveFilterAdjustment(
                    true,
                    new ElementStyleAdjustment(0.9, false),
                    new ElementStyleAdjustment(0.3, true)))
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void recedesEveryOtherOwnerByTheSharedRecede() {
            // A non-spotlighted owner the reading does not adjust takes the pass's shared recede
            // whole, so the sector fades to a muted background the spotlight reads against.
            var recede = new ElementStyleAdjustment(0.3, true);

            assertThat(OwnerStyleResolver.resolveFilterAdjustment(
                    false,
                    ElementStyleAdjustment.NONE,
                    recede))
                .isEqualTo(recede);
        }

        @Test
        void unionsTheOwnerRecedeWithTheSharedRecede() {
            // An owner the layer already recedes of its own accord and the filter also recedes takes
            // the union - strongest mute, either desaturate - applied once, so it never mutes twice
            // by compounding the two multipliers.
            var ownerRecede = new ElementStyleAdjustment(0.5, false);
            var sharedRecede = new ElementStyleAdjustment(0.3, true);

            assertThat(OwnerStyleResolver.resolveFilterAdjustment(false, ownerRecede, sharedRecede))
                .isEqualTo(new ElementStyleAdjustment(0.3, true));
        }
    }

    @Nested
    class ResolveBlocStyleDecision {

        @Test
        void keepsTheReadingsCategoryForANonSpotlitOwnerUnderFilter() {
            // The single decision the fills and the labels both read, so pinning it here pins both.
            // Under a filter only the ADJUSTMENT is filter-driven; the category stays the reading's,
            // so an owner the layer recedes keeps its quieter category rather than snapping to full
            // strength when a filter turns on.
            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                "independent",
                buildReadingMockDeciding(OwnerMapCategory.INDEPENDENT, ElementStyleAdjustment.NONE),
                HolderCategories.INSTANCE,
                ContentInputsFixtures.createInputsRecedingBehind(
                    "tritachyon",
                    new ElementStyleAdjustment(0.3, true)));

            assertThat(decision.category())
                .isEqualTo(OwnerMapCategory.INDEPENDENT);
        }

        @Test
        void holdsTheSpotlitOwnerInTheFullStrengthCategoryUnderFilter() {
            // The spotlit key never inherits the reading's category, which the reading's own
            // desaturate would otherwise trip: it draws in the category the layer declares full
            // strength, whatever the reading would have said.
            var readingMock = buildReadingMockDeciding(
                OwnerMapCategory.INDEPENDENT,
                new ElementStyleAdjustment(0.5, true));

            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                SpotlitBlocs.readSpotlitBlocKey(),
                readingMock,
                HolderCategories.INSTANCE,
                ContentInputsFixtures.createInputsRecedingBehind(
                    "tritachyon",
                    new ElementStyleAdjustment(0.3, true)));

            assertThat(decision)
                .isEqualTo(new OwnerStyleDecision(OwnerMapCategory.FACTION, ElementStyleAdjustment.NONE));
            verify(readingMock, never()).resolveCategory(any(), any());
        }

        @Test
        void unionsTheOwnerRecedeWithTheSharedRecedeUnderFilter() {
            // A non-spotlit owner the layer already recedes of its own accord and the filter recedes
            // too takes the union - strongest mute, either desaturate - once, so a receded name still
            // cannot drift from its receded fill and neither mutes twice by compounding the two
            // multipliers.
            var ownerRecede = new ElementStyleAdjustment(0.5, false);
            var sharedRecede = new ElementStyleAdjustment(0.3, true);
            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                "hegemony",
                buildReadingMockDeciding(OwnerMapCategory.FACTION, ownerRecede),
                HolderCategories.INSTANCE,
                ContentInputsFixtures.createInputsRecedingBehind("tritachyon", sharedRecede));

            assertThat(decision)
                .isEqualTo(new OwnerStyleDecision(
                    OwnerMapCategory.FACTION,
                    new ElementStyleAdjustment(0.3, true)));
        }

        @Test
        void offersTheUnionedRecedeToTheReadingsCategoryUnderFilter() {
            // The category is asked with the union the owner actually paints under, not the
            // reading's own recede alone, so a layer keying its category off desaturation cannot
            // disagree with the palette - which resolves from this same adjustment. Here only the
            // shared recede desaturates: the reading must still be offered a desaturating adjustment,
            // or an owner would paint in the desaturation palette while keeping full strength.
            var readingMock = buildReadingMockDeciding(
                OwnerMapCategory.FACTION,
                new ElementStyleAdjustment(0.5, false));

            OwnerStyleResolver.resolveBlocStyleDecision(
                "hegemony",
                readingMock,
                HolderCategories.INSTANCE,
                ContentInputsFixtures.createInputsRecedingBehind(
                    "tritachyon",
                    new ElementStyleAdjustment(0.3, true)));

            verify(readingMock)
                .resolveCategory("hegemony", new ElementStyleAdjustment(0.3, true));
        }

        @Test
        void offersTheUntouchedAdjustmentToTheReadingsCategoryOffFilter() {
            // Off filter the reading's own adjustment is what the owner paints under, so that is what
            // its category reads - the same "bundle and palette agree" rule, with nothing to union in.
            var adjustment = new ElementStyleAdjustment(0.5, true);
            var readingMock = buildReadingMockDeciding(OwnerMapCategory.INDEPENDENT, adjustment);

            OwnerStyleResolver.resolveBlocStyleDecision(
                "pirates",
                readingMock,
                HolderCategories.INSTANCE,
                ContentInputsFixtures.createInputsRecedingBehind(null, ElementStyleAdjustment.NONE));

            verify(readingMock)
                .resolveCategory("pirates", adjustment);
        }

        @Test
        void takesTheReadingsAnswersOffFilter() {
            // Off filter the decision is the layer's own call, unchanged: its category and its
            // per-owner adjustment, so a normal pass styles exactly as the layer says.
            var adjustment = new ElementStyleAdjustment(0.5, true);
            var decision = OwnerStyleResolver.resolveBlocStyleDecision(
                "pirates",
                buildReadingMockDeciding(OwnerMapCategory.INDEPENDENT, adjustment),
                HolderCategories.INSTANCE,
                ContentInputsFixtures.createInputsRecedingBehind(null, ElementStyleAdjustment.NONE));

            assertThat(decision.category())
                .isEqualTo(OwnerMapCategory.INDEPENDENT);
            assertThat(decision.adjustment())
                .isSameAs(adjustment);
        }
    }
}
