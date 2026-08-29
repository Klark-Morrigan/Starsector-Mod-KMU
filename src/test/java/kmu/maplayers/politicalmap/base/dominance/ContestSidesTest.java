package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the split every surface reporting a contest routes its blocks by: the contestants standing
 * with the holder against the rest, in the order they arrived.
 *
 * <p>Posed over plain bloc ids, since what a surface lists a contestant as is its own business and
 * the split reads nothing of one but the bloc it names.
 */
class ContestSidesTest {

    private static final String HOLDER = "hegemony";
    private static final String ALLY = "astral_armada";
    private static final String RIVAL = "tritachyon";
    private static final String OUTSIDER = "pirates";

    // What names a bloc when the contestant is the bloc id itself.
    private static final Function<String, String> BLOC_ID_ITSELF = Function.identity();

    @Nested
    class SelectSide {

        @Test
        void selectSideKeepsOnlyTheHoldersOwnAlliesOnTheAlliedSide() {

            assertThat(buildSidesUnderAnAlliance()
                    .selectSide(ContestSide.ALLIED, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .containsExactly(ALLY);
        }

        @Test
        void selectSideDropsTheHoldersOwnAlliesFromTheRivalSide() {
            // The two sides take every contestant between them and none twice, which is what makes
            // them one split rather than two questions asked of the same list.
            assertThat(buildSidesUnderAnAlliance()
                    .selectSide(ContestSide.RIVAL, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .containsExactly(RIVAL);
        }

        @Test
        void selectSideKeepsTheOrderTheContestantsArrivedIn() {
            // The caller hands them over ranked - strongest first on a standings box, the mechanic's
            // own order on a claims box - and a block reads in that order rather than in one this
            // split invented.
            var secondAlly = "luddic_church";

            assertThat(new ContestSides(
                    HOLDER,
                    new BlocAffiliation(
                        HolderGroupingFixture.buildAllianceOf(HOLDER, secondAlly, ALLY)))
                    .selectSide(ContestSide.ALLIED, List.of(secondAlly, ALLY), BLOC_ID_ITSELF))
                .containsExactly(secondAlly, ALLY);
        }

        @Test
        void selectSideKeepsTwoBlocsAlliedWithEachOtherButNotWithTheHolderAsRivals() {
            // Only the holder's own allies leave the rival side. Two rivals standing together still
            // both stand against the holder, which is the relation a contest block states.
            assertThat(new ContestSides(
                    HOLDER,
                    new BlocAffiliation(HolderGroupingFixture.buildAllianceOf(RIVAL, OUTSIDER)))
                    .selectSide(ContestSide.RIVAL, List.of(RIVAL, OUTSIDER), BLOC_ID_ITSELF))
                .containsExactly(RIVAL, OUTSIDER);
        }

        @Test
        void selectSideAlliesNobodyWhereNothingGroupsFactions() {
            // The install without the mod that supplies alliances: no two blocs stand together, so
            // the allied block is empty and its heading is dropped by the surface drawing it.
            assertThat(new ContestSides(HOLDER, BlocAffiliation.NONE)
                    .selectSide(ContestSide.ALLIED, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .isEmpty();
        }

        @Test
        void selectSideKeepsEverybodyAsRivalsWhereNothingGroupsFactions() {
            // Why an install without alliances needs no branch anywhere: every contestant routes
            // exactly as it did before either side existed.
            assertThat(new ContestSides(HOLDER, BlocAffiliation.NONE)
                    .selectSide(ContestSide.RIVAL, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .containsExactly(ALLY, RIVAL);
        }

        @Test
        void selectSideAlliesNobodyWhereNobodyHoldsTheSystem() {
            // An unheld system stands nobody with anybody - there is no holder to be allied with -
            // so a heading naming one never draws over a system without one.
            assertThat(new ContestSides(null, buildAllianceAffiliation())
                    .selectSide(ContestSide.ALLIED, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .isEmpty();
        }

        @Test
        void selectSideKeepsEverybodyAsRivalsWhereNobodyHoldsTheSystem() {

            assertThat(new ContestSides(null, buildAllianceAffiliation())
                    .selectSide(ContestSide.RIVAL, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .containsExactly(ALLY, RIVAL);
        }
    }

    // The holder allied with one bloc and not the other, so one instance poses both sides at once.
    private static ContestSides buildSidesUnderAnAlliance() {
        return new ContestSides(HOLDER, buildAllianceAffiliation());
    }

    private static BlocAffiliation buildAllianceAffiliation() {
        return new BlocAffiliation(HolderGroupingFixture.buildAllianceOf(HOLDER, ALLY));
    }
}
