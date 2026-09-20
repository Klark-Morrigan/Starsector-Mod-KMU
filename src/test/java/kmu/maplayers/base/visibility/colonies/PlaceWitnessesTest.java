package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.impl.campaign.ids.Factions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.buildAllianceOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins who a place's settlers would speak about - the owner comparison, the alliance that widens
 * it, and what each reads as when the set or the alliances are missing.
 *
 * <p>Posed on bare faction IDs rather than on colonies, which is the whole point of the value: the
 * question is about who stands here and who holds the thing being spoken of, and nothing about it
 * needs a market, a system or a rule around it.
 */
final class PlaceWitnessesTest {

    private static final String ALLY = "hegemony";
    private static final String OWNER = "pirates";
    private static final String RIVAL = "tritachyon";

    @Nested
    class WouldSpeakAbout {

        @Test
        void answersTrueForARivalStandingHere() {
            // A faction that is not keeping the secret has every reason to mention what is sitting
            // in the system with it.
            assertThat(buildWitnesses(RIVAL).wouldSpeakAbout(OWNER))
                .isTrue();
        }

        @Test
        void answersFalseForTheOwnersOwnColony() {
            // The people keeping a secret are exactly the ones a faction-blind test credits with
            // telling it, so a base in a system its own faction openly holds is spoken for by
            // nobody.
            assertThat(buildWitnesses(OWNER).wouldSpeakAbout(OWNER))
                .isFalse();
        }

        @Test
        void answersFalseForAPartnerOfTheOwner() {
            // An alliance is a standing arrangement to act as one, and handing a partner's
            // concealed base to a third party is the thing it forbids.
            assertThat(new PlaceWitnesses(Set.of(ALLY), buildAllianceOf(OWNER, ALLY))
                    .wouldSpeakAbout(OWNER))
                .isFalse();
        }

        @Test
        void answersTrueForOneUnalliedSettlerAmongPartners() {
            // Asked of the whole set rather than of whichever settler is reached first: one faction
            // outside the alliance is enough, however many partners stand around it.
            assertThat(new PlaceWitnesses(Set.of(ALLY, RIVAL), buildAllianceOf(OWNER, ALLY))
                    .wouldSpeakAbout(OWNER))
                .isTrue();
        }

        @Test
        void answersTrueForAPartnerOfSomebodyElse() {
            // An alliance the owner is not in silences nobody, so the comparison answers exactly as
            // it does with no alliances at all.
            assertThat(new PlaceWitnesses(Set.of(RIVAL), buildAllianceOf(ALLY, RIVAL))
                    .wouldSpeakAbout(OWNER))
                .isTrue();
        }

        @Test
        void answersFalseWhereNobodyIsStandingHere() {
            assertThat(PlaceWitnesses.NONE.wouldSpeakAbout(OWNER))
                .isFalse();
        }

        @Test
        void answersTrueForAColonyHeldByNobodyWhereAFactionStandsHere() {
            // A derelict names no owner, so every settler here is somebody else and speaks for it.
            assertThat(buildWitnesses(RIVAL).wouldSpeakAbout(null))
                .isTrue();
        }

        @Test
        void answersFalseForAColonyHeldByNobodyAmongSettlersHeldByNobody() {
            // The one arrangement in which the comparison withholds with no owner to compare: a
            // station no faction holds that the economy lists anyway settles its place, and falls
            // to the same nobody the derelict beside it does.
            assertThat(buildWitnesses(Factions.NEUTRAL).wouldSpeakAbout(Factions.NEUTRAL))
                .isFalse();
        }

        @Test
        void answersTrueForASettlerWhoseOwnerCarriesNoId() {
            // A faction standing here is a witness whatever the game failed to call it, so the fold
            // keeps it and the comparison reads its missing name like any other.
            var settlingOwnerIds = new HashSet<String>();

            settlingOwnerIds.add(null);

            assertThat(new PlaceWitnesses(settlingOwnerIds, FactionAlliances.NONE)
                    .wouldSpeakAbout(OWNER))
                .isTrue();
        }

        @Test
        void readsAbsentAlliancesAsNobodyStandingTogether() {
            // What an install without the mod that keeps alliances answers: the bare owner
            // comparison, unwidened.
            assertThat(new PlaceWitnesses(Set.of(ALLY), null).wouldSpeakAbout(OWNER))
                .isTrue();
        }

        @Test
        void readsAnAbsentSettlerSetAsNobodyStandingHere() {
            assertThat(new PlaceWitnesses(null, FactionAlliances.NONE).wouldSpeakAbout(OWNER))
                .isFalse();
        }
    }

    @Nested
    class SettlingOwnerIds {

        @Test
        void keepsACopyTheCallerCannotChangeAfterwards() {
            // Folded once for a place and spent by every colony judged there, so a set that went on
            // moving underneath would have two colonies in one place answer under different
            // witnesses.
            var settlingOwnerIds = new HashSet<String>();

            settlingOwnerIds.add(RIVAL);

            var witnesses = new PlaceWitnesses(settlingOwnerIds, FactionAlliances.NONE);

            settlingOwnerIds.add(OWNER);

            assertThat(witnesses.settlingOwnerIds())
                .containsExactly(RIVAL);
        }

        @Test
        void refusesToBeChangedThroughTheValueItself() {

            var witnesses = buildWitnesses(RIVAL);

            assertThatThrownBy(() -> witnesses.settlingOwnerIds().add(OWNER))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // Somebody standing in the place, among no alliances at all - the sector's ordinary shape, and
    // what every case poses unless the alliance is the thing it is about.
    private static PlaceWitnesses buildWitnesses(String... settlingOwnerIds) {
        return new PlaceWitnesses(Set.of(settlingOwnerIds), FactionAlliances.NONE);
    }
}
