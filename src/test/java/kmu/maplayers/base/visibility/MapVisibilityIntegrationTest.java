package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the on-map rule: {@link MapVisibility}
 * composing the real {@link StarSystems}, {@link Colonies}, {@link VisibleStars},
 * and {@link DecivilisedMarkets}. A reachable system appears; an unreachable one
 * appears once inhabited - a colony the player knows of, registered with the economy
 * or not, or a revealed decivilised planet - and otherwise stays off; and the
 * fingerprint shifts when a system joins the on-map set. Exercised together because
 * the value is the composition: a mock of each rule would hide whether they are wired
 * in the right order.
 */
class MapVisibilityIntegrationTest {

    // The force override on, so the rule admits a system regardless of access or
    // inhabitation - the widening the pre-computed entry point reads off the value.
    // The "show all factions" reveal on: the fog lifted outright, and no gate held, which
    // is the widest rule any surface reads under.
    private static final ColonyVisibility UNDER_THE_REVEAL =
        new ColonyVisibility(true, Set.of());

    private static final MapVisibilityOverrides FORCED_ONTO_MAP =
        new MapVisibilityOverrides(ColonyVisibility.BASE_FOG, true);

    // The undiscovered-colony widening on, the other component of the same value: an
    // undiscovered colony counts as inhabitation.
    private static final MapVisibilityOverrides INCLUDING_UNDISCOVERED_MARKETS =
        new MapVisibilityOverrides(UNDER_THE_REVEAL, false);

    // The inhabitation the pre-computed entry point is handed when the caller has already
    // decided the system holds nothing, so admission rests on access or the force override.
    private static final boolean UNINHABITED = false;

    // The ruin flag a caller that already read the system's planets hands to the inhabitation
    // rule, so the colony set is the only other thing that could be answering.
    private static final boolean IS_REVEALED_DECIVILISED = true;

    @Nested
    class ShouldAppearOnMap {

