package kmu.starsector.nexerelin;

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
            assertThat(AllianceFingerprint.compute(List.of(alliedPowers())))
                    .isEqualTo(AllianceFingerprint.compute(List.of(alliedPowers())));
        }

        @Test
        void ignoresReorderOfMembersBelowTheLead() {
            // Same lead (hegemony), the two trailing members swapped: neither who is allied nor
            // the colour lead changed, so the token must not move.
            var oneOrder = new AllianceRecord(
                    "alliance-1", "Allied Powers", List.of("hegemony", "astral_armada", "diktat"));
            var otherOrder = new AllianceRecord(
                    "alliance-1", "Allied Powers", List.of("hegemony", "diktat", "astral_armada"));
            assertThat(AllianceFingerprint.compute(List.of(otherOrder)))
                    .isEqualTo(AllianceFingerprint.compute(List.of(oneOrder)));
        }

        @Test
        void movesWhenTheLeadMemberSwaps() {
            // The same two members, a different dominant (element 0): the bloc's colour lead
            // swapped, so the token must move even though the membership set is identical.
            var relead = new AllianceRecord(
                    "alliance-1", "Allied Powers", List.of("astral_armada", "hegemony"));
            assertThat(AllianceFingerprint.compute(List.of(relead)))
                    .isNotEqualTo(AllianceFingerprint.compute(List.of(alliedPowers())));
        }

        @Test
        void ignoresTheOrderAlliancesAreReportedIn() {
            var first = List.of(alliedPowers(), rivalBloc());
            var second = List.of(rivalBloc(), alliedPowers());
            assertThat(AllianceFingerprint.compute(second))
                    .isEqualTo(AllianceFingerprint.compute(first));
        }

        @Test
        void movesWhenAnAllianceGainsAMember() {
            var grown = new AllianceRecord(
                    "alliance-1", "Allied Powers", List.of("hegemony", "astral_armada", "diktat"));
            assertThat(AllianceFingerprint.compute(List.of(grown)))
                    .isNotEqualTo(AllianceFingerprint.compute(List.of(alliedPowers())));
        }

        @Test
        void movesWhenAnAllianceLosesAMember() {
            var shrunk = new AllianceRecord(
                    "alliance-1", "Allied Powers", List.of("hegemony"));
            assertThat(AllianceFingerprint.compute(List.of(shrunk)))
                    .isNotEqualTo(AllianceFingerprint.compute(List.of(alliedPowers())));
        }

        @Test
        void movesWhenAnAllianceForms() {
            assertThat(AllianceFingerprint.compute(List.of(alliedPowers(), rivalBloc())))
                    .isNotEqualTo(AllianceFingerprint.compute(List.of(alliedPowers())));
        }

        @Test
        void movesWhenAnAllianceDissolves() {
            assertThat(AllianceFingerprint.compute(List.of()))
                    .isNotEqualTo(AllianceFingerprint.compute(List.of(alliedPowers())));
        }
    }

    // Two members ranked hegemony-first; reused across the steady-token and membership-move
    // assertions.
    private static AllianceRecord alliedPowers() {
        return new AllianceRecord(
                "alliance-1", "Allied Powers", List.of("hegemony", "astral_armada"));
    }

    // A second, distinct alliance, so forming and reordering can be probed against
    // alliedPowers.
    private static AllianceRecord rivalBloc() {
        return new AllianceRecord(
                "alliance-2", "Rival Bloc", List.of("tritachyon", "luddic_church"));
    }
}
