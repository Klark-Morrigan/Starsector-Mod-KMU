package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderInhabitation;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_DEV_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.HEGEMONY_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.NEUTRAL_BASE;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.PERSEAN_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.TRITACHYON_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildDarkTheme;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWithSystems;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.listSystemMarkets;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link FilteredPolitics}'s presence-aware holder assembly end to end:
 * reading a stubbed economy through {@link KnownMarketFootprints}, classifying the spotlighted
 * bloc's presence, and colouring each system's holder. Exercises the wiring the pure-rule unit test
 * cannot - that a dominated system carries the selected bloc's palette under the spotlit key, a
 * present-but-dominated system carries that same key yet is reported contested, an absent system
 * keeps its real (receding) holder, and the whole spotlit footprint keys alike so it fuses into one
 * territory. The stubbed economy is wired through the shared {@link SectorOwnershipFixtures} fixture.
 *
 * <p>It is also where the widening of presence past the weights is posed, since only a live economy
 * can hold the colony that makes the two part company: a station the economy does not list, which
 * raises no footprint and still puts its bloc in the system.
 *
 * <p>Where a bloc lives is {@link HolderInhabitation}'s read and is pinned in its own suite. The
 * presence cases here pin only that this assembly reads presence from it rather than from the weights.
 */
class FilteredPoliticsIntegrationTests {

    private static final DominanceRules STABILITY_WEIGHTED = buildStabilityWeightedRules();

    @Nested
    class ResolveFilteredHolder {

        @Test
        void marksADominatedSystemWithTheSpotlitKeyInTheSelectedBlocPaletteAndNotContested() {
            // The selected hegemony wins its system, so its cell keys spotlit and paints in
            // hegemony's own palette, and it is left out of the contested set (drawn solid).
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "hegemony");
            var holder = filtered.ownerBySystemKey().get(buildCellKey("owned-system"));

            assertThat(SpotlitOwners.isSpotlitOwner(holder.ownerId()))
                .isTrue();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainKey(buildCellKey("owned-system"));
            assertThat(holder.palette().primaryColour())
                .isEqualTo(HEGEMONY_FACTION_BRIGHT);
            assertThat(holder.palette().secondaryColour())
                .isEqualTo(buildDarkTheme(HEGEMONY_FACTION_BRIGHT));
        }

