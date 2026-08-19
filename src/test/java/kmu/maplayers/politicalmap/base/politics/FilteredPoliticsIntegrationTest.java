package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildDarkTheme;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHolderPassOver;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link FilteredPolitics}'s presence-aware holder assembly end to end:
 * reading a stubbed economy through {@link KnownMarketFootprints}, classifying the spotlighted
 * bloc's presence, and colouring each system's holder. Exercises the wiring the pure-rule unit test
 * cannot - that a dominated system carries the selected bloc's palette under the spotlit key, a
 * present-but-dominated system carries that same key yet is reported contested, an absent system
 * keeps its real (receding) holder, and the whole spotlit footprint keys alike so it fuses into one
 * territory. The stubbed economy is wired through the shared {@link SectorPoliticsFixtures} fixture.
 *
 * <p>It is also where the widening of presence past the weights is posed, since only a live economy
 * can hold the colony that makes the two part company: a station the economy does not list, which
 * raises no footprint and still puts its bloc in the system.
 *
 * <p>Covers the same class's read for systems the holder assembly never reaches - the ones a
 * narrow holding rule leaves unattributed - since that read answers presence off the same
 * economy walk and must agree with the assembly about where a bloc lives.
 */
class FilteredPoliticsIntegrationTest {

    private static final DominanceRules STABILITY_WEIGHTED = buildStabilityWeightedRules();

    @Nested
    class ResolveFilteredHolder {

