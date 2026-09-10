package kmu.mods.nexerelin.alliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AllianceFingerprint}: the token is stable for a fixed membership and moves
 * only when an alliance forms, dissolves, or gains or loses a member. Order must not
 * matter - neither the order Nexerelin reports alliances in nor the market-size order of
 * their members - so a pure reshuffle reads as no change and never churns the map. Built
 * on hand-made records so the token is proven without a live Nexerelin.
 */
class AllianceFingerprintTest {

    @Nested
    class Compute {

        @Test
        void yieldsTheSameTokenForAnUnchangedSet() {

            assertThat(AllianceFingerprint.compute(List.of(buildAlliedPowers())))
                .isEqualTo(AllianceFingerprint.compute(List.of(buildAlliedPowers())));
        }

        @Test
        void ignoresReorderOfMembersBelowTheLead() {
            // Same lead (hegemony), the two trailing members swapped: neither who is allied nor
            // the colour lead changed, so the token must not move.
            var oneOrder = new AllianceRecord(
                "alliance-1",
                "Allied Powers",
                List.of("hegemony", "astral_armada", "diktat"));

            var otherOrder = new AllianceRecord(
                "alliance-1",
                "Allied Powers",
                List.of("hegemony", "diktat", "astral_armada"));

            assertThat(AllianceFingerprint.compute(List.of(otherOrder)))
                .isEqualTo(AllianceFingerprint.compute(List.of(oneOrder)));
        }

        @Test
        void movesWhenTheLeadMemberSwaps() {
            // The same two members, a different dominant (element 0): the bloc's colour lead
            // swapped, so the token must move even though the membership set is identical.
            var relead = new AllianceRecord(
                "alliance-1",
                "Allied Powers",
                List.of("astral_armada", "hegemony"));

            assertThat(AllianceFingerprint.compute(List.of(relead)))
                .isNotEqualTo(AllianceFingerprint.compute(List.of(buildAlliedPowers())));
        }

        @Test
        void ignoresTheOrderAlliancesAreReportedIn() {

            var first = List.of(buildAlliedPowers(), buildRivalBloc());
            var second = List.of(buildRivalBloc(), buildAlliedPowers());

            assertThat(AllianceFingerprint.compute(second))
                .isEqualTo(AllianceFingerprint.compute(first));
        }

        @Test
        void movesWhenAnAllianceGainsAMember() {

            var grown = new AllianceRecord(
                "alliance-1",
                "Allied Powers",
                List.of("hegemony", "astral_armada", "diktat"));

            assertThat(AllianceFingerprint.compute(List.of(grown)))
                .isNotEqualTo(AllianceFingerprint.compute(List.of(buildAlliedPowers())));
        }

        @Test
        void movesWhenAnAllianceLosesAMember() {

            var shrunk = new AllianceRecord(
                "alliance-1",
                "Allied Powers",
                List.of("hegemony"));

            assertThat(AllianceFingerprint.compute(List.of(shrunk)))
                .isNotEqualTo(AllianceFingerprint.compute(List.of(buildAlliedPowers())));
        }

        @Test
        void movesWhenAnAllianceForms() {

            assertThat(AllianceFingerprint.compute(List.of(buildAlliedPowers(), buildRivalBloc())))
                .isNotEqualTo(AllianceFingerprint.compute(List.of(buildAlliedPowers())));
        }

        @Test
        void movesWhenAnAllianceDissolves() {

            assertThat(AllianceFingerprint.compute(List.of()))
                .isNotEqualTo(AllianceFingerprint.compute(List.of(buildAlliedPowers())));
        }
    }

    // Two members ranked hegemony-first; reused across the steady-token and membership-move
    // assertions.
    private static AllianceRecord buildAlliedPowers() {

        return new AllianceRecord(
            "alliance-1",
            "Allied Powers",
            List.of("hegemony", "astral_armada"));
    }

    // A second, distinct alliance, so forming and reordering can be probed against
    // alliedPowers.
    private static AllianceRecord buildRivalBloc() {

        return new AllianceRecord(
            "alliance-2",
            "Rival Bloc",
            List.of("tritachyon", "luddic_church"));
    }
}
