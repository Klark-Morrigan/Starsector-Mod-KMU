package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderInhabitation;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
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
import static org.mockito.Mockito.mock;

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

    // The synthetic key every spotlit system carries, written out rather than read back through the
    // class that recognises it, so a key that drifted would fail here.
    private static final String SPOTLIT_KEY = "$kmu_filter_spotlit";

    // The one system the contested sector holds.
    private static final String OWNED_SYSTEM = "owned-system";

    @Nested
    class ResolveFilteredHolder {

        // The selected hegemony wins its system, so its cell keys spotlit and paints in hegemony's
        // own palette, and it is left out of the contested set (drawn solid).
        @Test
        void marksADominatedSystemWithTheSpotlitKeyInTheSelectedBlocPaletteAndNotContested() {

            // ARRANGE
            var sector = buildContestedSector();
            var expectedHolder = new SystemOwner(SPOTLIT_KEY, buildPaletteOf(HEGEMONY_FACTION_BRIGHT));

            // ACT
            var actualResolution = resolveFor(sector, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .containsEntry(buildCellKey(OWNED_SYSTEM), expectedHolder);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainKey(buildCellKey(OWNED_SYSTEM));
        }

        // The selected tritachyon owns a market but loses to hegemony, so its cell keys spotlit
        // (fusing with the rest of tritachyon's footprint) yet is reported contested - the only
        // record that it draws hatched - while still painting in tritachyon's palette.
        @Test
        void reportsAPresentButDominatedSystemAsContestedInTheSelectedBlocPalette() {

            // ARRANGE
            var sector = buildContestedSector();
            var expectedHolder = new SystemOwner(SPOTLIT_KEY, buildPaletteOf(TRITACHYON_FACTION_BRIGHT));

            // ACT
            var actualResolution = resolveFor(sector, "tritachyon");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .containsEntry(buildCellKey(OWNED_SYSTEM), expectedHolder);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey(OWNED_SYSTEM), FillState.HATCHED);
        }

        // The selected hegemony owns nothing in the tritachyon system, so that cell keeps its real
        // tritachyon holder - a real owner ID rather than the spotlit key, so the caller recedes it -
        // and is never reported contested.
        @Test
        void keepsTheRealRecedingHolderWhereTheSelectedBlocIsAbsent() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("tritachyon-system", buildVisibleMarket(tritachyon, 3)));

            var expectedHolder = new SystemOwner("tritachyon", buildPaletteOf(TRITACHYON_FACTION_BRIGHT));

            // ACT
            var actualResolution = resolveFor(sector, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .containsEntry(buildCellKey("tritachyon-system"), expectedHolder);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainKey(buildCellKey("tritachyon-system"));
        }

        // The selected hegemony dominates one system and is present-but-dominated in another; the two
        // share one spotlit key so the geometry traces a single frontier over the whole footprint,
        // with only the contested set - not the key - telling them apart.
        @Test
        void keysADominatedAndAContestedSystemAlikeSoOneFrontierTracesTheWholeFootprint() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, tritachyon),
                listSystemMarkets("solid-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets(
                    "contested-system",
                    buildVisibleMarket(hegemony, 2),
                    buildVisibleMarket(tritachyon, 5)));

            // ACT
            var actualResolution = resolveFor(sector, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey().get(buildCellKey("solid-system")).ownerId())
                .isEqualTo(SPOTLIT_KEY);
            assertThat(actualResolution.ownerBySystemKey().get(buildCellKey("contested-system")).ownerId())
                .isEqualTo(SPOTLIT_KEY);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .containsExactly(Map.entry(buildCellKey("contested-system"), FillState.HATCHED));
        }

        // Both systems the selected bloc dominates share the one spotlit key, so the geometry fuses
        // them into a single territory the way one faction's systems fuse, neither contested.
        @Test
        void keysTwoDominatedSystemsAlikeSoTheyClusterTogether() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony),
                listSystemMarkets("system-a", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("system-b", buildVisibleMarket(hegemony, 4)));

            // ACT
            var actualResolution = resolveFor(sector, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey().get(buildCellKey("system-a")).ownerId())
                .isEqualTo(SPOTLIT_KEY);
            assertThat(actualResolution.ownerBySystemKey().get(buildCellKey("system-b")).ownerId())
                .isEqualTo(SPOTLIT_KEY);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .isEmpty();
        }

        // Under the alliance grouping the selected bloc is an alliance ID; its spotlit cell paints in
        // its dominant member's (hegemony's) palette, reusing the same colour path the normal
        // alliance holder does.
        @Test
        void paintsASpotlitAllianceInItsDominantMembersPalette() {

            // ARRANGE
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

            var pass = DominancePass.createOver(sector, STABILITY_WEIGHTED, UNDER_THE_FOG, grouping);
            var expectedHolder = new SystemOwner(SPOTLIT_KEY, buildPaletteOf(HEGEMONY_FACTION_BRIGHT));

            // ACT
            var actualResolution = FilteredPolitics.resolveFilteredHolder(pass, "alliance-1");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .containsEntry(buildCellKey("contested-system"), expectedHolder);
        }

        // Neutral outweighs the spotlit faction and is barred from winning anything, so the faction
        // holds the system outright and its cell fills solid. Judged under the pass's own rules: were
        // the spotlight to settle a winner under any other, it would hatch a cell the base layers
        // paint solid, which is a spotlight moving a fill.
        @Test
        void reportsTheSpotlitFactionUncontestedBesideAHeavierNeutralMarket() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var neutral = buildFaction(Factions.NEUTRAL, NEUTRAL_BASE);
            var sector = buildSectorWith(
                "salvage-system",
                List.of(neutral, hegemony),
                buildVisibleMarket(neutral, 6),
                buildVisibleMarket(hegemony, 3));

            // ACT
            var actualResolution = resolveFor(sector, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey().get(buildCellKey("salvage-system")).ownerId())
                .isEqualTo(SPOTLIT_KEY);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .doesNotContainKey(buildCellKey("salvage-system"));
        }

        // The widening's own case. Tri-Tachyon's one foothold is a station the economy never
        // registered, so no weight can be computed from it and the bloc raises no footprint - yet it
        // lives here, so its cell keys spotlit and is reported contested (hatched) rather than
        // falling back to hegemony's holder and receding.
        @Test
        void keepsTheSpotlitBlocWhoseOnlyColonyHereTheEconomyDoesNotList() {

            // ARRANGE
            var sector = buildSectorWhereTritachyonIsUnregistered(
                tritachyon -> buildVisibleMarket(tritachyon, 3));

            var expectedHolder = new SystemOwner(SPOTLIT_KEY, buildPaletteOf(TRITACHYON_FACTION_BRIGHT));

            // ACT
            var actualResolution = resolveFor(sector, "tritachyon");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .containsEntry(buildCellKey(OWNED_SYSTEM), expectedHolder);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey(OWNED_SYSTEM), FillState.HATCHED);
        }

        // Every colony here is unregistered, so nothing was weighed and the system has no dominant
        // holder at all. The spotlit bloc still lives in it, which is what the contested arm records -
        // so the cell draws in its colours instead of leaving the holder map and taking the
        // factionless recede.
        @Test
        void keepsTheSpotlitBlocInASystemNobodyHolds() {

            // ARRANGE
            var pirates = buildFaction("pirates", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith("haven", List.of(pirates));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildVisibleMarket(pirates, 4));

            // ACT
            var actualResolution = resolveFor(sector, "pirates");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey().get(buildCellKey("haven")).ownerId())
                .isEqualTo(SPOTLIT_KEY);
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(buildCellKey("haven"), FillState.HATCHED);
        }

        // Nobody lives aboard a derelict, so the bloc that owns it is absent and the system - which
        // nothing weighed - takes no holder at all. The listing over the cell still names the wreck;
        // what it may not do is put a fill under it.
        @Test
        void withholdsABlocWhoseOnlyMarketHereIsADerelict() {

            // ARRANGE
            var sector = buildSectorWith("haven", List.of(buildFaction(Factions.NEUTRAL)));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildAbandonedStationMarket(4));

            // ACT
            var actualResolution = resolveFor(sector, Factions.NEUTRAL);

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .doesNotContainKey(buildCellKey("haven"));
        }

        // The fog reaches the widened presence like any other read of the projection: an unknown
        // station names nobody, so the cell keeps hegemony's holder and recedes - and the dev reveal
        // restores the bloc to the spotlight, as it does everywhere else.
        @Test
        void withholdsABlocWhoseOnlyUnregisteredColonyHereThePlayerHasNotFound() {

            // ARRANGE
            var sector = buildSectorWhereTritachyonIsUnregistered(
                tritachyon -> buildUndiscoveredHiddenMarket(tritachyon, 3));

            // ACT
            var actualFoggedResolution = resolveFor(sector, "tritachyon");
            var actualRevealedResolution = resolveRevealedFor(sector, "tritachyon");

            // ASSERT
            assertThat(actualFoggedResolution.ownerBySystemKey().get(buildCellKey(OWNED_SYSTEM)).ownerId())
                .isEqualTo("hegemony");
            assertThat(actualRevealedResolution.ownerBySystemKey().get(buildCellKey(OWNED_SYSTEM)).ownerId())
                .isEqualTo(SPOTLIT_KEY);
        }

        // The widening is the spotlight's alone: a system whose every colony is unregistered still
        // resolves no dominant holder, so where the spotlit bloc is absent the system is left out of
        // the holder map. Presence keeps a bloc drawn; it never hands anybody a system.
        @Test
        void leavesASystemNothingWasWeighedInWithNoHolderWhereTheSpotlitBlocIsAbsent() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var pirates = buildFaction("pirates", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWithSystems(
                List.of(hegemony, pirates),
                listSystemMarkets("hegemony-system", buildVisibleMarket(hegemony, 5)),
                listSystemMarkets("haven"));

            placeMarketsOnSystemEntities(
                sector.getStarSystems().get(1),
                buildVisibleMarket(pirates, 4));

            // ACT
            var actualResolution = resolveFor(sector, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .doesNotContainKey(buildCellKey("haven"));
        }

        // No pick, no spotlight: the filtered holding is empty, the caller taking the plain one.
        @Test
        void isEmptyWhenNoBlocIsSelected() {

            // ARRANGE
            var sector = buildContestedSector();

            // ACT
            var actualResolution = FilteredPolitics.resolveFilteredHolder(buildPassOver(sector), null);

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .isEmpty();
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .isEmpty();
        }

        // A pass over no sector has nothing to walk.
        @Test
        void isEmptyForNullSector() {

            // ARRANGE
            var pass = buildPassOver(null);

            // ACT
            var actualResolution = FilteredPolitics.resolveFilteredHolder(pass, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .isEmpty();
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .isEmpty();
        }

        // A sector whose economy is not there to read - one torn down mid-session - weighs nobody,
        // so the filtered holding is empty rather than a walk that faults on the first system.
        @Test
        void isEmptyOverASectorWithNoEconomy() {

            // ARRANGE
            var pass = buildPassOver(mock(SectorAPI.class));

            // ACT
            var actualResolution = FilteredPolitics.resolveFilteredHolder(pass, "hegemony");

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .isEmpty();
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .isEmpty();
        }

        // The filter hatches a contested cell rather than blanking it, so the one resolution the
        // render pass reads carries no unfilled system even beside a hatched one.
        @Test
        void leavesNoSystemUnfilledEvenWhereTheSpotlitBlocIsContested() {

            // ARRANGE
            var sector = buildContestedSector();

            // ACT
            var actualResolution = resolveFor(sector, "tritachyon");

            // ASSERT
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .containsExactly(Map.entry(buildCellKey(OWNED_SYSTEM), FillState.HATCHED));
        }
    }

    @Nested
    class ResolveFilteredHolderIn {

        // The single-system arm a batch asks under a spotlight settles a system as the whole-sector
        // arm does - the spotlit key in the pick's palette, hatched where the pick lives without
        // winning - so a re-derived system lands where a filtered rebuild draws it.
        @Test
        void answersAPresentButDominatedSystemUnderTheSpotlitKeyHatched() {

            // ARRANGE
            var sector = buildContestedSector();
            var pass = buildPassOver(sector);
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner(SPOTLIT_KEY, buildPaletteOf(TRITACHYON_FACTION_BRIGHT)),
                FillState.HATCHED);

            // ACT
            var actualResolved = FilteredPolitics.resolveFilteredHolderIn(
                buildOnlySystem(sector),
                pass,
                FilteredPolitics.createSpotlight(pass, "tritachyon"));

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // A pick that wins its system holds it outright: the spotlit key, filled solid.
        @Test
        void fillsASystemThePickDominatesSolid() {

            // ARRANGE
            var sector = buildContestedSector();
            var pass = buildPassOver(sector);
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner(SPOTLIT_KEY, buildPaletteOf(HEGEMONY_FACTION_BRIGHT)),
                FillState.SOLID);

            // ACT
            var actualResolved = FilteredPolitics.resolveFilteredHolderIn(
                buildOnlySystem(sector),
                pass,
                FilteredPolitics.createSpotlight(pass, "hegemony"));

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // An absent pick leaves the real holder, solid, which the style layer recedes by its key.
        @Test
        void keepsTheRealHolderSolidWhereThePickIsAbsent() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                OWNED_SYSTEM,
                List.of(hegemony, tritachyon),
                buildVisibleMarket(tritachyon, 3));

            var pass = buildPassOver(sector);
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner("tritachyon", buildPaletteOf(TRITACHYON_FACTION_BRIGHT)),
                FillState.SOLID);

            // ACT
            var actualResolved = FilteredPolitics.resolveFilteredHolderIn(
                buildOnlySystem(sector),
                pass,
                FilteredPolitics.createSpotlight(pass, "hegemony"));

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // A spotlight whose colour faction has gone holds no holder to draw the pick in, and today
        // the held arm keeps the system on its real holder, solid, rather than dropping it - where
        // the claim arm drops the pick's claims outright.
        @Test
        void keepsTheRealHolderSolidWhereTheSpotlightsColourFactionIsGone() {

            // ARRANGE
            var sector = buildContestedSector();
            var expectedResolved = new ResolvedSystemOwner(
                new SystemOwner("hegemony", buildPaletteOf(HEGEMONY_FACTION_BRIGHT)),
                FillState.SOLID);

            // ACT
            var actualResolved = FilteredPolitics.resolveFilteredHolderIn(
                buildOnlySystem(sector),
                buildPassOver(sector),
                new FilteredPolitics.Spotlight("tritachyon", null));

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // A batch hands over a null for a system the sector no longer lists, which nobody owns.
        @Test
        void answersNobodyForASystemTheSectorNoLongerLists() {

            // ARRANGE
            var sector = buildContestedSector();
            var pass = buildPassOver(sector);

            // ACT
            var actualResolved = FilteredPolitics.resolveFilteredHolderIn(
                null,
                pass,
                FilteredPolitics.createSpotlight(pass, "hegemony"));

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }

        // A sector whose economy is not there to read weighs nobody, so a marked system re-derives
        // to nobody rather than faulting the batch.
        @Test
        void answersNobodyOverASectorWithNoEconomy() {

            // ARRANGE
            var pass = buildPassOver(mock(SectorAPI.class));

            // ACT
            var actualResolved = FilteredPolitics.resolveFilteredHolderIn(
                mock(StarSystemAPI.class),
                pass,
                new FilteredPolitics.Spotlight("hegemony", null));

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }
    }

    @Nested
    class CreateSpotlight {

        // Every system the pick is present in carries one holder, so it is made once here: the
        // spotlit key in the pick's own palette, which its bands take too.
        @Test
        void pairsThePickWithTheSpotlitKeyInItsPalette() {

            // ARRANGE
            var pass = buildPassOver(buildContestedSector());
            var expectedSpotlight = new FilteredPolitics.Spotlight(
                "hegemony",
                new SystemOwner(SPOTLIT_KEY, buildPaletteOf(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualSpotlight = FilteredPolitics.createSpotlight(pass, "hegemony");

            // ASSERT
            assertThat(actualSpotlight)
                .isEqualTo(expectedSpotlight);
        }

        // A pick whose colour faction the sector no longer names has no palette to draw in, so the
        // spotlight carries no holder and every system falls back to its real one.
        @Test
        void carriesNoHolderWhereThePicksColourFactionIsGone() {

            // ARRANGE
            var pass = buildPassOver(buildContestedSector());
            var expectedSpotlight = new FilteredPolitics.Spotlight("pirates", null);

            // ACT
            var actualSpotlight = FilteredPolitics.createSpotlight(pass, "pirates");

            // ASSERT
            assertThat(actualSpotlight)
                .isEqualTo(expectedSpotlight);
        }
    }

    // One system hegemony holds against a lighter Tri-Tachyon colony: the shape most cases pose,
    // since it is the smallest sector in which a pick can either win or live without winning.
    private static SectorAPI buildContestedSector() {

        var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
        var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);

        return buildSectorWith(
            OWNED_SYSTEM,
            List.of(hegemony, tritachyon),
            buildVisibleMarket(hegemony, 5),
            buildVisibleMarket(tritachyon, 3));
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
            OWNED_SYSTEM,
            List.of(hegemony, tritachyon),
            buildVisibleMarket(hegemony, 5));

        placeMarketsOnSystemEntities(
            buildOnlySystem(sector),
            buildUnregisteredColony.apply(tritachyon));

        return sector;
    }

    // The two shades a stubbed faction of the given bright colour is painted in.
    private static ColourPair buildPaletteOf(Color brightColour) {
        return new ColourPair(brightColour, buildDarkTheme(brightColour));
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