        @Test
        void shouldAppearOnMapIsTrueForReachableSystem() {

            var system = buildReachableSystem("a");

            assertThat(shouldAppearOnMapUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void shouldAppearOnMapIsTrueForUnreachableSystemHoldingAColony() {
            // No jump point, so unreachable by the access rule, but a discovered
            // colony makes it inhabited and admits it to the map.
            var system = buildUnreachableSystem("a");

            assertThat(shouldAppearOnMapUnderNoReveal(
                    buildSectorWith(system, buildOwnedMarket()),
                    system))
                .isTrue();
        }

        @Test
        void shouldAppearOnMapIsTrueForUnreachableSystemWithARevealedDecivilisedPlanet() {

            var system = buildUnreachableSystemWithDecivilisedPlanet("a");

            assertThat(shouldAppearOnMapUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void shouldAppearOnMapIsFalseForUnreachableUninhabitedSystem() {

            var system = buildUnreachableSystem("a");

            assertThat(shouldAppearOnMapUnderNoReveal(buildSectorWith(system), system))
                .isFalse();
        }

        @Test
        void shouldAppearOnMapIsFalseForReachableSystemWhoseStarIsHiddenOnMap() {
            // Reachable by a jump point, but the vanilla map hides its star (an
            // abyssal rogue object), so reachability alone does not admit it.
            var system = buildReachableSystem("a");

            assertThat(shouldAppearOnMapUnderNoReveal(buildSectorWithHiddenStar(system), system))
                .isFalse();
        }

        @Test
        void shouldAppearOnMapIsTrueForReachableNebulaWithNoVisibleStar() {
            // A nebula has no star anchor, so it is never in the visible-star
            // index; the vanilla map draws it as a cloud, so the rule must admit it
            // on the access path without an inhabitation.
            var system = buildReachableNebula("a");

            assertThat(shouldAppearOnMapUnderNoReveal(buildSectorWithoutStarAnchors(system), system))
                .isTrue();
        }

        @Test
        void shouldAppearOnMapIsTrueForUninhabitedSystemWhenForcedOntoMap() {
            // The force override admits a system the normal rule omits - unreachable and
            // uninhabited - so the full partition can be inspected.
            var system = buildUnreachableSystem("a");
            var visibleStars = VisibleStars.scan(buildSectorWithoutStarAnchors(system));

            assertThat(MapVisibility.shouldAppearOnMap(
                    system,
                    visibleStars,
                    UNINHABITED,
                    FORCED_ONTO_MAP))
                .isTrue();
        }

        @Test
        void shouldAppearOnMapIsFalseForUninhabitedSystemWhenNotForced() {
            // Without the force override the same unreachable, uninhabited system
            // stays off, so the reveal is what admits it, not the fixture.
            var system = buildUnreachableSystem("a");
            var visibleStars = VisibleStars.scan(buildSectorWithoutStarAnchors(system));

            assertThat(MapVisibility.shouldAppearOnMap(
                    system,
                    visibleStars,
                    UNINHABITED,
                    MapVisibilityOverrides.NONE))
                .isFalse();
        }
    }

    @Nested
    class IsInhabited {

        @Test
        void isInhabitedIsTrueWhenADiscoveredColonyExists() {

            var system = buildUnreachableSystem("a");

            assertThat(isInhabitedUnderNoReveal(buildSectorWith(system, buildOwnedMarket()), system))
                .isTrue();
        }

        @Test
        void isInhabitedIsTrueWhenARevealedDecivilisedPlanetExists() {

            var system = buildUnreachableSystemWithDecivilisedPlanet("a");

            assertThat(isInhabitedUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void isInhabitedIsFalseWhenNeitherColonyNorRuinExists() {

            var system = buildUnreachableSystem("a");

            assertThat(isInhabitedUnderNoReveal(buildSectorWith(system), system))
                .isFalse();
        }

        @Test
        void isInhabitedIsFalseForAnUndiscoveredColonyByDefault() {
            // An undiscovered colony fails the normal known-to-player gate, so the system
            // reads as uninhabited until the reveal is on.
            var system = buildUnreachableSystem("a");

            assertThat(isInhabitedUnderNoReveal(
                    buildSectorWith(system, buildUndiscoveredColony()),
                    system))
                .isFalse();
        }

        @Test
        void isInhabitedIsTrueForAColonyTheEconomyDoesNotList() {
            // Galatia Academy's shape: a real colony on a real station vanilla never registers.
            // Reading the economy's listing alone would leave such a system classified as empty
            // backdrop while every box drawn over it names the faction holding it.
            var system = buildUnreachableSystemHoldingUnlistedColony("a");

            assertThat(isInhabitedUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void isInhabitedHonoursARevealedRuinTheCallerAlreadyRead() {
            // The pre-read entry point: a caller that has the ruin flag in hand (the sector
            // scan, which needs it to salt the fingerprint) hands it over rather than paying a
            // second walk of every planet. A dropped argument would look identical everywhere
            // else, every other case passing a colony set that answers on its own.
            assertThat(MapVisibility.isInhabited(
                    Colonies.NONE,
                    IS_REVEALED_DECIVILISED,
                    MapVisibilityOverrides.NONE))
                .isTrue();
        }

        @Test
        void isInhabitedIsTrueForAnUndiscoveredColonyWhenTheyAreIncluded() {
            // The widening folds the undiscovered colony in, so the system counts as
            // inhabited and earns a cell.
            var system = buildUnreachableSystem("a");

            assertThat(MapVisibility.isInhabited(
                    buildSectorWith(system, buildUndiscoveredColony()),
                    system,
                    INCLUDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }
    }

    @Nested
    class FindInhabitedSystemIds {

        @Test
        void findInhabitedSystemIdsReportsOnlyTheSystemsHoldingSomething() {
            // The set the factionless classifier reads. It has to name the settled systems and
            // only those, since a system missing from it draws as the empty backdrop the
            // uninhabited-systems checkbox switches off.
            var settled = buildUnreachableSystem("settled");
            var empty = buildUnreachableSystem("empty");

            assertThat(MapVisibility.findInhabitedSystemIds(
                    buildSectorWithMarketsFor(settled, buildOwnedMarket(), empty),
                    MapVisibilityOverrides.NONE))
                .containsExactly("settled");
        }

        @Test
        void findInhabitedSystemIdsReportsARevealedRuinAlongsideALiveColony() {
            // Both reasons a system counts as settled land in the one set, so the classifier
            // cannot tell a dead colony from a live one it found no holder for - which is what
            // lets the two share a category.
            var colonised = buildUnreachableSystem("colonised");
            var ruined = buildUnreachableSystemWithDecivilisedPlanet("ruined");

            assertThat(MapVisibility.findInhabitedSystemIds(
                    buildSectorWithMarketsFor(colonised, buildOwnedMarket(), ruined),
                    MapVisibilityOverrides.NONE))
                .containsExactlyInAnyOrder("colonised", "ruined");
        }

        @Test
        void findInhabitedSystemIdsHonoursTheUndiscoveredColonyReveal() {
            // The roll-up hands its overrides down rather than judging inhabitation itself, and a
            // dropped argument would look identical at every other case here - all of which pass
            // the no-reveal value. Only a system that flips on the reveal catches it.
            var system = buildUnreachableSystem("undiscovered");
            var sector = buildSectorWith(system, buildUndiscoveredColony());

            assertThat(MapVisibility.findInhabitedSystemIds(sector, MapVisibilityOverrides.NONE))
                .isEmpty();

            assertThat(MapVisibility.findInhabitedSystemIds(
                    sector,
                    INCLUDING_UNDISCOVERED_MARKETS))
                .containsExactly("undiscovered");
        }

        @Test
        void findInhabitedSystemIdsIsEmptyForANullSector() {
            assertThat(MapVisibility.findInhabitedSystemIds(null, MapVisibilityOverrides.NONE))
                .isEmpty();
        }
    }

    @Nested
    class ComputeVisibilityContribution {

        @Test
        void visibilityContributionDiffersBetweenSystems() {
            // Distinct ids must land distinct contributions so two systems do not
            // cancel when summed into the fingerprint.
            assertThat(MapVisibility.computeVisibilityContribution("a", false))
                .isNotEqualTo(MapVisibility.computeVisibilityContribution("b", false));
        }

        @Test
        void visibilityContributionShiftsWhenASystemBecomesDecivilised() {
            // A live-to-dead flip on the same system - its draw class changing while
            // it stays on the map - must move its contribution via the deciv salt.
            assertThat(MapVisibility.computeVisibilityContribution("a", true))
                .isNotEqualTo(MapVisibility.computeVisibilityContribution("a", false));
        }

        @Test
        void visibilityContributionIsNonZeroForAnIdThatHashesToZero() {
            // The avalanche has a fixed point at 0, so an unseeded 0-hash id would
            // contribute 0 and be invisible to the summed fingerprint - the system
            // could enter or leave the map without moving it. The empty string is
            // the canonical 0-hash id; the seed spreads it to a non-zero value.
            var idHashingToZero = "";

            assertThat(idHashingToZero.hashCode())
                .isZero();
            assertThat(MapVisibility.computeVisibilityContribution(idHashingToZero, false))
                .isNotZero();
        }

        @Test
        void summedFingerprintMovesWhenAZeroHashSystemJoinsTheDrawnSet() {
            // The fingerprint is a sum of contributions, so a system joining the
            // drawn set must change it - including a 0-hash id, whose contribution
            // has to be non-zero for its arrival to register in the sum.
            assertThat("".hashCode())
                .isZero();

            var before = MapVisibility.computeVisibilityContribution("a", false);
            var after = before + MapVisibility.computeVisibilityContribution("", false);

            assertThat(after)
                .isNotEqualTo(before);
        }

        @Test
        void visibilityContributionShiftsWhenAZeroHashSystemBecomesDecivilised() {
            // A draw-class flip must move the contribution even for a 0-hash id, so a
            // live-to-dead change on such a system still moves the summed fingerprint
            // rather than reading identically live and dead.
            assertThat("".hashCode())
                .isZero();
            assertThat(MapVisibility.computeVisibilityContribution("", true))
                .isNotEqualTo(MapVisibility.computeVisibilityContribution("", false));
        }
    }

    // The whole rule over one system with no reveal applied: scans the sector's visible
    // stars, then asks the entry point that reads inhabitation itself. Bound here once so
    // the scenarios below differ only in the fixture they stage, not in the scan and the
    // no-reveal value each would otherwise repeat.
    private static boolean shouldAppearOnMapUnderNoReveal(
            SectorAPI sector,
            StarSystemAPI system) {

        return MapVisibility.shouldAppearOnMap(
            sector,
            system,
            VisibleStars.scan(sector),
            MapVisibilityOverrides.NONE);
    }

    // The inhabitation read with no reveal applied, so the normal known-to-player gate
    // stands. Bound here for the same reason as the rule helper above: the no-reveal value is
    // the constant across these scenarios, and only the fixture varies.
    private static boolean isInhabitedUnderNoReveal(
            SectorAPI sector,
            StarSystemAPI system) {

        return MapVisibility.isInhabited(sector, system, MapVisibilityOverrides.NONE);
    }

    // Wires a single-system sector whose economy returns the given markets for
    // that system - the read the inhabitation rule makes when judging colonies.
    private static SectorAPI buildSectorWith(StarSystemAPI system, MarketAPI... markets) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(system))
            .thenReturn(List.of(markets));

        // The hyperspace mock is built before the getHyperspace() stubbing so its
        // own stubbing is not nested inside this one.
        var hyperspaceMock = buildHyperspaceWithVisibleStarAnchorFor(system);
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(system));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);

        return sectorMock;
    }

    // A two-system sector where only the first holds markets, for the sector-wide scan: the
    // second is there so a scan that reported every system rather than the settled ones would
    // be caught. No hyperspace stubbing, the scan reading the economy alone.
    private static SectorAPI buildSectorWithMarketsFor(
            StarSystemAPI marketSystem,
            MarketAPI market,
            StarSystemAPI marketlessSystem) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(marketSystem))
            .thenReturn(List.of(market));
        when(economyMock.getMarkets(marketlessSystem))
            .thenReturn(List.of());

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(marketSystem, marketlessSystem));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        return sectorMock;
    }

    // A reachable system whose only star anchor is hidden on the map, with an
    // empty economy so the inhabited path cannot admit it either.
    private static SectorAPI buildSectorWithHiddenStar(StarSystemAPI system) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(system))
            .thenReturn(List.of());

        var hyperspaceMock = buildHyperspaceWithHiddenStarAnchorFor(system);
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(system));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);

