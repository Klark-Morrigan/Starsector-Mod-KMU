package kmu.politicalmap.domain.visibility;

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

import kmu.politicalmap.domain.politics.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the on-map rule: {@link PoliticalMapVisibility}
 * composing the real {@link SystemAccess}, {@link SectorPolitics}, and
 * {@link DecivilisedPresence}. A reachable system appears; an unreachable one
 * appears once inhabited - a discovered colony or a revealed decivilised planet -
 * and otherwise stays off; and the fingerprint shifts when a system joins the
 * on-map set. Exercised together because the value is the composition: a mock of
 * each rule would hide whether they are wired in the right order.
 */
class PoliticalMapVisibilityIntegrationTest {

    @Nested
    class ShouldAppearOnMap {

        @Test
        void shouldAppearOnMapIsTrueForReachableSystem() {
            var system = reachableSystem("a");

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(sectorWith(system), system))
                    .isTrue();
        }

        @Test
        void shouldAppearOnMapIsTrueForUnreachableSystemHoldingAColony() {
            // No jump point, so unreachable by the access rule, but a discovered
            // colony makes it inhabited and admits it to the map.
            var system = unreachableSystem("a");

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(
                    sectorWith(system, ownedMarket()), system)).isTrue();
        }

        @Test
        void shouldAppearOnMapIsTrueForUnreachableSystemWithARevealedDecivilisedPlanet() {
            var system = unreachableSystemWithDecivilisedPlanet("a");

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(sectorWith(system), system))
                    .isTrue();
        }

        @Test
        void shouldAppearOnMapIsFalseForUnreachableUninhabitedSystem() {
            var system = unreachableSystem("a");

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(sectorWith(system), system))
                    .isFalse();
        }

        @Test
        void shouldAppearOnMapIsFalseForReachableSystemWhoseStarIsHiddenOnMap() {
            // Reachable by a jump point, but the vanilla map hides its star (an
            // abyssal rogue object), so reachability alone does not admit it.
            var system = reachableSystem("a");

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(sectorWithHiddenStar(system), system))
                    .isFalse();
        }

        @Test
        void shouldAppearOnMapIsTrueForReachableNebulaWithNoVisibleStar() {
            // A nebula has no star anchor, so it is never in the visible-star
            // index; the vanilla map draws it as a cloud, so the political map
            // must admit it on the access path without an inhabitation.
            var system = reachableNebula("a");

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(sectorWithoutStarAnchors(system), system))
                    .isTrue();
        }

        @Test
        void shouldAppearOnMapIsTrueForUninhabitedSystemWhenForcedOntoMap() {
            // The force-all-systems dev reveal admits a system the normal rule omits -
            // unreachable and uninhabited - so the full partition can be inspected.
            var system = unreachableSystem("a");
            var visibleStars = MapVisibleStars.scan(sectorWithoutStarAnchors(system));

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(system, visibleStars, false, true))
                    .isTrue();
        }

        @Test
        void shouldAppearOnMapIsFalseForUninhabitedSystemWhenNotForced() {
            // Without the force override the same unreachable, uninhabited system
            // stays off, so the reveal is what admits it, not the fixture.
            var system = unreachableSystem("a");
            var visibleStars = MapVisibleStars.scan(sectorWithoutStarAnchors(system));

            assertThat(PoliticalMapVisibility.shouldAppearOnMap(system, visibleStars, false, false))
                    .isFalse();
        }
    }

    @Nested
    class IsInhabited {

        @Test
        void isInhabitedIsTrueWhenADiscoveredColonyExists() {
            var system = unreachableSystem("a");

            assertThat(PoliticalMapVisibility.isInhabited(sectorWith(system, ownedMarket()), system))
                    .isTrue();
        }

        @Test
        void isInhabitedIsTrueWhenARevealedDecivilisedPlanetExists() {
            var system = unreachableSystemWithDecivilisedPlanet("a");

            assertThat(PoliticalMapVisibility.isInhabited(sectorWith(system), system)).isTrue();
        }

        @Test
        void isInhabitedIsFalseWhenNeitherColonyNorRuinExists() {
            var system = unreachableSystem("a");

            assertThat(PoliticalMapVisibility.isInhabited(sectorWith(system), system)).isFalse();
        }

        @Test
        void isInhabitedIsFalseForAnUndiscoveredColonyByDefault() {
            // A concealed colony fails the normal known-to-player gate, so the system
            // reads as uninhabited until the reveal is on.
            var system = unreachableSystem("a");

            assertThat(PoliticalMapVisibility.isInhabited(
                    sectorWith(system, undiscoveredColony()), system)).isFalse();
        }

        @Test
        void isInhabitedIsTrueForAnUndiscoveredColonyWhenShowingAllFactions() {
            // The show-all-factions dev reveal folds the concealed colony in, so the
            // system counts as inhabited and earns a cell.
            var system = unreachableSystem("a");

            assertThat(PoliticalMapVisibility.isInhabited(
                    sectorWith(system, undiscoveredColony()), system, true)).isTrue();
        }
    }

    @Nested
    class ComputeVisibilityContribution {

        @Test
        void visibilityContributionDiffersBetweenSystems() {
            // Distinct ids must land distinct contributions so two systems do not
            // cancel when summed into the fingerprint.
            assertThat(PoliticalMapVisibility.computeVisibilityContribution("a", false))
                    .isNotEqualTo(PoliticalMapVisibility.computeVisibilityContribution("b", false));
        }

        @Test
        void visibilityContributionShiftsWhenASystemBecomesDecivilised() {
            // A live-to-dead flip on the same system - its draw class changing while
            // it stays on the map - must move its contribution via the deciv salt.
            assertThat(PoliticalMapVisibility.computeVisibilityContribution("a", true))
                    .isNotEqualTo(PoliticalMapVisibility.computeVisibilityContribution("a", false));
        }

        @Test
        void visibilityContributionIsNonZeroForAnIdThatHashesToZero() {
            // The avalanche has a fixed point at 0, so an unseeded 0-hash id would
            // contribute 0 and be invisible to the summed fingerprint - the system
            // could enter or leave the map without moving it. The empty string is
            // the canonical 0-hash id; the seed spreads it to a non-zero value.
            var idHashingToZero = "";
            assertThat(idHashingToZero.hashCode()).isZero();
            assertThat(PoliticalMapVisibility.computeVisibilityContribution(idHashingToZero, false))
                    .isNotZero();
        }

        @Test
        void summedFingerprintMovesWhenAZeroHashSystemJoinsTheDrawnSet() {
            // The fingerprint is a sum of contributions, so a system joining the
            // drawn set must change it - including a 0-hash id, whose contribution
            // has to be non-zero for its arrival to register in the sum.
            assertThat("".hashCode()).isZero();
            var before = PoliticalMapVisibility.computeVisibilityContribution("a", false);
            var after = before + PoliticalMapVisibility.computeVisibilityContribution("", false);
            assertThat(after).isNotEqualTo(before);
        }

        @Test
        void visibilityContributionShiftsWhenAZeroHashSystemBecomesDecivilised() {
            // A draw-class flip must move the contribution even for a 0-hash id, so a
            // live-to-dead change on such a system still moves the summed fingerprint
            // rather than reading identically live and dead.
            assertThat("".hashCode()).isZero();
            assertThat(PoliticalMapVisibility.computeVisibilityContribution("", true))
                    .isNotEqualTo(PoliticalMapVisibility.computeVisibilityContribution("", false));
        }
    }

    // Wires a single-system sector whose economy returns the given markets for
    // that system - the read SectorPolitics makes when judging faction presence.
    private static SectorAPI sectorWith(StarSystemAPI system, MarketAPI... markets) {
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(system)).thenReturn(List.of(markets));
        // The hyperspace mock is built before the getHyperspace() stubbing so its
        // own stubbing is not nested inside this one.
        var hyperspaceMock = hyperspaceWithVisibleStarAnchorFor(system);
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(system));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    // A reachable system whose only star anchor is hidden on the map, with an
    // empty economy so the inhabited path cannot admit it either.
    private static SectorAPI sectorWithHiddenStar(StarSystemAPI system) {
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(system)).thenReturn(List.of());
        var hyperspaceMock = hyperspaceWithHiddenStarAnchorFor(system);
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(system));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    // A star anchor tagged hidden leading into the system, so the vanilla map
    // draws no star and the system reads as map-invisible.
    private static LocationAPI hyperspaceWithHiddenStarAnchorFor(StarSystemAPI system) {
        var anchorMock = mock(JumpPointAPI.class);
        when(anchorMock.isStarAnchor()).thenReturn(true);
        when(anchorMock.hasTag(Tags.STAR_HIDDEN_ON_MAP)).thenReturn(true);
        when(anchorMock.getDestinationStarSystem()).thenReturn(system);
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of(anchorMock));
        return hyperspaceMock;
    }

    // A visible (untagged) star anchor leading into the system, so a reachable
    // system reads as map-visible. A system with no jump point stays off the map
    // regardless, so its anchor cannot wrongly admit it.
    private static LocationAPI hyperspaceWithVisibleStarAnchorFor(StarSystemAPI system) {
        var anchorMock = mock(JumpPointAPI.class);
        when(anchorMock.isStarAnchor()).thenReturn(true);
        when(anchorMock.getDestinationStarSystem()).thenReturn(system);
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of(anchorMock));
        return hyperspaceMock;
    }

    // A sector whose hyperspace holds no star anchor, so no system reads as
    // star-visible: the route onto the map is the nebula draw or inhabitation.
    private static SectorAPI sectorWithoutStarAnchors(StarSystemAPI system) {
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(system)).thenReturn(List.of());
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of());
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(system));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    private static StarSystemAPI reachableSystem(String id) {
        // Wired into hyperspace by a jump point and its star drawn on the map, so
        // the access rule admits it.
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(1f, 1f));
        when(systemMock.getJumpPoints()).thenReturn(List.of(mock(SectorEntityToken.class)));
        return systemMock;
    }

    private static StarSystemAPI reachableNebula(String id) {
        // Reachable, but drawn on the map as a nebula cloud rather than a star, so
        // it never appears in the visible-star index - its draw is the nebula flag.
        var systemMock = reachableSystem(id);
        when(systemMock.isNebula()).thenReturn(true);
        return systemMock;
    }

    private static StarSystemAPI unreachableSystem(String id) {
        // No jump point and not cut off - reachable only by transverse jump, so
        // the access rule rejects it; inhabitation is its only route onto the map.
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(2f, 2f));
        when(systemMock.getJumpPoints()).thenReturn(List.of());
        return systemMock;
    }

    private static StarSystemAPI unreachableSystemWithDecivilisedPlanet(String id) {
        // Build the planet (and its own stubs) before the getPlanets() stubbing,
        // so Mockito does not see one stubbing nested inside another.
        var planet = decivilisedPlanet();
        var systemMock = unreachableSystem(id);
        when(systemMock.getPlanets()).thenReturn(List.of(planet));
        return systemMock;
    }

    private static PlanetAPI decivilisedPlanet() {
        var conditionMock = mock(MarketConditionAPI.class);
        when(conditionMock.requiresSurveying()).thenReturn(false);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getSurveyLevel()).thenReturn(MarketAPI.SurveyLevel.FULL);
        when(marketMock.getFirstCondition(Conditions.DECIVILIZED)).thenReturn(conditionMock);
        var planetMock = mock(PlanetAPI.class);
        when(planetMock.getMarket()).thenReturn(marketMock);
        return planetMock;
    }

    // A discovered, openly owned colony: faction set, not condition-only, its
    // entity no longer discoverable - what SectorPolitics counts as presence.
    private static MarketAPI ownedMarket() {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(false);
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn("hegemony");
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(factionMock);
        when(marketMock.getSize()).thenReturn(5);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(false);
        when(marketMock.isHidden()).thenReturn(false);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }

    // A concealed colony the player has not found: hidden market on a still-
    // discoverable entity, so it fails the normal known-to-player gate and confers
    // presence only under the show-all-factions reveal.
    private static MarketAPI undiscoveredColony() {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(true);
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn("hegemony");
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(factionMock);
        when(marketMock.getSize()).thenReturn(5);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(false);
        when(marketMock.isHidden()).thenReturn(true);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }
}
