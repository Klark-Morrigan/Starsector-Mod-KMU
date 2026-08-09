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
 * bloc's identity and a spotlight resolved from another's id would light a row the map cannot paint,
 * and the metrics for whether the row reads back, since the identity has no number to judge that by.
 */
final class RankedBlocTest {

    // Any metrics stand in: the seam never opens the stats half, so its values are immaterial here.
    private static final DominanceStats ANY_STATS = new DominanceStats(5, 8, 40, 12);

    // One fully-populated bloc every case reads a different seam value off, so a delegation that
    // crossed its wires (a crest answered from the id, say) shows up as a mismatch rather than
    // passing against a fixture built to suit it.
    private static final RankedBloc<DominanceStats> HEGEMONY = new RankedBloc<>(
        new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
        ANY_STATS);

    @Nested
    class ItemId {

        @Test
        void itemIdIsTheBlocId() {
            // The id is the one value the seam renames, and it must stay the bloc id the filter stores.
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
            // differ on it purely by what their numbers say. Run over the metrics that actually
            // declare a rule (no claims, so the row reads back), since a payload taking the default
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
        void isDimmedIsFalseForMetricsThatDeclareNoRule() {
            // A layer whose blocs are all equally worth spotlighting states nothing, so its rows
            // read at full strength - which is what keeps the held layers unchanged by any of this.
            assertThat(HEGEMONY.isDimmed())
                .isFalse();
        }
    }
}
