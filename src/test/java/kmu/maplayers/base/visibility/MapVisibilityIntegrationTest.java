package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.DecivilisedPlanetFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the on-map rule: {@link MapVisibility} over the real
 * {@link StarSystems}, {@link Colonies}, {@link VisibleStars}, and
 * {@link DecivilisedMarkets}, assembled as {@link DrawnSystemPositions} assembles them - the
 * rule takes the answers, so the predicate that reads them is where the whole of it stands.
 * A reachable system appears; an unreachable one
 * appears once inhabited - a colony somebody lives on that the player knows of,
 * registered with the economy or not, or a revealed decivilised planet - and otherwise
 * stays off; and the fingerprint shifts when a system joins the on-map set. Exercised
 * together because the value is the composition: a mock of each rule would hide whether
 * they are wired in the right order.
 *
 * <p>The derelict cases are the composition's own, and pass through the whole of it: the
 * fog admits an abandoned station, habitation does not, and membership follows habitation
 * - so a stub anywhere in that chain would let a hulk go on dragging its system onto the
 * map while every read in isolation looked right.
 */
class MapVisibilityIntegrationTest {

    // The force override on, so the rule admits a system regardless of access or
    // inhabitation - the widening the pre-computed entry point reads off the value.
    private static final MapVisibilityRules FORCED_ONTO_MAP =
        new MapVisibilityRules(BASE_FOG, true);

    // The undiscovered-colony widening on, the other component of the same value: an
    // undiscovered colony counts as inhabitation.
    private static final MapVisibilityRules INCLUDING_UNDISCOVERED_MARKETS =
        new MapVisibilityRules(UNDER_THE_REVEAL, false);

    // The size every colony these cases stage carries. Nothing the visibility rule reads weighs a
    // colony, so a case varying this would vary nothing the rule can see.
    private static final int COLONY_SIZE = 5;

