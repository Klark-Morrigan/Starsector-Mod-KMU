package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.maplayers.SectorScenarioFixtures;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.buildRulesUnder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration coverage for the holders' inhabitation reads: the whole-sector scans a rebuild takes
 * and the per-system reads a refresh takes - who lives anywhere, and where the spotlit bloc lives -
 * each composed over a real pass, a real colony walk and the real decivilised-world read.
 *
 * <p>Integration rather than unit because the value is the composition. The scan and the read are
 * two scopes of one question, taken minutes apart - the scan where a rebuild began, the read when a
 * colony event marks a system - and one of them composing the two arms differently would leave a
 * single system in the standing set answering under a rule none of its neighbours did. That shows as
 * one cell drawn as backdrop in a settled system, and nothing rebuilds it, since a colony event
 * marks a system once. Stub the pass and the wiring this exists to catch is what gets asserted.
 *
 * <p>The derelict is the case the pair is read against throughout. One passes the fog and nobody
 * lives on it, so a read wired to the listing rather than to habitation would report every derelict
 * in the sector as a settled system - and would answer identically on every case that stages no
 * derelict, which is nearly all of them.
 */
final class HolderInhabitationIntegrationTests {

    // The size every staged colony carries. Nothing inhabitation reads weighs a colony, so a case
    // varying this would vary nothing the answer can see.
    private static final int COLONY_SIZE = 5;

    // A rival colony larger than the spotlit bloc's, for the one case pinning that living in a system
    // is not winning it: a read that ranked the blocs would drop the smaller one.
    private static final int RIVAL_COLONY_SIZE = 9;

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    // Who owns the derelict the scenario fixture stages. Spotlit in the derelict case, so the read is
    // asked about the very bloc the wreck names.
    private static final String DERELICT_OWNER_ID = Factions.NEUTRAL;

    private static final FactionAPI HEGEMONY_FACTION = SectorOwnershipFixtures.buildFaction("hegemony");
    private static final FactionAPI TRITACHYON_FACTION = SectorOwnershipFixtures.buildFaction("tritachyon");

    private static final String SETTLED_SYSTEM = "settled";
    private static final String EMPTY_SYSTEM = "empty";

    @Nested
    class ReadInhabitedSystemKeys {

        @Test
        void namesOnlyTheSystemsSomebodyLivesIn() {
            // The set the factionless classifier reads. It has to name the settled systems and only
            // those, since a system missing from it draws as the empty backdrop the
            // uninhabited-systems checkbox switches off.
            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets(SETTLED_SYSTEM, buildColony()),
                SectorOwnershipFixtures.listSystemMarkets(EMPTY_SYSTEM));

            assertThat(readInhabitedSystemKeys(sector, BASE_FOG))
                .containsExactly(buildCellKey(SETTLED_SYSTEM));
        }

        @Test
        void leavesOutASystemHoldingOnlyADerelict() {
            // The derelict passes the fog - un-hidden, on a found entity - so this is the habitation
            // read's own case rather than a fog case wearing a derelict's clothes. A system with
            // one wreck in it and nothing else is empty space, and the cell has to draw as such.
            var sector = SectorOwnershipFixtures.buildSectorWith(EMPTY_SYSTEM);

            SectorScenarioFixtures.placeDerelictIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(readInhabitedSystemKeys(sector, BASE_FOG))
                .isEmpty();
        }

        @Test
        void namesASystemHoldingARevealedDecivilisedWorld() {
            // The arm no colony read answers - nobody owns a decivilised world - so a scan that had
            // dropped it would take every decivilised system off the map at once.
            var sector = SectorOwnershipFixtures.buildSectorWith(EMPTY_SYSTEM);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(
                SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(readInhabitedSystemKeys(sector, BASE_FOG))
                .containsExactly(buildCellKey(EMPTY_SYSTEM));
        }

        @Test
        void widensUnderTheRevealTheRestOfThePassResolvedUnder() {
            // The rule reaches the scan rather than being applied over its result: a system left
            // out of the scan has no cell to reveal. It arrives on the pass, so the scan cannot be
            // reading a rule the holding beside it is not.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SETTLED_SYSTEM,
                SectorOwnershipFixtures.buildUndiscoveredOpenMarket(
                    SectorOwnershipFixtures.buildFaction("hegemony"),
                    COLONY_SIZE));

            assertThat(readInhabitedSystemKeys(sector, BASE_FOG))
                .isEmpty();
            assertThat(readInhabitedSystemKeys(sector, UNDER_THE_REVEAL))
                .containsExactly(buildCellKey(SETTLED_SYSTEM));
        }