        @Test
        void marksADominatedSystemWithTheSpotlitKeyInTheSelectedBlocPaletteAndNotContested() {
            // The selected hegemony wins its system, so its cell keys spotlit and paints in
            // hegemony's own palette, and it is left out of the contested set (drawn solid).
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "hegemony");
            var holder = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId()))
                .isTrue();
            assertThat(filtered.contestedSystemIds())
                .doesNotContain("owned-system");
            assertThat(holder.primaryColour())
                .isEqualTo(HEGEMONY_BRIGHT);
            assertThat(holder.secondaryColour())
                .isEqualTo(buildDarkTheme(HEGEMONY_BRIGHT));
        }

        @Test
        void reportsAPresentButDominatedSystemAsContestedInTheSelectedBlocPalette() {
            // The selected tritachyon owns a market but loses to hegemony, so its cell keys spotlit
            // (fusing with the rest of tritachyon's footprint) yet is reported contested - the only
            // record that it draws hatched - while still painting in tritachyon's palette.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");
            var holder = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId()))
                .isTrue();
            assertThat(filtered.contestedSystemIds())
                .contains("owned-system");
            assertThat(holder.primaryColour())
                .isEqualTo(TRITACHYON_BRIGHT);
            assertThat(holder.secondaryColour())
                .isEqualTo(buildDarkTheme(TRITACHYON_BRIGHT));
        }

        @Test
        void keepsTheRealRecedingHolderWhereTheSelectedBlocIsAbsent() {
            // The selected hegemony owns nothing in the tritachyon system, so that cell keeps its
            // real tritachyon holder - which isSpotlitBloc rejects, so the caller recedes it - and is
            // never reported contested.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("tritachyon-system", buildVisibleMarket(tritachyon, 3)));

            var filtered = resolveFor(sector, "hegemony");
            var holder = filtered.ownerBySystemId().get("tritachyon-system");

            assertThat(holder.factionId())
                .isEqualTo("tritachyon");
            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId()))
                .isFalse();
            assertThat(filtered.contestedSystemIds())
                .doesNotContain("tritachyon-system");
            assertThat(holder.primaryColour())
                .isEqualTo(TRITACHYON_BRIGHT);
        }

        @Test
        void keysADominatedAndAContestedSystemAlikeSoOneFrontierTracesTheWholeFootprint() {
            // The selected hegemony dominates one system and is present-but-dominated in another;
            // the two now share one spotlit key so the geometry traces a single frontier over the
            // whole footprint, with only the contested set - not the key - telling them apart.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("solid-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets(
                    "contested-system",
                    buildVisibleMarket(hegemony, 2),
                    buildVisibleMarket(tritachyon, 5)));

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
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("system-b", buildVisibleMarket(hegemony, 4)));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(filtered.ownerBySystemId().get("system-a").factionId())
                .isEqualTo(filtered.ownerBySystemId().get("system-b").factionId());
            assertThat(filtered.contestedSystemIds())
                .isEmpty();
        }

        @Test
        void paintsASpotlitAllianceInItsDominantMembersPalette() {
            // Under the alliance grouping the selected bloc is an alliance id; its spotlit cell
            // paints in its dominant member's (hegemony's) palette, reusing the same colour path
            // the normal alliance holder does.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var persean = buildFaction("persean", PERSEAN_BRIGHT);
            var sector = buildSectorWith(
                "contested-system",
                List.of(hegemony, tritachyon, persean),
                buildVisibleMarket(hegemony, 2),
                buildVisibleMarket(tritachyon, 2),
                buildVisibleMarket(persean, 3));

            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));

            var holder = FilteredPolitics.resolveFilteredHolder(
                    DominancePass.over(sector, STABILITY_WEIGHTED, false, grouping),
                    "alliance-1")
                .ownerBySystemId()
                .get("contested-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId()))
                .isTrue();
            assertThat(holder.primaryColour())
                .isEqualTo(HEGEMONY_BRIGHT);
        }

        @Test
        void keepsTheSpotlitBlocWhoseOnlyColonyHereTheEconomyDoesNotList() {
            // The step's own case. Tri-Tachyon's one foothold is a station the economy never
            // registered, so no weight can be computed from it and the bloc raises no footprint -
            // yet it lives here, so its cell keys spotlit and is reported contested (hatched)
            // rather than falling back to hegemony's holder and receding.
            var sector = buildSectorWhereTritachyonIsUnregistered(
                tritachyon -> buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");
            var holder = filtered.ownerBySystemId().get("owned-system");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId()))
                .isTrue();
            assertThat(filtered.contestedSystemIds())
                .contains("owned-system");
            assertThat(holder.primaryColour())
                .isEqualTo(TRITACHYON_BRIGHT);
        }

        @Test
        void keepsTheSpotlitBlocInASystemNobodyHolds() {
            // Every colony here is unregistered, so nothing was weighed and the system has no
            // dominant holder at all. The spotlit bloc still lives in it, which is what the
            // contested arm records - so the cell draws in its colours instead of leaving the
            // holder map and taking the factionless recede.
            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("haven", List.of(pirates));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildVisibleMarket(pirates, 4));

            var filtered = resolveFor(sector, "pirates");
            var holder = filtered.ownerBySystemId().get("haven");

            assertThat(FilteredPolitics.isSpotlitBloc(holder.factionId()))
                .isTrue();
            assertThat(filtered.contestedSystemIds())
                .contains("haven");
        }

        @Test
        void withholdsABlocWhoseOnlyUnregisteredColonyHereThePlayerHasNotFound() {
            // The fog reaches the widened presence like any other read of the projection: an
            // unfound station names nobody, so the cell keeps hegemony's holder and recedes - and
            // the dev reveal restores the bloc to the spotlight, as it does everywhere else.
            var sector = buildSectorWhereTritachyonIsUnregistered(
                tritachyon -> buildUndiscoveredHiddenMarket(tritachyon, 3));

            assertThat(FilteredPolitics.isSpotlitBloc(
                    resolveFor(sector, "tritachyon")
                        .ownerBySystemId()
                        .get("owned-system")
                        .factionId()))
                .isFalse();
            assertThat(FilteredPolitics.isSpotlitBloc(
                    resolveRevealedFor(sector, "tritachyon")
                        .ownerBySystemId()
                        .get("owned-system")
                        .factionId()))
                .isTrue();
        }

        @Test
        void leavesASystemNothingWasWeighedInWithNoHolderWhereTheSpotlitBlocIsAbsent() {
            // The widening is the spotlight's alone: a system whose every colony is unregistered
            // still resolves no dominant holder, so an unspotlit one is left out of the holder map
            // exactly as before. Presence keeps a bloc drawn; it never hands anybody a system.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, pirates),
                listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("haven"));

            placeMarketsOnSystemEntities(
                sector.getStarSystems().get(1),
                buildVisibleMarket(pirates, 4));

            assertThat(resolveFor(sector, "hegemony").ownerBySystemId())
                .doesNotContainKey("haven");
        }

        @Test
        void isEmptyWhenNoBlocIsSelected() {

            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith("owned-system", List.of(hegemony), buildVisibleMarket(hegemony, 5));
            var filtered =
                FilteredPolitics.resolveFilteredHolder(buildPassOver(sector), null);

            assertThat(filtered.ownerBySystemId())
                .isEmpty();
            assertThat(filtered.contestedSystemIds())
                .isEmpty();
        }

        @Test
        void isEmptyForNullSector() {
            var filtered =
                FilteredPolitics.resolveFilteredHolder(buildPassOver(null), "hegemony");

            assertThat(filtered.ownerBySystemId())
                .isEmpty();
            assertThat(filtered.contestedSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class FindPresentSystemIds {

        @Test
        void reportsACandidateTheSelectedBlocOwnsAMarketIn() {
            // The claims view's case: pirates hold a colony but claim nothing, so the system reaches
            // the render pass with no holder. The read has to find them there, or the cell over
            // their own colony sinks into the receded background.
            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("haven", List.of(pirates), buildVisibleMarket(pirates, 4));

            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(sector),
                    "pirates",
                    Set.of("haven")))
                .containsExactly("haven");
        }

        @Test
        void reportsACandidateTheSelectedBlocOwnsAMarketInEvenWhenARivalOutweighsIt() {
            // Presence, not dominance: a holderless system has no contest to win, so a bloc living
            // beside a larger rival counts exactly as one living alone does.
            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var independent = buildFaction("independent", PERSEAN_BRIGHT);
            var sector = buildSectorWith(
                "haven",
                List.of(pirates, independent),
                buildVisibleMarket(pirates, 2),
                buildVisibleMarket(independent, 6));

            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(sector),
                    "pirates",
                    Set.of("haven")))
                .containsExactly("haven");
        }

        @Test
        void reportsACandidateTheSelectedBlocOnlyHoldsAnUnregisteredColonyIn() {
            // The shape this read exists for, now that presence is read off the colonies rather
            // than off the weights: a haven whose one station the economy never registered, which
            // is why nothing holds the system and why it reached this read at all.
            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("haven", List.of(pirates));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildVisibleMarket(pirates, 4));

            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(sector),
                    "pirates",
                    Set.of("haven")))
                .containsExactly("haven");
        }

        @Test
        void omitsACandidateTheSelectedBlocOwnsNothingIn() {

            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var independent = buildFaction("independent", PERSEAN_BRIGHT);
            var sector = buildSectorWith(
                "haven",
                List.of(pirates, independent),
                buildVisibleMarket(independent, 6));

            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(sector),
                    "pirates",
                    Set.of("haven")))
                .isEmpty();
        }

        @Test
        void omitsASystemOutsideTheCandidateSetTheSelectedBlocLivesIn() {
            // The candidate set is the whole of what the read answers for. A system the holding
            // already attributed to somebody draws in that territory, so re-reporting it here would
            // spare a cell a recede its own bloc is meant to take.
            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(pirates),
                listSystemMarkets("held", buildVisibleMarket(pirates, 5)),
                listSystemMarkets("haven", buildVisibleMarket(pirates, 4)));

            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(sector),
                    "pirates",
                    Set.of("haven")))
                .containsExactly("haven");
        }

        @Test
        void isEmptyWhenNoBlocIsSelected() {

            var pirates = buildFaction("pirates", TRITACHYON_BRIGHT);
            var sector = buildSectorWith("haven", List.of(pirates), buildVisibleMarket(pirates, 4));

            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(sector),
                    null,
                    Set.of("haven")))
                .isEmpty();
        }

        @Test
        void isEmptyForNullSector() {
            assertThat(FilteredPolitics.findPresentSystemIds(
                    buildHolderPassOver(null),
                    "pirates",
                    Set.of("haven")))
                .isEmpty();
        }
    }

    // A system hegemony holds through the economy's own listing, with Tri-Tachyon's one foothold
    // hung on an entity of the system that the listing never held. The shape the widened-presence
    // cases share, taking the unregistered colony itself because what varies between them is that
    // market alone - whether the player has found it.
    private static SectorAPI buildSectorWhereTritachyonIsUnregistered(
            Function<FactionAPI, MarketAPI> buildUnregisteredColony) {

        var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
        var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
        var sector = buildSectorWith(
            "owned-system",
            List.of(hegemony, tritachyon),
            buildVisibleMarket(hegemony, 5));

        placeMarketsOnSystemEntities(
            buildOnlySystem(sector),
            buildUnregisteredColony.apply(tritachyon));

        return sector;
    }

    // Resolves the presence-aware holding for a selected faction under the identity grouping and
    // the shared stability rule, the shape every faction-view filter test reads.
    private static FilteredPolitics.FilteredHolder resolveFor(
            SectorAPI sector,
            String selectedBlocId) {

        return FilteredPolitics.resolveFilteredHolder(
            buildPassOver(sector),
            selectedBlocId);
    }

    // The same resolve with the dev reveal lifting the fog, for the one case that poses a colony
    // the player has not found: the reveal is a knob on the reading of the sector rather than on
    // the weighting rule, so it cannot be reached through the shared pass builder.
    private static FilteredPolitics.FilteredHolder resolveRevealedFor(
            SectorAPI sector,
            String selectedBlocId) {

        return FilteredPolitics.resolveFilteredHolder(
            DominancePass.over(sector, STABILITY_WEIGHTED, true, HolderGrouping.identity()),
            selectedBlocId);
    }
}
