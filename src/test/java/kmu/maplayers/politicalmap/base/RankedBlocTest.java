package kmu.maplayers.politicalmap.base;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a ranked bloc answers the framework's picker seam. The stats half is the calling view's own
 * and needs no pinning here; what needs pinning is the identity half reaching the seam intact, since a
 * row drawn from one bloc's identity and a spotlight resolved from another's id would light a row the
 * map cannot paint.
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
}
