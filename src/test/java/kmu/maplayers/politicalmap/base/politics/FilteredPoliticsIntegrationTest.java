package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildDarkTheme;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link FilteredPolitics}'s presence-aware holder assembly end to end:
 * reading a stubbed economy through {@link KnownMarketFootprints}, classifying the spotlighted
 * bloc's presence, and colouring each system's holder. Exercises the wiring the pure-rule unit test
 * cannot - that a dominated system carries the selected bloc's palette under the spotlit key, a
 * present-but-dominated system carries that same key yet is reported contested, an absent system
 * keeps its real (receding) holder, and the whole spotlit footprint keys alike so it fuses into one
 * territory. The stubbed economy is wired through the shared {@link SectorPoliticsFixtures} fixture.
 */
class FilteredPoliticsIntegrationTest {
    private static final DominanceRules STABILITY_WEIGHTED = buildStabilityWeightedRules();

    // The faction-view pass most tests filter under: the stability rule, the normal filter, and the
    // identity grouping. The alliance-grouping test builds its own pass.
    private static final DominancePass STABILITY_PASS =
            new DominancePass(STABILITY_WEIGHTED, false, HolderGrouping.identity());

    @Nested
    class ResolveFilteredHolder {

        @Test
        void marksADominatedSystemWithTheSpotlitKeyInTheSelectedBlocPaletteAndNotContested() {
            // The selected hegemony wins its system, so its cell keys spotlit and paints in
            // hegemony's own palette, and it is left out of the contested set (drawn solid).
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("owned-system", List.of(hegemony, tritachyon),
                    buildVisibleMarket(hegemony, 5), buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "hegemony");
            var holder = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId())).isTrue();
            assertThat(filtered.contestedSystemIds()).doesNotContain("owned-system");
            assertThat(holder.primaryColour()).isEqualTo(HEGEMONY_BRIGHT);
            assertThat(holder.secondaryColour()).isEqualTo(buildDarkTheme(HEGEMONY_BRIGHT));
        }

        @Test
        void reportsAPresentButDominatedSystemAsContestedInTheSelectedBlocPalette() {
            // The selected tritachyon owns a market but loses to hegemony, so its cell keys spotlit
            // (fusing with the rest of tritachyon's footprint) yet is reported contested - the only
            // record that it draws hatched - while still painting in tritachyon's palette.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("owned-system", List.of(hegemony, tritachyon),
                    buildVisibleMarket(hegemony, 5), buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");
            var holder = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId())).isTrue();
            assertThat(filtered.contestedSystemIds()).contains("owned-system");
            assertThat(holder.primaryColour()).isEqualTo(TRITACHYON_BRIGHT);
            assertThat(holder.secondaryColour()).isEqualTo(buildDarkTheme(TRITACHYON_BRIGHT));
        }

        @Test
        void keepsTheRealRecedingHolderWhereTheSelectedBlocIsAbsent() {
            // The selected hegemony owns nothing in the tritachyon system, so that cell keeps its
            // real tritachyon holder - which isSpotlitBloc rejects, so the caller recedes it - and is
            // never reported contested.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony, tritachyon),
                    listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                    listSystemMarkets("tritachyon-system", buildVisibleMarket(tritachyon, 3)));

            var filtered = resolveFor(sector, "hegemony");
            var holder = filtered.ownerBySystemId().get("tritachyon-system");

            assertThat(holder.factionId()).isEqualTo("tritachyon");
            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId())).isFalse();
            assertThat(filtered.contestedSystemIds()).doesNotContain("tritachyon-system");
            assertThat(holder.primaryColour()).isEqualTo(TRITACHYON_BRIGHT);
        }

        @Test
        void keysADominatedAndAContestedSystemAlikeSoOneFrontierTracesTheWholeFootprint() {
            // The selected hegemony dominates one system and is present-but-dominated in another;
            // the two now share one spotlit key so the geometry traces a single frontier over the
            // whole footprint, with only the contested set - not the key - telling them apart.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony, tritachyon),
                    listSystemMarkets("solid-system", buildVisibleMarket(hegemony, 5)),
                    listSystemMarkets("contested-system",
                            buildVisibleMarket(hegemony, 2), buildVisibleMarket(tritachyon, 5)));

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
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWithSystems(List.of(hegemony),
                    listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                    listSystemMarkets("system-b", buildVisibleMarket(hegemony, 4)));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(filtered.ownerBySystemId().get("system-a").factionId())
                    .isEqualTo(filtered.ownerBySystemId().get("system-b").factionId());
            assertThat(filtered.contestedSystemIds()).isEmpty();
        }

        @Test
        void paintsASpotlitAllianceInItsDominantMembersPalette() {
            // Under the alliance grouping the selected bloc is an alliance id; its spotlit cell
            // paints in its dominant member's (hegemony's) palette, reusing the same colour path
            // the normal alliance holder does.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var persean = buildFaction("persean", PERSEAN_BRIGHT);
            var sector = buildSectorWith("contested-system", List.of(hegemony, tritachyon, persean),
                    buildVisibleMarket(hegemony, 2), buildVisibleMarket(tritachyon, 2),
                    buildVisibleMarket(persean, 3));
            var grouping = new HolderGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));

            var holder = FilteredPolitics.resolveFilteredHolder(
                    sector, new DominancePass(STABILITY_WEIGHTED, false, grouping), "alliance-1")
                    .ownerBySystemId().get("contested-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId())).isTrue();
            assertThat(holder.primaryColour()).isEqualTo(HEGEMONY_BRIGHT);
        }

        @Test
        void isEmptyWhenNoBlocIsSelected() {
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith("owned-system", List.of(hegemony), buildVisibleMarket(hegemony, 5));

            var filtered = FilteredPolitics.resolveFilteredHolder(sector, STABILITY_PASS, null);

            assertThat(filtered.ownerBySystemId()).isEmpty();
            assertThat(filtered.contestedSystemIds()).isEmpty();
        }

        @Test
        void isEmptyForNullSector() {
            var filtered =
                    FilteredPolitics.resolveFilteredHolder(null, STABILITY_PASS, "hegemony");

            assertThat(filtered.ownerBySystemId()).isEmpty();
            assertThat(filtered.contestedSystemIds()).isEmpty();
        }
    }

    // Resolves the presence-aware holding for a selected faction under the identity grouping and
    // the shared stability rule, the shape every faction-view filter test reads.
    private static FilteredPolitics.FilteredHolder resolveFor(
            SectorAPI sector, String selectedBlocId) {
        return FilteredPolitics.resolveFilteredHolder(sector, STABILITY_PASS, selectedBlocId);
    }
}