        return sectorMock;
    }

    // A star anchor tagged hidden leading into the system, so the vanilla map
    // draws no star and the system reads as map-invisible.
    private static LocationAPI buildHyperspaceWithHiddenStarAnchorFor(StarSystemAPI system) {

        var anchorMock = mock(JumpPointAPI.class);

        when(anchorMock.isStarAnchor())
            .thenReturn(true);
        when(anchorMock.hasTag(Tags.STAR_HIDDEN_ON_MAP))
            .thenReturn(true);
        when(anchorMock.getDestinationStarSystem())
            .thenReturn(system);

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of(anchorMock));

        return hyperspaceMock;
    }

    // A visible (untagged) star anchor leading into the system, so a reachable
    // system reads as map-visible. A system with no jump point stays off the map
    // regardless, so its anchor cannot wrongly admit it.
    private static LocationAPI buildHyperspaceWithVisibleStarAnchorFor(StarSystemAPI system) {

        var anchorMock = mock(JumpPointAPI.class);

        when(anchorMock.isStarAnchor())
            .thenReturn(true);
        when(anchorMock.getDestinationStarSystem())
            .thenReturn(system);

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of(anchorMock));

        return hyperspaceMock;
    }

    // A sector whose hyperspace holds no star anchor, so no system reads as
    // star-visible: the route onto the map is the nebula draw or inhabitation.
    private static SectorAPI buildSectorWithoutStarAnchors(StarSystemAPI system) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(system))
            .thenReturn(List.of());

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of());

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(system));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);

        return sectorMock;
    }

    private static StarSystemAPI buildReachableSystem(String id) {
        // Wired into hyperspace by a jump point and its star drawn on the map, so
        // the access rule admits it.
        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(id);
        when(systemMock.getLocation())
            .thenReturn(new Vector2f(1f, 1f));
        when(systemMock.getJumpPoints())
            .thenReturn(List.of(mock(SectorEntityToken.class)));

        return systemMock;
    }

    private static StarSystemAPI buildReachableNebula(String id) {
        // Reachable, but drawn on the map as a nebula cloud rather than a star, so
        // it never appears in the visible-star index - its draw is the nebula flag.
        var systemMock = buildReachableSystem(id);

        when(systemMock.isNebula())
            .thenReturn(true);

        return systemMock;
    }

    private static StarSystemAPI buildUnreachableSystem(String id) {
        // No jump point and not cut off - reachable only by transverse jump, so
        // the access rule rejects it; inhabitation is its only route onto the map.
        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(id);
        when(systemMock.getLocation())
            .thenReturn(new Vector2f(2f, 2f));
        when(systemMock.getJumpPoints())
            .thenReturn(List.of());

        return systemMock;
    }

    private static StarSystemAPI buildUnreachableSystemHoldingUnlistedColony(String id) {
        // The colony hangs on one of the system's own entities and is absent from the economy's
        // listing, so only the entity walk finds it. The market and its entity finish their own
        // stubbing before the system's opens, so Mockito sees no nested stubbing.
        var market = buildOwnedMarket();
        var entity = market.getPrimaryEntity();

        when(entity.getMarket())
            .thenReturn(market);

        var systemMock = buildUnreachableSystem(id);

        when(systemMock.getAllEntities())
            .thenReturn(List.of(entity));

        return systemMock;
    }

    private static StarSystemAPI buildUnreachableSystemWithDecivilisedPlanet(String id) {
        // Build the planet (and its own stubs) before the getPlanets() stubbing,
        // so Mockito does not see one stubbing nested inside another.
        var planet = buildDecivilisedPlanet();
        var systemMock = buildUnreachableSystem(id);

        when(systemMock.getPlanets())
            .thenReturn(List.of(planet));

        return systemMock;
    }

    private static PlanetAPI buildDecivilisedPlanet() {

        var conditionMock = mock(MarketConditionAPI.class);

        when(conditionMock.requiresSurveying())
            .thenReturn(false);

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getSurveyLevel())
            .thenReturn(MarketAPI.SurveyLevel.FULL);
        when(marketMock.getFirstCondition(Conditions.DECIVILIZED))
            .thenReturn(conditionMock);

        var planetMock = mock(PlanetAPI.class);

        when(planetMock.getMarket())
            .thenReturn(marketMock);

        return planetMock;
    }

    // A discovered, openly owned colony: faction set, not condition-only, its
    // entity no longer discoverable - what StarSystems counts as a known colony.
    private static MarketAPI buildOwnedMarket() {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(false);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn("hegemony");

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getSize())
            .thenReturn(5);
        when(marketMock.isPlanetConditionMarketOnly())
            .thenReturn(false);
        when(marketMock.isHidden())
            .thenReturn(false);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);

        return marketMock;
    }

    // An undiscovered colony the player has not found: hidden market on a still-
    // discoverable entity, so it fails the normal known-to-player gate and confers
    // presence only under the show-undiscovered-markets reveal.
    private static MarketAPI buildUndiscoveredColony() {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(true);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn("hegemony");

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getSize())
            .thenReturn(5);
        when(marketMock.isPlanetConditionMarketOnly())
            .thenReturn(false);
        when(marketMock.isHidden())
            .thenReturn(true);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);

        return marketMock;
    }
}
