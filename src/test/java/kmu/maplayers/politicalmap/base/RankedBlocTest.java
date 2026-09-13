package kmu.maplayers.politicalmap.base;

import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a ranked bloc answers the framework's picker seam. The stats half is the calling view's own
 * and needs no pinning here; what needs pinning is each seam value reaching the picker from the half
 * that owns it - the identity for what a row draws and a pick reports, since a row drawn from one
 * bloc's identity and a spotlight resolved from another's ID would light a row the map cannot paint,
 * and the metrics for whether the row reads back, since the identity has no number to judge that by.
 */
final class RankedBlocTest {

    // Any metrics stand in: the three identity values never open the stats half, so its numbers are
    // immaterial to the cases that read them.
    private static final DominanceStats ANY_STATS = new DominanceStats(5, 8, 40, 12);

    // One fully-populated bloc every case reads a different seam value off, so a delegation that
    // crossed its wires (a crest answered from the ID, say) shows up as a mismatch rather than
    // passing against a fixture built to suit it.
    private static final RankedBloc<DominanceStats> HEGEMONY = new RankedBloc<>(
        new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
        ANY_STATS);

    @Nested
    class ItemId {

        @Test
        void itemIdIsTheBlocId() {
            // The ID is the one value the seam renames, and it must stay the bloc ID the filter stores.
            assertThat(HEGEMONY.itemId())
                .isEqualTo("hegemony");
        }
    }

    @Nested
    class DisplayName {

        @Test
        void displayNameIsTheIdentitysLabel() {

            assertThat(HEGEMONY.displayName())
                .isEqualTo("Hegemony");
        }
    }

    @Nested
    class CrestSpritePath {

        @Test
        void crestSpritePathIsTheIdentitysCrest() {

            assertThat(HEGEMONY.crestSpritePath())
                .isEqualTo("crest_heg");
        }
    }

    @Nested
    class IsDimmed {

        @Test
        void isDimmedIsAnsweredByTheMetricsRatherThanTheIdentity() {
            // The one seam value the identity cannot answer: two blocs sharing a name and a crest
            // differ on it purely by what their numbers say. Run over metrics stating a painting
            // rule they fail (no claims, so the row reads back), since a payload that states none
            // would pass whether the delegation ran or not.
            var claimless = new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                new ClaimStats(0, 40));

            assertThat(claimless.isDimmed())
                .isTrue();
        }

        @Test
        void isDimmedFollowsThePayloadBackToFullStrength() {
            // The same identity reads at full strength once its metrics say so, so the answer tracks
            // the payload rather than anything the row draws.
            var claimant = new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                new ClaimStats(2, 40));

            assertThat(claimant.isDimmed())
                .isFalse();
        }

        @Test
        void isDimmedIsFalseForMetricsThatStateNoPaintingRule() {
            // A picker whose rows are not painters is never asked, so its rows draw plain rather
            // than inheriting an answer to a question its payload cannot be asked. Run over a
            // payload outside the two painting vocabularies, since one of those would pass here
            // whether it was consulted or not.
            var measured = new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                new HazardRating(4, 1));

            assertThat(measured.isDimmed())
                .isFalse();
        }
    }
}
