package kmu.maplayers.politicalmap.base;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a bloc answers the framework's picker seam. Everything else about the record is its own
 * components; what needs pinning is the one value the seam renames - the id the filter stores -
 * since a bloc listed under the seam and the same bloc resolved into territory must be keyed by the
 * same string or a spotlight would light a row it cannot paint.
 */
final class SelectableBlocTest {

    @Nested
    class ItemId {

        @Test
        void itemIdIsTheBlocId() {
            var bloc = new SelectableBloc(
                "hegemony",
                "Hegemony",
                "crest_heg",
                new DominanceStats(5, 8, 40, 12));

            assertThat(bloc.itemId())
                .isEqualTo("hegemony");
        }
    }
}