        @Test
        void namesNobodyForAPassOverNoSector() {
            // The unreachable-sector case: the pass walks no systems, so the scan reports none
            // rather than guarding a sector it was never given.
            assertThat(readInhabitedSystemKeys(null, BASE_FOG))
                .isEmpty();
        }
    }

    @Nested
    class IsSystemInhabited {

        @Test
        void agreesWithTheScanOverTheSameSystem() {
            // The guarantee this class exists for. A refresh re-deriving one system has to land the
            // answer the rebuild's scan gave it, or that one cell draws as backdrop inside a
            // settled system until something else rebuilds the map.
            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets(SETTLED_SYSTEM, buildColony()),
                SectorOwnershipFixtures.listSystemMarkets(EMPTY_SYSTEM));

            var pass = buildPassOver(sector, BASE_FOG);
            var systems = sector.getStarSystems();

            assertThat(HolderInhabitation.isSystemInhabited(pass, systems.get(0)))
                .isTrue();
            assertThat(HolderInhabitation.isSystemInhabited(pass, systems.get(1)))
                .isFalse();
        }

        @Test
        void leavesOutASystemHoldingOnlyADerelict() {
            // The per-system arm reads habitation exactly as the scan does. Wiring one of the two
            // to the listing instead would have a marked derelict-only system flip to settled the
            // moment an event touched it, and stay that way.
            var sector = SectorOwnershipFixtures.buildSectorWith(EMPTY_SYSTEM);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            SectorScenarioFixtures.placeDerelictIn(system);

            assertThat(HolderInhabitation.isSystemInhabited(
                    buildPassOver(sector, BASE_FOG),
                    system))
                .isFalse();
        }

        @Test
        void readsTheDecivilisedArmBesideTheColonyOne() {
            // Both arms in the one read, so a refresh cannot take a decivilised system off the map
            // by answering on colonies alone.
            var sector = SectorOwnershipFixtures.buildSectorWith(EMPTY_SYSTEM);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            assertThat(HolderInhabitation.isSystemInhabited(
                    buildPassOver(sector, BASE_FOG),
                    system))
                .isTrue();
        }

        @Test
        void reportsNobodyForASystemThatIsNotThere() {
            // A refresh handed a system the sector no longer lists answers rather than faulting,
            // on the same terms every read through the pass does.
            assertThat(HolderInhabitation.isSystemInhabited(
                    buildPassOver(mock(SectorAPI.class), BASE_FOG),
                    null))
                .isFalse();
        }
    }

    @Nested
    class FindSpotlitPresenceSystemKeys {

        @Test
        void namesTheCandidatesTheSpotlitBlocLivesIn() {

            var sector = buildTwoSystemSector();

            var corvusKey = readSystemKey(sector, "corvus");
            var askoniaKey = readSystemKey(sector, "askonia");

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(corvusKey, askoniaKey)))
                .containsExactly(corvusKey);
        }

        @Test
        void leavesOutASystemThatIsNoCandidate() {
            // The holder map already drew every system it resolved, so a system outside the
            // candidates is never answered for, wherever the bloc lives.
            var sector = buildTwoSystemSector();

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(readSystemKey(sector, "askonia"))))
                .isEmpty();
        }

        @Test
        void answersNothingWithNoBlocSpotlit() {

            var sector = buildTwoSystemSector();

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    null,
                    Set.of(readSystemKey(sector, "corvus"))))
                .isEmpty();
        }

        @Test
        void answersNothingWithNoCandidates() {

            var sector = buildTwoSystemSector();

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of()))
                .isEmpty();
        }

        @Test
        void keepsTheCandidatesOwnOrder() {
            // The spared cells are walked in the order the holder map handed them over, so the read
            // keeps that order rather than one of its own.
            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets(
                    "corvus",
                    SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)),
                SectorOwnershipFixtures.listSystemMarkets(
                    "hybrasil",
                    SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)));

            var candidateKeys = new LinkedHashSet<SystemKey>();

            candidateKeys.add(readSystemKey(sector, "hybrasil"));
            candidateKeys.add(readSystemKey(sector, "corvus"));

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    candidateKeys))
                .containsExactly(readSystemKey(sector, "hybrasil"), readSystemKey(sector, "corvus"));
        }

        @Test
        void leavesOutACandidateTheSectorNoLongerLists() {
            // A key the sector dropped reaches the read as no system at all, and nobody lives in it.
            var elsewhere = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets(
                    "elsewhere",
                    SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)));

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(buildTwoSystemSector()),
                    SPOTLIT_BLOC_ID,
                    Set.of(readSystemKey(elsewhere, "elsewhere"))))
                .isEmpty();
        }

        @Test
        void answersNothingBeforeTheEconomyStandsUp() {
            // Mid-load the systems are walkable while the economy is not, so no colony can be read
            // and nothing is reported present rather than every system reported empty.
            var sector = SectorOwnershipFixtures.buildEconomylessSectorWithSystem("corvus");

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(readSystemKey(sector, "corvus"))))
                .isEmpty();
        }

        @Test
        void answersNothingForAPassOverNoSector() {
            // The unreachable-sector case: the pass walks no systems, so no candidate is answered for.
            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(null),
                    SPOTLIT_BLOC_ID,
                    Set.of(buildCellKey(SETTLED_SYSTEM))))
                .isEmpty();
        }

        @Test
        void namesACandidateTheSpotlitBlocSharesWithALargerRival() {
            // Living there, not winning there: a candidate has no holder, so there is no contest to
            // win. A bloc living beside a larger rival counts exactly as one living alone does.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SETTLED_SYSTEM,
                List.of(),
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(TRITACHYON_FACTION, RIVAL_COLONY_SIZE));

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(buildCellKey(SETTLED_SYSTEM))))
                .containsExactly(buildCellKey(SETTLED_SYSTEM));
        }

        @Test
        void namesACandidateWhereTheSpotlitBlocHoldsOnlyAnUnregisteredColony() {
            // The shape this read exists for. The economy never registered the bloc's one station,
            // which is why nothing holds the system and why it is a candidate at all. Presence is
            // read off the colonies rather than off the economy's listing, so the bloc still lives here.
            var sector = SectorOwnershipFixtures.buildSectorWith(SETTLED_SYSTEM);

            SectorOwnershipFixtures.placeMarketsOnSystemEntities(
                SectorOwnershipFixtures.buildOnlySystem(sector),
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(buildCellKey(SETTLED_SYSTEM))))
                .containsExactly(buildCellKey(SETTLED_SYSTEM));
        }

        @Test
        void leavesOutACandidateWhoseOnlyMarketOfTheSpotlitBlocIsADerelict() {
            // Presence is habitation, so a derelict's owner does not live here, however plainly the
            // box over the cell names it. Sparing this cell would keep a fill over a system the same
            // pass classified as empty backdrop.
            var sector = SectorOwnershipFixtures.buildSectorWith(EMPTY_SYSTEM);

            SectorScenarioFixtures.placeDerelictIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(HolderInhabitation.findSpotlitPresenceSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    DERELICT_OWNER_ID,
                    Set.of(buildCellKey(EMPTY_SYSTEM))))
                .isEmpty();
        }
    }

    @Nested
    class IsBlocPresentIn {

        @Test
        void answersYesWhereTheBlocLives() {

            var sector = buildTwoSystemSector();

            assertThat(HolderInhabitation.isBlocPresentIn(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    SectorOwnershipFixtures.findSystemIn(sector, "corvus")))
                .isTrue();
        }

        @Test
        void answersNoWhereOnlyARivalLives() {

            var sector = buildTwoSystemSector();

            assertThat(HolderInhabitation.isBlocPresentIn(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    SectorOwnershipFixtures.findSystemIn(sector, "askonia")))
                .isFalse();
        }

        @Test
        void answersNoWithNoBlocSpotlit() {

            var sector = buildTwoSystemSector();

            assertThat(HolderInhabitation.isBlocPresentIn(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    null,
                    SectorOwnershipFixtures.findSystemIn(sector, "corvus")))
                .isFalse();
        }

        @Test
        void answersNoForASystemTheSectorNoLongerLists() {
            // A marked key the sector dropped reaches a batch as no system at all, and nobody can
            // be living in it.
            assertThat(HolderInhabitation.isBlocPresentIn(
                    SectorOwnershipFixtures.buildHolderPassOver(buildTwoSystemSector()),
                    SPOTLIT_BLOC_ID,
                    null))
                .isFalse();
        }

        @Test
        void answersNoBeforeTheEconomyStandsUp() {
            // The single-system arm answers the mid-load sector exactly as the sector-wide scan does,
            // so a batch run then cannot spare a cell the rebuild would not.
            var sector = SectorOwnershipFixtures.buildEconomylessSectorWithSystem("corvus");

            assertThat(HolderInhabitation.isBlocPresentIn(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    SectorOwnershipFixtures.findSystemIn(sector, "corvus")))
                .isFalse();
        }
    }

    // The scan under one rule, off a pass opened over the sector as a rebuild opens it.
    private static Set<SystemKey> readInhabitedSystemKeys(
            SectorAPI sector,
            ColonyVisibility colonyVisibility) {

        return HolderInhabitation.readInhabitedSystemKeys(
            buildPassOver(sector, colonyVisibility));
    }

    // A plain identity pass under the given rule: what is varied across these cases is the rule and
    // the fixture, never the grouping, inhabitation folding no blocs of its own.
    private static HolderPass buildPassOver(SectorAPI sector, ColonyVisibility colonyVisibility) {
        return HolderPass.createOver(sector, buildRulesUnder(colonyVisibility), HolderGrouping.identity());
    }

    // An ordinary colony on a found entity: the plain "somebody lives here" case.
    private static MarketAPI buildColony() {
        return SectorOwnershipFixtures.buildVisibleMarket(
            SectorOwnershipFixtures.buildFaction("hegemony"),
            COLONY_SIZE);
    }

    // Corvus holds a colony of the spotlit bloc; Askonia holds only a rival's.
    private static SectorAPI buildTwoSystemSector() {

        return SectorOwnershipFixtures.buildSectorWithSystems(
            List.of(),
            SectorOwnershipFixtures.listSystemMarkets(
                "corvus",
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)),
            SectorOwnershipFixtures.listSystemMarkets(
                "askonia",
                SectorOwnershipFixtures.buildVisibleMarket(TRITACHYON_FACTION, COLONY_SIZE)));
    }

    // The key the pass reads a staged system under, so a case names its candidates the same way.
    private static SystemKey readSystemKey(SectorAPI sector, String systemId) {
        return SystemKey.readKeyOf(SectorOwnershipFixtures.findSystemIn(sector, systemId));
    }
}
