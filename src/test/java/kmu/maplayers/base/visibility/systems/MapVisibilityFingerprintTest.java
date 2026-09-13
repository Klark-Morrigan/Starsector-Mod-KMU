package kmu.maplayers.base.visibility.systems;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the drawn-set fingerprint: what one system contributes to the scalar a poll
 * compares. A pure fold over a key and a flag, so the cases state keys directly rather than posing
 * a sector to read them off.
 *
 * <p>Every case is about two systems failing to cancel. A fingerprint is a sum, so what breaks it
 * is two systems contributing the same value - one entering the drawn set as the other leaves then
 * moves nothing, and the map goes on drawing what it last built. The pairs below are the ones that
 * can really arise: two systems sharing an ID, the same system drawn live and drawn collapsed, and
 * a system the sector states nothing at all about.
 */
class MapVisibilityFingerprintTest {

    // The draw class of a system shown as a live colony, and of one shown only as a collapsed
    // colony the player has been told of. Named because the flag is what the whole salt exists
    // for, and a bare true at a call site says nothing about which of the two a case poses.
    private static final boolean DRAWN_LIVE = false;
    private static final boolean DRAWN_AS_A_REVEALED_RUIN = true;

    // The key of a system the sector states nothing about - no ID, no centre, no anchor. Every arm
    // hashes to zero and the avalanche's one fixed point is zero, so this is the key whose whole
    // fold is zero, and the one the seed exists to lift off it.
    private static final SystemKey KEY_STATING_NOTHING = new SystemKey("", "", "");

    @Nested
    class ComputeSystemContribution {

        @Test
        void contributionsDifferBetweenSystems() {
            // Distinct IDs must land distinct contributions so two systems do not cancel when
            // summed into the fingerprint.
            assertThat(computeContributionOf(buildKeyOfIdAlone("a")))
                .isNotEqualTo(computeContributionOf(buildKeyOfIdAlone("b")));
        }

        @Test
        void contributionsDifferBetweenTwoSystemsSharingAnId() {
            // The collision the key exists for: a sector holds two systems answering to one ID,
            // told apart only by the entities they are built around. Keyed by ID both would
            // contribute the same value, so one entering the drawn set as the other left would
            // leave the fingerprint standing still.
            var firstOfThePair = new SystemKey("a", "centre-1", "anchor-1");
            var secondOfThePair = new SystemKey("a", "centre-2", "anchor-2");

            assertThat(computeContributionOf(firstOfThePair))
                .isNotEqualTo(computeContributionOf(secondOfThePair));
        }

        @Test
        void contributionsDifferForAPairWhoseArmsShiftEachOtherBack() {
            // The pair a linear fold cannot tell apart, in the shape a live sector holds it: one
            // ID, procgen centre names one character apart, and short engine-minted anchor ids.
            // String hashes are linear in their characters, so the centres' hashes differ by 1 and
            // the anchors' by exactly 31 the other way - a fold weighting the centre arm by 31
            // would move one arm by what the other moves back, and hand both systems one value.
            var centredOnTheThirdStar = new SystemKey("deep space", "deep_space_star_3", "8c3");
            var centredOnTheFourthStar = new SystemKey("deep space", "deep_space_star_4", "8b3");

            assertThat("8c3".hashCode() - "8b3".hashCode())
                .isEqualTo(31 * ("deep_space_star_4".hashCode() - "deep_space_star_3".hashCode()));
            assertThat(computeContributionOf(centredOnTheThirdStar))
                .isNotEqualTo(computeContributionOf(centredOnTheFourthStar));
        }

        @Test
        void contributionsDifferWhenOneIdMovesBetweenTheArms() {
            // The arms are folded by position, so an entity ID standing as one system's centre
            // and another's anchor tells the two apart. Folded without position they would read
            // as one system and the pair would share a contribution.
            var centredOnTheEntity = new SystemKey("a", "shared-entity", "");
            var anchoredToIt = new SystemKey("a", "", "shared-entity");

            assertThat(computeContributionOf(centredOnTheEntity))
                .isNotEqualTo(computeContributionOf(anchoredToIt));
        }

        @Test
        void contributionShiftsWhenASystemBecomesDecivilised() {
            // A live-to-dead flip on the same system - its draw class changing while it stays on
            // the map - must move its contribution via the deciv salt.
            var system = buildKeyOfIdAlone("a");

            assertThat(MapVisibilityFingerprint.computeSystemContribution(
                    system,
                    DRAWN_AS_A_REVEALED_RUIN))
                .isNotEqualTo(computeContributionOf(system));
        }

        @Test
        void contributionIsNonZeroForAKeyThatFoldsToZero() {
            // The avalanche has a fixed point at 0, so an unseeded 0-fold key would contribute 0
            // and be invisible to the summed fingerprint - the system could enter or leave the map
            // without moving it. The seed spreads it to a non-zero value.
            assertThat("".hashCode())
                .isZero();
            assertThat(computeContributionOf(KEY_STATING_NOTHING))
                .isNotZero();
        }

        @Test
        void summedFingerprintMovesWhenAZeroFoldSystemJoinsTheDrawnSet() {
            // The fingerprint is a sum of contributions, so a system joining the drawn set must
            // change it - including a 0-fold key, whose contribution has to be non-zero for its
            // arrival to register in the sum.
            var before = computeContributionOf(buildKeyOfIdAlone("a"));
            var after = before + computeContributionOf(KEY_STATING_NOTHING);

            assertThat(after)
                .isNotEqualTo(before);
        }

        @Test
        void contributionShiftsWhenAZeroFoldSystemBecomesDecivilised() {
            // A draw-class flip must move the contribution even for a 0-fold key, so a live-to-dead
            // change on such a system still moves the summed fingerprint rather than reading
            // identically live and dead.
            assertThat(MapVisibilityFingerprint.computeSystemContribution(
                    KEY_STATING_NOTHING,
                    DRAWN_AS_A_REVEALED_RUIN))
                .isNotEqualTo(computeContributionOf(KEY_STATING_NOTHING));
        }
    }

    // What a live system contributes, which is what nearly every case here compares two of. Bound
    // once so a case reads as the pair of keys it poses and not as the flag they share.
    private static int computeContributionOf(SystemKey systemKey) {
        return MapVisibilityFingerprint.computeSystemContribution(systemKey, DRAWN_LIVE);
    }

    // The key of a system carrying neither a centre nor an anchor, so the ID is the whole of what
    // tells it apart. Minted rather than read off a staged system: a case about the fold states
    // the arms it means, and one taking them from the read under test would expect nothing.
    private static SystemKey buildKeyOfIdAlone(String systemId) {
        return new SystemKey(systemId, "", "");
    }
}
