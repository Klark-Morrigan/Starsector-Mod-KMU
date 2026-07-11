package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.PERSEAN_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.dark;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.faction;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.sectorWith;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.sectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.stabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.systemMarkets;
import static kmu.maplayers.politicalmap.base.politics.PoliticsTestSectors.visibleMarket;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link FilteredPolitics}'s presence-aware owner assembly end to end:
 * reading a stubbed economy through {@link KnownMarketFootprints}, classifying the spotlighted
 * bloc's presence, and colouring each system's owner. Exercises the wiring the pure-rule unit test
 * cannot - that a dominated system carries the selected bloc's palette under the spotlit key, a
 * present-but-dominated system carries that same key yet is reported contested, an absent system
 * keeps its real (receding) owner, and the whole spotlit footprint keys alike so it fuses into one
 * territory. The stubbed economy is wired through the shared {@link PoliticsTestSectors} fixture.
 */
class FilteredPoliticsIntegrationTest {
    private static final DominanceRules STABILITY_WEIGHTED = stabilityWeightedRules();

    @Nested
    class ResolveFilteredOwnership {

        @Test
        void marksADominatedSystemWithTheSpotlitKeyInTheSelectedBlocPaletteAndNotContested() {
            // The selected hegemony wins its system, so its cell keys spotlit and paints in
            // hegemony's own palette, and it is left out of the contested set (drawn solid).
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "hegemony");
            var owner = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isTrue();
            assertThat(filtered.contestedSystemIds()).doesNotContain("owned-system");
            assertThat(owner.primaryColor()).isEqualTo(HEGEMONY_BRIGHT);
            assertThat(owner.secondaryColor()).isEqualTo(dark(HEGEMONY_BRIGHT));
        }

        @Test
        void reportsAPresentButDominatedSystemAsContestedInTheSelectedBlocPalette() {
            // The selected tritachyon owns a market but loses to hegemony, so its cell keys spotlit
            // (fusing with the rest of tritachyon's footprint) yet is reported contested - the only
            // record that it draws hatched - while still painting in tritachyon's palette.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");
            var owner = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isTrue();
            assertThat(filtered.contestedSystemIds()).contains("owned-system");
            assertThat(owner.primaryColor()).isEqualTo(TRITACHYON_BRIGHT);
            assertThat(owner.secondaryColor()).isEqualTo(dark(TRITACHYON_BRIGHT));
        }

        @Test
        void keepsTheRealRecedingOwnerWhereTheSelectedBlocIsAbsent() {
            // The selected hegemony owns nothing in the tritachyon system, so that cell keeps its
            // real tritachyon owner - which isSpotlitBloc rejects, so the caller recedes it - and is
            // never reported contested.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony, tritachyon),
                    systemMarkets("hegemony-system", visibleMarket(hegemony, 5)),
                    systemMarkets("tritachyon-system", visibleMarket(tritachyon, 3)));

            var filtered = resolveFor(sector, "hegemony");
            var owner = filtered.ownerBySystemId().get("tritachyon-system");

            assertThat(owner.factionId()).isEqualTo("tritachyon");
            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isFalse();
            assertThat(filtered.contestedSystemIds()).doesNotContain("tritachyon-system");
            assertThat(owner.primaryColor()).isEqualTo(TRITACHYON_BRIGHT);
        }

        @Test
        void keysADominatedAndAContestedSystemAlikeSoOneFrontierTracesTheWholeFootprint() {
            // The selected hegemony dominates one system and is present-but-dominated in another;
            // the two now share one spotlit key so the geometry traces a single frontier over the
            // whole footprint, with only the contested set - not the key - telling them apart.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony, tritachyon),
                    systemMarkets("solid-system", visibleMarket(hegemony, 5)),
                    systemMarkets("contested-system",
                            visibleMarket(hegemony, 2), visibleMarket(tritachyon, 5)));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(filtered.ownerBySystemId().get("solid-system").factionId())
                    .isEqualTo(filtered.ownerBySystemId().get("contested-system").factionId());
            assertThat(filtered.contestedSystemIds())
                    .contains("contested-system").doesNotContain("solid-system");
        }

        @Test
        void keysTwoDominatedSystemsAlikeSoTheyClusterTogether() {
            // Both systems the selected bloc dominates share the one spotlit key, so the geometry
            // fuses them into a single territory the way one faction's systems fuse, neither
            // contested.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony),
                    systemMarkets("system-a", visibleMarket(hegemony, 5)),
                    systemMarkets("system-b", visibleMarket(hegemony, 4)));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(filtered.ownerBySystemId().get("system-a").factionId())
                    .isEqualTo(filtered.ownerBySystemId().get("system-b").factionId());
            assertThat(filtered.contestedSystemIds()).isEmpty();
        }

        @Test
        void paintsASpotlitAllianceInItsDominantMembersPalette() {
            // Under the alliance grouping the selected bloc is an alliance id; its spotlit cell
            // paints in its dominant member's (hegemony's) palette, reusing the same colour path
            // the normal alliance owner does.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var persean = faction("persean", PERSEAN_BRIGHT);
            var sector = sectorWith("contested-system", List.of(hegemony, tritachyon, persean),
                    visibleMarket(hegemony, 2), visibleMarket(tritachyon, 2),
                    visibleMarket(persean, 3));
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));

            var owner = FilteredPolitics.resolveFilteredOwnership(sector, STABILITY_WEIGHTED, false,
                    grouping, "alliance-1").ownerBySystemId().get("contested-system");

            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isTrue();
            assertThat(owner.primaryColor()).isEqualTo(HEGEMONY_BRIGHT);
        }

        @Test
        void isEmptyWhenNoBlocIsSelected() {
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony), visibleMarket(hegemony, 5));

            var filtered = FilteredPolitics.resolveFilteredOwnership(
                    sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity(), null);

            assertThat(filtered.ownerBySystemId()).isEmpty();
            assertThat(filtered.contestedSystemIds()).isEmpty();
        }

        @Test
        void isEmptyForNullSector() {
            var filtered = FilteredPolitics.resolveFilteredOwnership(
                    null, STABILITY_WEIGHTED, false, OwnershipGrouping.identity(), "hegemony");

            assertThat(filtered.ownerBySystemId()).isEmpty();
            assertThat(filtered.contestedSystemIds()).isEmpty();
        }
    }

    // Resolves the presence-aware ownership for a selected faction under the identity grouping and
    // the shared stability rule, the shape every faction-view filter test reads.
    private static FilteredPolitics.FilteredOwnership resolveFor(
            SectorAPI sector, String selectedBlocId) {
        return FilteredPolitics.resolveFilteredOwnership(
                sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity(), selectedBlocId);
    }
}