    // The faction behind every colony staged here. Which faction holds a colony reaches no answer
    // the rule gives, so one name serves them all and no case reads as being about whose it is.
    private static final String OWNING_FACTION = "hegemony";

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
        void shouldAppearOnMapIsFalseForAStarHiddenSystemHoldingOnlyAnAbandonedStation() {
            // Membership is where reading habitation is visible rather than merely tidy. The
            // vanilla map draws no star for this system, so inhabitation was its only route on,
            // and a hulk is not inhabitation - a system hidden by its own design stops being
            // dragged onto the map by a derelict the player has never been near.
            var system = buildReachableSystem("a");
            var sector = buildSectorWithHiddenStar(system);

            hangMarketsOnSystemEntities(system, buildAbandonedStation());

            assertThat(shouldAppearOnMapUnderNoReveal(sector, system))
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
                    MapVisibilityRules.BASE))
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
            // The composed entry, taken by every caller that read both facts for itself - the
            // sector scan, which holds the ruin flag anyway to salt the fingerprint, and a render
            // pass, which answers habitation off its own walk. The ruin is the arm no colony read
            // can carry, nobody owning a dead world, so this is the one place it could be dropped
            // and the only place the composition is stated.
            assertThat(MapVisibility.isInhabited(false, IS_REVEALED_DECIVILISED))
                .isTrue();
            assertThat(MapVisibility.isInhabited(false, false))
                .isFalse();
        }

        @Test
        void isInhabitedIsFalseForASystemHoldingOnlyAnAbandonedStation() {
            // Nobody has ever been aboard a derelict, so a system with one hulk in it and nothing
            // else is empty space with a wreck in it. The fog admits the hulk - it is un-hidden and
            // its entity is found - which is what makes this the habitation read's own case rather
            // than a fog case wearing a derelict's clothes.
            var system = buildUnreachableSystem("a");
            var sector = buildSectorWith(system);

            hangMarketsOnSystemEntities(system, buildAbandonedStation());

            assertThat(isInhabitedUnderNoReveal(sector, system))
                .isFalse();
        }

        @Test
        void isInhabitedIsTrueOnceAColonyStandsBesideTheAbandonedStation() {
            // The same system after somebody settles it. The hulk is staged unchanged, so what
            // turned the answer is the colony rather than anything the derelict stopped being.
            var system = buildUnreachableSystem("a");
            var sector = buildSectorWith(system, buildOwnedMarket());

            hangMarketsOnSystemEntities(system, buildAbandonedStation());

            assertThat(isInhabitedUnderNoReveal(sector, system))
                .isTrue();
        }

        @Test
        void isInhabitedIsTrueForAnUndiscoveredColonyWhenTheyAreIncluded() {
            // The widening folds the undiscovered colony in, so the system counts as
            // inhabited and earns a cell.
            var system = buildUnreachableSystem("a");

            assertThat(isInhabitedUnder(
                    buildSectorWith(system, buildUndiscoveredColony()),
                    system,
                    INCLUDING_UNDISCOVERED_MARKETS))
                .isTrue();
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

    // The whole rule over one system with no reveal applied, reached through the drawn-set
    // predicate. That is where the rule's two walks are now composed - the membership test
    // itself takes the answers rather than the sector - so a case asking the rule for real has
    // to ask it through the predicate that assembles them. Bound here once so the scenarios
    // below differ only in the fixture they stage.
    private static boolean shouldAppearOnMapUnderNoReveal(
            SectorAPI sector,
            StarSystemAPI system) {

        return DrawnSystemPositions
            .buildDrawnSystemPredicate(
                new SystemColoniesIndex(sector),
                VisibleStars.scan(sector),
                MapVisibilityRules.BASE)
            .test(system);
    }

    // The inhabitation read with no reveal applied, so the normal known-to-player gate stands.
    private static boolean isInhabitedUnderNoReveal(
            SectorAPI sector,
            StarSystemAPI system) {

        return isInhabitedUnder(sector, system, MapVisibilityRules.BASE);
    }

    // The inhabitation read over a sector, reached through the production composition rather
    // than restated here: the rule takes two answers, and a case stating for itself which
    // walks produce them would be asserting against its own copy of the rule.
    private static boolean isInhabitedUnder(
            SectorAPI sector,
            StarSystemAPI system,
            MapVisibilityRules visibilityRules) {

        return DrawnSystemPositions.isSystemInhabited(
            new SystemColoniesIndex(sector),
            system,
            visibilityRules);
    }

    // Wires a single-system sector whose economy returns the given markets for
    // that system - the read the inhabitation rule makes when judging colonies.
    private static SectorAPI buildSectorWith(StarSystemAPI system, MarketAPI... markets) {
        return buildSector(system, buildHyperspaceWithVisibleStarAnchorFor(system), markets);
    }

    // A reachable system whose only star anchor is hidden on the map, its economy listing nothing -
    // so the inhabited path cannot admit it either unless a case hangs something on its entities.
    private static SectorAPI buildSectorWithHiddenStar(StarSystemAPI system) {
        return buildSector(system, buildHyperspaceWithHiddenStarAnchorFor(system));
    }

    // A sector whose hyperspace holds no star anchor, so no system reads as
    // star-visible: the route onto the map is the nebula draw or inhabitation.
    private static SectorAPI buildSectorWithoutStarAnchors(StarSystemAPI system) {
        return buildSector(system, buildHyperspaceHolding());
    }

    // The single-system sector the three above are each one hyperspace of: an economy listing the
    // staged markets for that system, and whatever the vanilla map draws around it.
    //
    // One wiring rather than three, since what a case varies is the hyperspace and the markets and
    // never the sector itself - three copies of that stubbing is three chances for a case to be
    // posed against a sector its neighbours are not.
    private static SectorAPI buildSector(
            StarSystemAPI system,
            LocationAPI hyperspace,
            MarketAPI... markets) {

        // The economy finishes its own stubbing before the sector's opens, as the hyperspace the
        // caller passes already has, so Mockito sees no stubbing nested inside another.
        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(system))
            .thenReturn(List.of(markets));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(system));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspace);

        return sectorMock;
    }

    // A star anchor tagged hidden leading into the system, so the vanilla map
    // draws no star and the system reads as map-invisible.
    private static LocationAPI buildHyperspaceWithHiddenStarAnchorFor(StarSystemAPI system) {

        var anchorMock = buildStarAnchorTo(system);

        when(anchorMock.hasTag(Tags.STAR_HIDDEN_ON_MAP))
            .thenReturn(true);

        return buildHyperspaceHolding(anchorMock);
    }

    // A visible (untagged) star anchor leading into the system, so a reachable
    // system reads as map-visible. A system with no jump point stays off the map
    // regardless, so its anchor cannot wrongly admit it.
    private static LocationAPI buildHyperspaceWithVisibleStarAnchorFor(StarSystemAPI system) {
        return buildHyperspaceHolding(buildStarAnchorTo(system));
    }

    // A star anchor leading into the system, drawn on the map until a case tags it otherwise -
    // which is what makes the hidden variant above read as the one thing it changes.
    private static JumpPointAPI buildStarAnchorTo(StarSystemAPI system) {

        var anchorMock = mock(JumpPointAPI.class);

        when(anchorMock.isStarAnchor())
            .thenReturn(true);
        when(anchorMock.getDestinationStarSystem())
            .thenReturn(system);

        return anchorMock;
    }

    // Hyperspace holding the given anchors, and none is the case where no system reads as
    // star-visible at all.
    private static LocationAPI buildHyperspaceHolding(JumpPointAPI... anchors) {

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of(anchors));

        return hyperspaceMock;
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

        var systemMock = buildUnreachableSystem(id);

        hangMarketsOnSystemEntities(systemMock, buildOwnedMarket());

        return systemMock;
    }

    // Hangs the given markets on entities of the system without registering any with the economy -
    // the shape vanilla builds an unlisted colony in, and the only shape a derelict comes in.
    //
    // Each market and its entity finish their own stubbing before the system's opens, so Mockito
    // sees no stubbing nested inside another.
    private static void hangMarketsOnSystemEntities(
            StarSystemAPI system,
            MarketAPI... markets) {

        var entities = new ArrayList<SectorEntityToken>(markets.length);

        for (var market : markets) {
            var entityMock = market.getPrimaryEntity();

            when(entityMock.getMarket())
                .thenReturn(market);

            entities.add(entityMock);
        }
        when(system.getAllEntities())
            .thenReturn(entities);
    }

    private static StarSystemAPI buildUnreachableSystemWithDecivilisedPlanet(String id) {
        // Build the planet (and its own stubs) before the getPlanets() stubbing,
        // so Mockito does not see one stubbing nested inside another.
        var planet = DecivilisedPlanetFixtures.buildRevealedDecivilisedPlanet();
        var systemMock = buildUnreachableSystem(id);

        when(systemMock.getPlanets())
            .thenReturn(List.of(planet));

        return systemMock;
    }

    // A discovered, openly owned colony: faction set, not condition-only, its
    // entity no longer discoverable - what the colony read counts as a colony the player has
    // found.
    private static MarketAPI buildOwnedMarket() {
        return buildColonyOnEntity(OWNING_FACTION, false, false);
    }

    // A derelict hulk's market: an ordinary open market on a found entity, marked out by the
    // condition vanilla hangs on an abandoned station and held by nobody.
    //
    // Its owner and its absence from the economy are both part of the shape rather than details of
    // it. The condition on a market a faction holds - or on one the economy lists - is a station
    // somebody keeps, which inhabits its system like any colony, so a case staging one of these
    // through the economy would be posing an outpost and asserting a derelict's answers. Every
    // case here hangs it on a system entity instead, which is where vanilla builds one.
    private static MarketAPI buildAbandonedStation() {

        var marketMock = buildColonyOnEntity(Factions.NEUTRAL, false, false);

        when(marketMock.hasCondition(Conditions.ABANDONED_STATION))
            .thenReturn(true);

        return marketMock;
    }

    // An undiscovered colony the player has not found: hidden market on a still-
    // discoverable entity, so it fails the normal known-to-player gate and confers
    // presence only under the show-undiscovered-markets reveal.
    private static MarketAPI buildUndiscoveredColony() {
        return buildColonyOnEntity(OWNING_FACTION, true, true);
    }

    // The shape the named colonies above are points on, kept private so no case poses itself as a
    // pair of bare booleans - which axis a case varies is the whole of what it says.
    private static MarketAPI buildColonyOnEntity(
            String factionId,
            boolean isEntityDiscoverable,
            boolean isHidden) {

        // The entity and the faction each finish their own stubbing before the market's opens, so
        // Mockito sees no stubbing nested inside another.
        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(isEntityDiscoverable);

        var factionMock = buildFaction(factionId);
        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getSize())
            .thenReturn(COLONY_SIZE);
        when(marketMock.isPlanetConditionMarketOnly())
            .thenReturn(false);
        when(marketMock.isHidden())
            .thenReturn(isHidden);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);

        return marketMock;
    }

    // The faction holding one of these markets. Whether it is the neutral one is answered off the
    // faction, as the engine answers it, rather than left false: the colony kind read parts an
    // unowned hulk from a station somebody keeps on exactly this question, so a fixture that had
    // neutral deny being neutral would pose every derelict here as a manned outpost.
    private static FactionAPI buildFaction(String factionId) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn(factionId);
        when(factionMock.isNeutralFaction())
            .thenReturn(Factions.NEUTRAL.equals(factionId));

        return factionMock;
    }
}
