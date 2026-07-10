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
 * cannot - that a dominated system carries the selected bloc's palette under a spotlit key, a
 * present-but-dominated system carries a distinct contested key, an absent system keeps its real
 * (receding) owner, and the two spotlit clusters key apart while same-state systems key alike. The
 * stubbed economy is wired through the shared {@link PoliticsTestSectors} fixture.
 */
class FilteredPoliticsIntegrationTest {
    private static final DominanceRules STABILITY_WEIGHTED = stabilityWeightedRules();

    @Nested
    class ResolveOwnerBySystemId {

        @Test
        void marksADominatedSystemWithASpotlitKeyInTheSelectedBlocPalette() {
            // The selected hegemony wins its system, so its cell keys as spotlit-solid (not
            // contested) and paints in hegemony's own palette.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            var owner = resolveFor(sector, "hegemony").get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isTrue();
            assertThat(FilteredPolitics.isContestedBloc(owner.factionId())).isFalse();
            assertThat(owner.primaryColor()).isEqualTo(HEGEMONY_BRIGHT);
            assertThat(owner.secondaryColor()).isEqualTo(dark(HEGEMONY_BRIGHT));
        }

        @Test
        void marksAPresentButDominatedSystemAsContestedInTheSelectedBlocPalette() {
            // The selected tritachyon owns a market but loses to hegemony, so its cell keys as
            // contested (drawn hatched downstream) yet still paints in tritachyon's palette.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            var owner = resolveFor(sector, "tritachyon").get("owned-system");

            assertThat(FilteredPolitics.isContestedBloc(owner.factionId())).isTrue();
            assertThat(owner.primaryColor()).isEqualTo(TRITACHYON_BRIGHT);
            assertThat(owner.secondaryColor()).isEqualTo(dark(TRITACHYON_BRIGHT));
        }

        @Test
        void keepsTheRealRecedingOwnerWhereTheSelectedBlocIsAbsent() {
            // The selected hegemony owns nothing in the tritachyon system, so that cell keeps its
            // real tritachyon owner - which isSpotlitBloc rejects, so the caller recedes it.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony, tritachyon),
                    systemMarkets("hegemony-system", visibleMarket(hegemony, 5)),
                    systemMarkets("tritachyon-system", visibleMarket(tritachyon, 3)));

            var owner = resolveFor(sector, "hegemony").get("tritachyon-system");

            assertThat(owner.factionId()).isEqualTo("tritachyon");
            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isFalse();
            assertThat(owner.primaryColor()).isEqualTo(TRITACHYON_BRIGHT);
        }

        @Test
        void keysADominatedAndAContestedSystemApartSoTheyClusterSeparately() {
            // The selected hegemony dominates one system and is present-but-dominated in another;
            // the two must carry different keys so the geometry traces a solid and a contested
            // territory rather than fusing them.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony, tritachyon),
                    systemMarkets("solid-system", visibleMarket(hegemony, 5)),
                    systemMarkets("contested-system",
                            visibleMarket(hegemony, 2), visibleMarket(tritachyon, 5)));

            var owners = resolveFor(sector, "hegemony");

            assertThat(owners.get("solid-system").factionId())
                    .isNotEqualTo(owners.get("contested-system").factionId());
            assertThat(FilteredPolitics.isContestedBloc(owners.get("solid-system").factionId()))
                    .isFalse();
            assertThat(FilteredPolitics.isContestedBloc(owners.get("contested-system").factionId()))
                    .isTrue();
        }

        @Test
        void keysTwoDominatedSystemsAlikeSoTheyClusterTogether() {
            // Both systems the selected bloc dominates share the one spotlit-solid key, so the
            // geometry fuses them into a single territory the way one faction's systems fuse.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony),
                    systemMarkets("system-a", visibleMarket(hegemony, 5)),
                    systemMarkets("system-b", visibleMarket(hegemony, 4)));

            var owners = resolveFor(sector, "hegemony");

            assertThat(owners.get("system-a").factionId())
                    .isEqualTo(owners.get("system-b").factionId());
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

            var owner = FilteredPolitics.resolveOwnerBySystemId(
                    sector, STABILITY_WEIGHTED, false, grouping, "alliance-1").get("contested-system");

            assertThat(FilteredPolitics.isSpotlitBloc(owner.factionId())).isTrue();
            assertThat(owner.primaryColor()).isEqualTo(HEGEMONY_BRIGHT);
        }

        @Test
        void isEmptyWhenNoBlocIsSelected() {
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony), visibleMarket(hegemony, 5));

            assertThat(FilteredPolitics.resolveOwnerBySystemId(
                    sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity(), null)).isEmpty();
        }

        @Test
        void isEmptyForNullSector() {
            assertThat(FilteredPolitics.resolveOwnerBySystemId(
                    null, STABILITY_WEIGHTED, false, OwnershipGrouping.identity(), "hegemony"))
                    .isEmpty();
        }
    }

    // Resolves the presence-aware owners for a selected faction under the identity grouping and
    // the shared stability rule, the shape every faction-view filter test reads.
    private static Map<String, DominantOwner> resolveFor(SectorAPI sector, String selectedBlocId) {
        return FilteredPolitics.resolveOwnerBySystemId(
                sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity(), selectedBlocId);
    }
}
