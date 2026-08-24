package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.maplayers.SectorScenarioFixtures;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration coverage for the political map's inhabitation read: the whole-sector scan a rebuild
 * takes and the per-system read a refresh takes, each composed over a real pass, a real colony walk
 * and the real ruin read.
 *
 * <p>Integration rather than unit because the value is the composition. The scan and the read are
 * two scopes of one question, taken minutes apart - the scan where a rebuild began, the read when a
 * colony event marks a system - and one of them composing the two arms differently would leave a
 * single system in the standing set answering under a rule none of its neighbours did. That shows as
 * one cell drawn as backdrop in a settled system, and nothing rebuilds it, since a colony event
 * marks a system once. Stub the pass and the wiring this exists to catch is what gets asserted.
 *
 * <p>The derelict is the case the pair is read against throughout. A hulk passes the fog and nobody
 * lives on it, so a read wired to the listing rather than to habitation would report every derelict
 * in the sector as a settled system - and would answer identically on every case that stages no
 * hulk, which is nearly all of them.
 */
final class PoliticalMapInhabitationIntegrationTest {

    // The size every staged colony carries. Nothing inhabitation reads weighs a colony, so a case
    // varying this would vary nothing the answer can see.
    private static final int COLONY_SIZE = 5;

    private static final String SETTLED_SYSTEM = "settled";
    private static final String EMPTY_SYSTEM = "empty";

    @Nested
    class ReadInhabitedSystemIds {

        @Test
        void namesOnlyTheSystemsSomebodyLivesIn() {
            // The set the factionless classifier reads. It has to name the settled systems and only
            // those, since a system missing from it draws as the empty backdrop the
            // uninhabited-systems checkbox switches off.
            var sector = SectorPoliticsFixtures.buildSectorWithSystems(
                List.of(),
                SectorPoliticsFixtures.listSystemMarkets(SETTLED_SYSTEM, buildColony()),
                SectorPoliticsFixtures.listSystemMarkets(EMPTY_SYSTEM));

            assertThat(readInhabitedSystemIds(sector, BASE_FOG))
                .containsExactly(SETTLED_SYSTEM);
        }

        @Test
        void leavesOutASystemHoldingOnlyADerelict() {
            // The hulk passes the fog - un-hidden, on a found entity - so this is the habitation
            // read's own case rather than a fog case wearing a derelict's clothes. A system with
            // one wreck in it and nothing else is empty space, and the cell has to draw as such.
            var sector = SectorPoliticsFixtures.buildSectorWith(EMPTY_SYSTEM);

            SectorScenarioFixtures.placeDerelictIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(readInhabitedSystemIds(sector, BASE_FOG))
                .isEmpty();
        }

        @Test
        void namesASystemHoldingARevealedRuin() {
            // The arm no colony read answers - nobody owns a dead world - so a scan that had
            // dropped it would take every decivilised system off the map at once.
            var sector = SectorPoliticsFixtures.buildSectorWith(EMPTY_SYSTEM);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(
                SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(readInhabitedSystemIds(sector, BASE_FOG))
                .containsExactly(EMPTY_SYSTEM);
        }

        @Test
        void widensUnderTheRevealTheRestOfThePassResolvedUnder() {
            // The rule reaches the scan rather than being applied over its result: a system left
            // out of the scan has no cell to reveal. It arrives on the pass, so the scan cannot be
            // reading a rule the holding beside it is not.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SETTLED_SYSTEM,
                SectorPoliticsFixtures.buildUndiscoveredOpenMarket(
                    SectorPoliticsFixtures.buildFaction("hegemony"),
                    COLONY_SIZE));

            assertThat(readInhabitedSystemIds(sector, BASE_FOG))
                .isEmpty();
            assertThat(readInhabitedSystemIds(sector, UNDER_THE_REVEAL))
                .containsExactly(SETTLED_SYSTEM);
        }

        @Test
        void namesNobodyForAPassOverNoSector() {
            // The unreachable-sector case: the pass walks no systems, so the scan reports none
            // rather than guarding a sector it was never given.
            assertThat(readInhabitedSystemIds(null, BASE_FOG))
                .isEmpty();
        }
    }

    @Nested
    class IsSystemInhabited {

        @Test
        void agreesWithTheScanOverTheSameSystem() {
            // The claim this class exists for. A refresh re-deriving one system has to land the
            // answer the rebuild's scan gave it, or that one cell draws as backdrop inside a
            // settled system until something else rebuilds the map.
            var sector = SectorPoliticsFixtures.buildSectorWithSystems(
                List.of(),
                SectorPoliticsFixtures.listSystemMarkets(SETTLED_SYSTEM, buildColony()),
                SectorPoliticsFixtures.listSystemMarkets(EMPTY_SYSTEM));

            var pass = buildPassOver(sector, BASE_FOG);
            var systems = sector.getStarSystems();

            assertThat(PoliticalMapInhabitation.isSystemInhabited(pass, systems.get(0)))
                .isTrue();
            assertThat(PoliticalMapInhabitation.isSystemInhabited(pass, systems.get(1)))
                .isFalse();
        }

        @Test
        void leavesOutASystemHoldingOnlyADerelict() {
            // The per-system arm reads habitation exactly as the scan does. Wiring one of the two
            // to the listing instead would have a marked derelict-only system flip to settled the
            // moment an event touched it, and stay that way.
            var sector = SectorPoliticsFixtures.buildSectorWith(EMPTY_SYSTEM);
            var system = SectorPoliticsFixtures.buildOnlySystem(sector);

            SectorScenarioFixtures.placeDerelictIn(system);

            assertThat(PoliticalMapInhabitation.isSystemInhabited(
                    buildPassOver(sector, BASE_FOG),
                    system))
                .isFalse();
        }

        @Test
        void readsTheRuinArmBesideTheColonyOne() {
            // Both arms in the one read, so a refresh cannot take a decivilised system off the map
            // by answering on colonies alone.
            var sector = SectorPoliticsFixtures.buildSectorWith(EMPTY_SYSTEM);
            var system = SectorPoliticsFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            assertThat(PoliticalMapInhabitation.isSystemInhabited(
                    buildPassOver(sector, BASE_FOG),
                    system))
                .isTrue();
        }

        @Test
        void reportsNobodyForASystemThatIsNotThere() {
            // A refresh handed a system the sector no longer lists answers rather than faulting,
            // on the same terms every read through the pass does.
            assertThat(PoliticalMapInhabitation.isSystemInhabited(
                    buildPassOver(mock(SectorAPI.class), BASE_FOG),
                    null))
                .isFalse();
        }
    }

    // The scan under one rule, off a pass opened over the sector as a rebuild opens it.
    private static Set<String> readInhabitedSystemIds(
            SectorAPI sector,
            ColonyVisibility colonyVisibility) {

        return PoliticalMapInhabitation.readInhabitedSystemIds(
            buildPassOver(sector, colonyVisibility));
    }

    // A plain identity pass under the given rule: what is varied across these cases is the rule and
    // the fixture, never the grouping, inhabitation folding no blocs of its own.
    private static HolderPass buildPassOver(SectorAPI sector, ColonyVisibility colonyVisibility) {
        return HolderPass.over(sector, colonyVisibility, HolderGrouping.identity());
    }

    // An ordinary colony on a found entity: the plain "somebody lives here" case.
    private static MarketAPI buildColony() {
        return SectorPoliticsFixtures.buildVisibleMarket(
            SectorPoliticsFixtures.buildFaction("hegemony"),
            COLONY_SIZE);
    }

}
