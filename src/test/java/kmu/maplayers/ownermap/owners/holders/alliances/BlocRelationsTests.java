package kmu.maplayers.ownermap.owners.holders.alliances;

import kmlib.starsector.factions.relation.FactionSetFriendliness;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.buildAllianceOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link BlocRelations}: both axes are required, and the two questions every box routing
 * against the pair asks are answered off it.
 *
 * <p>A box files every bloc it lists against both, so a pair sampled with one missing would fault
 * part-way through a box rather than at the point that failed to sample it.
 */
final class BlocRelationsTests {

    private static final String HOLDER = "hegemony";
    private static final String ALLY = "astral_armada";
    private static final String RIVAL = "tritachyon";

    // What names a bloc when the contestant is the bloc ID itself.
    private static final Function<String, String> BLOC_ID_ITSELF = Function.identity();

    // The one warm pair these cases pose: the rival thinks well of the holder.
    private static final FactionSetFriendliness RIVAL_WARM_TO_HOLDER = new FactionSetFriendliness(
        (factionId, otherFactionId) -> RIVAL.equals(factionId) && HOLDER.equals(otherFactionId));

    @Nested
    class Constructor {

        @Test
        void refusesAMissingAffiliation() {

            assertThatThrownBy(() -> new BlocRelations(null, mock(FactionSetFriendliness.class)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("blocAffiliation");
        }

        @Test
        void refusesAMissingFriendliness() {

            assertThatThrownBy(() -> new BlocRelations(mock(BlocAffiliation.class), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("factionSetFriendliness");
        }
    }

    @Nested
    class SelectSide {

        @Test
        void placesTheHoldersOwnAlliesOnTheAlliedSide() {

            assertThat(buildRelations()
                    .selectSide(ContestSide.ALLIED, HOLDER, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .containsExactly(ALLY);
        }

        @Test
        void placesEveryoneElseOnTheRivalSide() {

            assertThat(buildRelations()
                    .selectSide(ContestSide.RIVAL, HOLDER, List.of(ALLY, RIVAL), BLOC_ID_ITSELF))
                .containsExactly(RIVAL);
        }
    }

    @Nested
    class IsFactionFriendlyWith {

        @Test
        void answersYesForAFactionWarmToTheHolder() {

            assertThat(buildRelations().isFactionFriendlyWith(RIVAL, Set.of(HOLDER)))
                .isTrue();
        }

        @Test
        void answersNoForAFactionThatIsNot() {

            assertThat(buildRelations().isFactionFriendlyWith(ALLY, Set.of(HOLDER)))
                .isFalse();
        }

        @Test
        void answersNoWhereNobodyHoldsTheSystem() {
            // An unheld system names nobody to be friendly with, so a heading stating goodwill never
            // draws over one.
            assertThat(buildRelations().isFactionFriendlyWith(RIVAL, Set.of()))
                .isFalse();
        }
    }

    // The holder allied with one bloc, and the other bloc warm toward it.
    private static BlocRelations buildRelations() {

        return new BlocRelations(
            new BlocAffiliation(buildAllianceOf(HOLDER, ALLY)),
            RIVAL_WARM_TO_HOLDER);
    }
}