        @Test
        void reportsAPresentButDominatedSystemAsContestedInTheSelectedBlocPalette() {
            // The selected tritachyon owns a market but loses to hegemony, so its cell keys spotlit
            // (fusing with the rest of tritachyon's footprint) yet is reported contested - the only
            // record that it draws hatched - while still painting in tritachyon's palette.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");
            var holder = filtered.ownerBySystemKey().get(buildCellKey("owned-system"));

            assertThat(SpotlitOwners.isSpotlitOwner(holder.ownerId()))
                .isTrue();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey("owned-system"), FillState.HATCHED);
            assertThat(holder.palette().primaryColour())
                .isEqualTo(TRITACHYON_FACTION_BRIGHT);
            assertThat(holder.palette().secondaryColour())
                .isEqualTo(buildDarkTheme(TRITACHYON_FACTION_BRIGHT));
        }

        @Test
        void keepsTheRealRecedingHolderWhereTheSelectedBlocIsAbsent() {
            // The selected hegemony owns nothing in the tritachyon system, so that cell keeps its
            // real tritachyon holder - a real owner ID rather than the spotlit key, so the caller
            // recedes it - and is never reported contested.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("tritachyon-system", buildVisibleMarket(tritachyon, 3)));

            var filtered = resolveFor(sector, "hegemony");
            var holder = filtered.ownerBySystemKey().get(buildCellKey("tritachyon-system"));

            assertThat(holder.ownerId())
                .isEqualTo("tritachyon");
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainKey(buildCellKey("tritachyon-system"));
            assertThat(holder.palette().primaryColour())
                .isEqualTo(TRITACHYON_FACTION_BRIGHT);
        }

        @Test
        void keysADominatedAndAContestedSystemAlikeSoOneFrontierTracesTheWholeFootprint() {
            // The selected hegemony dominates one system and is present-but-dominated in another;
            // the two share one spotlit key so the geometry traces a single frontier over the
            // whole footprint, with only the contested set - not the key - telling them apart.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("solid-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets(
                    "contested-system",
                    buildVisibleMarket(hegemony, 2),
                    buildVisibleMarket(tritachyon, 5)));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(filtered.ownerBySystemKey().get(buildCellKey("solid-system")).ownerId())
                .isEqualTo(filtered.ownerBySystemKey().get(buildCellKey("contested-system")).ownerId());
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey("contested-system"), FillState.HATCHED)
                .doesNotContainKey(buildCellKey("solid-system"));
        }

        @Test
        void keysTwoDominatedSystemsAlikeSoTheyClusterTogether() {
            // Both systems the selected bloc dominates share the one spotlit key, so the geometry
            // fuses them into a single territory the way one faction's systems fuse, neither
            // contested.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("system-b", buildVisibleMarket(hegemony, 4)));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(filtered.ownerBySystemKey().get(buildCellKey("system-a")).ownerId())
                .isEqualTo(filtered.ownerBySystemKey().get(buildCellKey("system-b")).ownerId());
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainValue(FillState.HATCHED);
        }

        @Test
        void paintsASpotlitAllianceInItsDominantMembersPalette() {
            // Under the alliance grouping the selected bloc is an alliance ID; its spotlit cell
            // paints in its dominant member's (hegemony's) palette, reusing the same colour path
            // the normal alliance holder does.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var persean = buildFaction("persean", PERSEAN_FACTION_BRIGHT);
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
                    DominancePass.createOver(sector, STABILITY_WEIGHTED, UNDER_THE_FOG, grouping),
                    "alliance-1")
                .ownerBySystemKey()
                .get(buildCellKey("contested-system"));

            assertThat(SpotlitOwners.isSpotlitOwner(holder.ownerId()))
                .isTrue();
            assertThat(holder.palette().primaryColour())
                .isEqualTo(HEGEMONY_FACTION_BRIGHT);
        }

        @Test
        void reportsTheSpotlitFactionUncontestedBesideAHeavierNeutralMarket() {
            // Neutral outweighs the spotlit faction and is barred from winning anything, so the
            // faction holds the system outright and its cell fills solid. Judged under the pass's
            // own rules: were the spotlight to settle a winner under any other, it would hatch a
            // cell the base layers paint solid, which is a spotlight moving a fill.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var neutral = buildFaction(Factions.NEUTRAL, NEUTRAL_BASE);
            var sector = buildSectorWith(
                "salvage-system",
                List.of(neutral, hegemony),
                buildVisibleMarket(neutral, 6),
                buildVisibleMarket(hegemony, 3));

            var filtered = resolveFor(sector, "hegemony");

            assertThat(SpotlitOwners.isSpotlitOwner(
                    filtered.ownerBySystemKey().get(buildCellKey("salvage-system")).ownerId()))
                .isTrue();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainKey(buildCellKey("salvage-system"));
        }

        @Test
        void keepsTheSpotlitBlocWhoseOnlyColonyHereTheEconomyDoesNotList() {
            // The widening's own case. Tri-Tachyon's one foothold is a station the economy never
            // registered, so no weight can be computed from it and the bloc raises no footprint -
            // yet it lives here, so its cell keys spotlit and is reported contested (hatched)
            // rather than falling back to hegemony's holder and receding.
            var sector = buildSectorWhereTritachyonIsUnregistered(
                tritachyon -> buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");
            var holder = filtered.ownerBySystemKey().get(buildCellKey("owned-system"));

            assertThat(SpotlitOwners.isSpotlitOwner(holder.ownerId()))
                .isTrue();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey("owned-system"), FillState.HATCHED);
            assertThat(holder.palette().primaryColour())
                .isEqualTo(TRITACHYON_FACTION_BRIGHT);
        }

        @Test
        void keepsTheSpotlitBlocInASystemNobodyHolds() {
            // Every colony here is unregistered, so nothing was weighed and the system has no
            // dominant holder at all. The spotlit bloc still lives in it, which is what the
            // contested arm records - so the cell draws in its colours instead of leaving the
            // holder map and taking the factionless recede.
            var pirates = buildFaction("pirates", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith("haven", List.of(pirates));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildVisibleMarket(pirates, 4));

            var filtered = resolveFor(sector, "pirates");
            var holder = filtered.ownerBySystemKey().get(buildCellKey("haven"));

            assertThat(SpotlitOwners.isSpotlitOwner(holder.ownerId()))
                .isTrue();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey("haven"), FillState.HATCHED);
        }

        @Test
        void withholdsABlocWhoseOnlyMarketHereIsADerelict() {
            // Nobody lives aboard a derelict, so the bloc that owns it is absent and the system - which
            // nothing weighed - takes no holder at all. The listing over the cell still names the
            // wreck; what it may not do is put a fill under it.
            var sector = buildSectorWith("haven", List.of(buildFaction(Factions.NEUTRAL)));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildAbandonedStationMarket(4));

            assertThat(resolveFor(sector, Factions.NEUTRAL).ownerBySystemKey())
                .doesNotContainKey(buildCellKey("haven"));
        }

        @Test
        void withholdsABlocWhoseOnlyUnregisteredColonyHereThePlayerHasNotFound() {
            // The fog reaches the widened presence like any other read of the projection: an
            // unknown station names nobody, so the cell keeps hegemony's holder and recedes - and
            // the dev reveal restores the bloc to the spotlight, as it does everywhere else.
            var sector = buildSectorWhereTritachyonIsUnregistered(
                tritachyon -> buildUndiscoveredHiddenMarket(tritachyon, 3));

            assertThat(SpotlitOwners.isSpotlitOwner(
                    resolveFor(sector, "tritachyon")
                        .ownerBySystemKey()
                        .get(buildCellKey("owned-system"))
                        .ownerId()))
                .isFalse();
            assertThat(SpotlitOwners.isSpotlitOwner(
                    resolveRevealedFor(sector, "tritachyon")
                        .ownerBySystemKey()
                        .get(buildCellKey("owned-system"))
                        .ownerId()))
                .isTrue();
        }

        @Test
        void leavesASystemNothingWasWeighedInWithNoHolderWhereTheSpotlitBlocIsAbsent() {
            // The widening is the spotlight's alone: a system whose every colony is unregistered
            // still resolves no dominant holder, so where the spotlit bloc is absent the system is
            // left out of the holder map. Presence keeps a bloc drawn; it never hands anybody a system.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var pirates = buildFaction("pirates", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, pirates),
                listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("haven"));

            placeMarketsOnSystemEntities(
                sector.getStarSystems().get(1),
                buildVisibleMarket(pirates, 4));

            assertThat(resolveFor(sector, "hegemony").ownerBySystemKey())
                .doesNotContainKey(buildCellKey("haven"));
        }

        @Test
        void isEmptyWhenNoBlocIsSelected() {

            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith("owned-system", List.of(hegemony), buildVisibleMarket(hegemony, 5));
            var filtered =
                FilteredPolitics.resolveFilteredHolder(buildPassOver(sector), null);

            assertThat(filtered.ownerBySystemKey())
                .isEmpty();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainValue(FillState.HATCHED);
        }

        @Test
        void isEmptyForNullSector() {
            var filtered =
                FilteredPolitics.resolveFilteredHolder(buildPassOver(null), "hegemony");

            assertThat(filtered.ownerBySystemKey())
                .isEmpty();
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainValue(FillState.HATCHED);
        }

        @Test
        void leavesNoSystemUnfilledEvenWhereTheSpotlitBlocIsContested() {
            // The filter hatches a contested cell rather than blanking it, so the one resolution the
            // render pass reads carries no unfilled system even beside a hatched one.
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            var filtered = resolveFor(sector, "tritachyon");

            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey("owned-system"), FillState.HATCHED);
            assertThat(filtered.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainValue(FillState.UNFILLED);
        }
    }

    // A system hegemony holds through the economy's own listing, with Tri-Tachyon's one foothold
    // hung on an entity of the system that the listing never held. The shape the widened-presence
    // cases share, taking the unregistered colony itself because what varies between them is that
    // market alone - whether the player has found it.
    private static SectorAPI buildSectorWhereTritachyonIsUnregistered(
            Function<FactionAPI, MarketAPI> buildUnregisteredColony) {

        var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
        var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
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
    private static HolderResolution resolveFor(
            SectorAPI sector,
            String selectedBlocId) {

        return FilteredPolitics.resolveFilteredHolder(
            buildPassOver(sector),
            selectedBlocId);
    }

    // The same resolve with the dev reveal lifting the fog, for the one case that poses a colony
    // the player has not found: the reveal is a knob on the reading of the sector rather than on
    // the weighting rule, so it cannot be reached through the shared pass builder.
    private static HolderResolution resolveRevealedFor(
            SectorAPI sector,
            String selectedBlocId) {

        return FilteredPolitics.resolveFilteredHolder(
            DominancePass.createOver(
                sector,
                STABILITY_WEIGHTED,
                UNDER_THE_DEV_REVEAL,
                HolderGrouping.identity()),
            selectedBlocId);
    }
}
