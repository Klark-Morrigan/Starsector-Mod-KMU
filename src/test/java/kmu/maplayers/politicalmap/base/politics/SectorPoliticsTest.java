package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Unit coverage for {@link SectorPolitics}'s pure grouping fold, kept apart from the economy-reading
 * pipeline exercised in {@link SectorPoliticsIntegrationTest}. {@link SectorPolitics#regroupByBloc} is
 * the single fold both the dominance-only footprint regroup and the picker's fuller contribution
 * regroup route through, so it is pinned here on hand-built inputs with a synthetic value type - a
 * plain {@code Integer} summed - to prove the collapse, the identity handling, and the order are the
 * fold's own behaviour, independent of any one value type's merge semantics.
 */
class SectorPoliticsTest {

    @Nested
    class RegroupByBloc {

        @Test
        void collapsesSameBlocFactionsThroughTheMerge() {
            // Two factions the grouping folds into one bloc merge their values into a single entry, so
            // an alliance's members rank as one summed unit.
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(SectorPolitics.regroupByBloc(valueByFactionId, grouping, 0, Integer::sum))
                    .containsExactly(entry("alliance-1", 5));
        }

        @Test
        void keepsFactionsInDistinctBlocsSeparate() {
            // Only the mapped faction folds into its bloc; an unmapped faction stays its own bloc, so
            // the two never merge.
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(SectorPolitics.regroupByBloc(valueByFactionId, grouping, 0, Integer::sum))
                    .containsOnly(entry("alliance-1", 2), entry("tritachyon", 3));
        }

        @Test
        void leavesEachFactionsValueUnchangedUnderTheIdentityGrouping() {
            // Under identity every faction is its own bloc, so each value merges into the fold's
            // identity alone and comes out unchanged - the no-op the render's faction view relies on.
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(SectorPolitics.regroupByBloc(
                    valueByFactionId, OwnershipGrouping.identity(), 0, Integer::sum))
                    .containsOnly(entry("hegemony", 2), entry("tritachyon", 3));
        }

        @Test
        void preservesTheFirstSeenBlocOrderOfTheInput() {
            // The fold keeps each bloc in the order it first appears in the faction walk, not sorted,
            // so the picker's economy-walk ordering flows straight through the regroup.
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("z-faction", 1);
            valueByFactionId.put("a-faction", 2);

            assertThat(SectorPolitics.regroupByBloc(
                    valueByFactionId, OwnershipGrouping.identity(), 0, Integer::sum))
                    .containsExactly(entry("z-faction", 1), entry("a-faction", 2));
        }
    }
}
