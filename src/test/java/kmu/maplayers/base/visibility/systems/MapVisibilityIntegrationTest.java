package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.systems.SystemAccessRoutes;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.DecivilisedPlanetFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildHyperspaceHolding;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildStarAnchorTo;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildStarAnchoredSectorOf;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildUnroutedSectorOf;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.listMarketsIn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the on-map rule: {@link MapVisibility} over the real
 * {@link StarSystems}, {@link Colonies}, {@link VisibleStars}, and
 * {@link DecivilisedMarkets}, assembled as {@link MapVisibilityPass} assembles them - the rule
 * takes the answers, so the pass that reads them is where the whole of it stands.
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

    // The key of a system the sector states nothing about - no id, no centre, no anchor. Every arm
    // hashes to zero, which is the avalanche's own fixed point, so this is the key the fingerprint
    // seed exists to lift off zero.
    private static final SystemKey KEY_HASHING_TO_ZERO = new SystemKey("", "", "");

    // The faction behind every colony staged here. Which faction holds a colony reaches no answer
    // the rule gives, so one name serves them all and no case reads as being about whose it is.
    private static final String OWNING_FACTION = "hegemony";

    // The inhabitation the pre-computed entry point is handed when the caller has already
    // decided the system holds nothing, so admission rests on access or the force override.
    private static final boolean UNINHABITED = false;

    // The installed routes are one set per running game rather than per holder, so a case staging
    // one has to empty them around itself - and every case that stages none is then posed on an
    // install that has none, whatever order the suite runs in.
    @BeforeEach
    void setUp() {
        SystemAccessRoutes.clearRoutes();
    }

    @AfterEach
    void tearDown() {
        SystemAccessRoutes.clearRoutes();
    }

    @Nested
    class IsDrawn {

        @Test
        void isDrawnIsTrueForReachableSystem() {

            var system = buildReachableSystem("a");

            assertThat(isDrawnUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void isDrawnIsTrueForUnreachableSystemHoldingAColony() {
            // No jump point, so unreachable by the access rule, but a discovered
            // colony makes it inhabited and admits it to the map.
            var system = buildUnreachableSystem("a");

            assertThat(isDrawnUnderNoReveal(
                    buildSectorWith(system, buildOwnedMarket()),
                    system))
                .isTrue();
        }

        @Test
        void isDrawnIsTrueForUnreachableSystemWithARevealedDecivilisedPlanet() {

            var system = buildUnreachableSystemWithDecivilisedPlanet("a");

            assertThat(isDrawnUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void isDrawnIsFalseForUnreachableUninhabitedSystem() {

            var system = buildUnreachableSystem("a");

            assertThat(isDrawnUnderNoReveal(buildSectorWith(system), system))
                .isFalse();
        }

        @Test
        void isDrawnIsFalseForReachableSystemWhoseStarIsHiddenOnMap() {
            // Reachable by a jump point, but the vanilla map hides its star (an
            // abyssal rogue object), so reachability alone does not admit it.
            var system = buildReachableSystem("a");

            assertThat(isDrawnUnderNoReveal(buildSectorWithHiddenStar(system), system))
                .isFalse();
        }

        @Test
        void isDrawnIsFalseForAStarHiddenSystemHoldingOnlyAnAbandonedStation() {
            // Membership is where reading habitation is visible rather than merely tidy. The
            // vanilla map draws no star for this system, so inhabitation was its only route on,
            // and a hulk is not inhabitation - a system hidden by its own design stops being
            // dragged onto the map by a derelict the player has never been near.
            var system = buildReachableSystem("a");
            var sector = buildSectorWithHiddenStar(system);

            hangMarketsOnSystemEntities(system, buildAbandonedStation());

            assertThat(isDrawnUnderNoReveal(sector, system))
                .isFalse();
        }

        @Test
        void isDrawnIsTrueForASystemWithNoVisibleStarAnInstalledRouteReaches() {
            // The shape a mod-made destination arrives in: no jump point, no star the vanilla map
            // draws, and nobody living there - a mod carries fleets in by an entity of its own and
            // marks the spot itself. Neither vanilla read can see any of that, so the route is the
            // only thing that can admit it, and it has to admit it without a drawn star.
            var system = buildUnreachableSystem("a");

            SystemAccessRoutes.registerRoute("a mod", askedSystem -> askedSystem == system);

            assertThat(isDrawnUnderNoReveal(buildSectorWithoutStarAnchors(system), system))
                .isTrue();
        }

        @Test
        void isDrawnIsFalseForASystemWithNoVisibleStarNoInstalledRouteReaches() {
            // A route is asked per system, so one installed for somewhere else leaves this system
            // exactly where it found it - the widening above is the mod's answer about this place,
            // not a standing yes for every place on an install carrying the mod.
            var system = buildUnreachableSystem("a");

            SystemAccessRoutes.registerRoute("a mod", askedSystem -> false);

            assertThat(isDrawnUnderNoReveal(buildSectorWithoutStarAnchors(system), system))
                .isFalse();
        }

        @Test
        void isDrawnIsTrueForReachableNebulaWithNoVisibleStar() {
            // A nebula has no star anchor, so it is never in the visible-star
            // index; the vanilla map draws it as a cloud, so the rule must admit it
            // on the access path without an inhabitation.
            var system = buildReachableNebula("a");

            assertThat(isDrawnUnderNoReveal(buildSectorWithoutStarAnchors(system), system))
                .isTrue();
        }

        @Test
        void isDrawnIsTrueForUninhabitedSystemWhenForcedOntoMap() {
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
        void isDrawnIsFalseForUninhabitedSystemWhenNotForced() {
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
    class IsSystemInhabited {

        @Test
        void isSystemInhabitedIsTrueWhenADiscoveredColonyExists() {

            var system = buildUnreachableSystem("a");

            assertThat(isSystemInhabitedUnderNoReveal(buildSectorWith(system, buildOwnedMarket()), system))
                .isTrue();
        }

        @Test
        void isSystemInhabitedIsTrueWhenARevealedDecivilisedPlanetExists() {

            var system = buildUnreachableSystemWithDecivilisedPlanet("a");

            assertThat(isSystemInhabitedUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void isSystemInhabitedIsFalseWhenNeitherColonyNorRuinExists() {

            var system = buildUnreachableSystem("a");

            assertThat(isSystemInhabitedUnderNoReveal(buildSectorWith(system), system))
                .isFalse();
        }

        @Test
        void isSystemInhabitedIsFalseForAnUndiscoveredColonyByDefault() {
            // An undiscovered colony fails the normal known-to-player gate, so the system
            // reads as uninhabited until the reveal is on.
            var system = buildUnreachableSystem("a");

            assertThat(isSystemInhabitedUnderNoReveal(
                    buildSectorWith(system, buildUndiscoveredColony()),
                    system))
                .isFalse();
        }

        @Test
        void isSystemInhabitedIsTrueForAColonyTheEconomyDoesNotList() {
            // Galatia Academy's shape: a real colony on a real station vanilla never registers.
            // Reading the economy's listing alone would leave such a system classified as empty
            // backdrop while every box drawn over it names the faction holding it.
            var system = buildUnreachableSystemHoldingUnlistedColony("a");

            assertThat(isSystemInhabitedUnderNoReveal(buildSectorWith(system), system))
                .isTrue();
        }

        @Test
        void isSystemInhabitedIsFalseForASystemHoldingOnlyAnAbandonedStation() {
            // Nobody has ever been aboard a derelict, so a system with one hulk in it and nothing
            // else is empty space with a wreck in it. The fog admits the hulk - it is un-hidden and
            // its entity is found - which is what makes this the habitation read's own case rather
            // than a fog case wearing a derelict's clothes.
            var system = buildUnreachableSystem("a");
            var sector = buildSectorWith(system);

            hangMarketsOnSystemEntities(system, buildAbandonedStation());

            assertThat(isSystemInhabitedUnderNoReveal(sector, system))
                .isFalse();
        }

        @Test
        void isSystemInhabitedIsTrueOnceAColonyStandsBesideTheAbandonedStation() {
            // The same system after somebody settles it. The hulk is staged unchanged, so what
            // turned the answer is the colony rather than anything the derelict stopped being.
            var system = buildUnreachableSystem("a");
            var sector = buildSectorWith(system, buildOwnedMarket());

            hangMarketsOnSystemEntities(system, buildAbandonedStation());

            assertThat(isSystemInhabitedUnderNoReveal(sector, system))
                .isTrue();
        }

        @Test
        void isSystemInhabitedIsTrueForAnUndiscoveredColonyWhenTheyAreIncluded() {
            // The widening folds the undiscovered colony in, so the system counts as
            // inhabited and earns a cell.
            var system = buildUnreachableSystem("a");

            assertThat(isSystemInhabitedUnder(
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
            assertThat(MapVisibility.computeVisibilityContribution(buildKeyOfIdAlone("a"), false))
                .isNotEqualTo(
                    MapVisibility.computeVisibilityContribution(buildKeyOfIdAlone("b"), false));
        }

        @Test
        void visibilityContributionDiffersBetweenTwoSystemsSharingAnId() {
            // The collision the key exists for: a sector holds two systems answering to one id,
            // told apart only by the entities they are built around. Keyed by id both would
            // contribute the same value, so one entering the drawn set as the other left would
            // leave the fingerprint standing still.
            var firstOfThePair = new SystemKey("a", "centre-1", "anchor-1");
            var secondOfThePair = new SystemKey("a", "centre-2", "anchor-2");

            assertThat(MapVisibility.computeVisibilityContribution(firstOfThePair, false))
                .isNotEqualTo(
                    MapVisibility.computeVisibilityContribution(secondOfThePair, false));
        }

        @Test
        void visibilityContributionDiffersForAPairWhoseArmsShiftEachOtherBack() {
            // The pair a linear fold cannot tell apart, in the shape a live sector holds it: one
            // id, procgen centre names one character apart, and short engine-minted anchor ids.
            // String hashes are linear in their characters, so the centres' hashes differ by 1 and
            // the anchors' by exactly 31 the other way - a fold weighting the centre arm by 31
            // would move one arm by what the other moves back, and hand both systems one value.
            var centredOnTheThirdStar = new SystemKey("deep space", "deep_space_star_3", "8c3");
            var centredOnTheFourthStar = new SystemKey("deep space", "deep_space_star_4", "8b3");

            assertThat("8c3".hashCode() - "8b3".hashCode())
                .isEqualTo(31 * ("deep_space_star_4".hashCode() - "deep_space_star_3".hashCode()));
            assertThat(MapVisibility.computeVisibilityContribution(centredOnTheThirdStar, false))
                .isNotEqualTo(
                    MapVisibility.computeVisibilityContribution(centredOnTheFourthStar, false));
        }

        @Test
        void visibilityContributionDiffersWhenOneIdMovesBetweenTheArms() {
            // The arms are folded by position, so an entity id standing as one system's centre
            // and another's anchor tells the two apart. Folded without position they would read
            // as one system and the pair would share a contribution.
            var centredOnTheEntity = new SystemKey("a", "shared-entity", "");
            var anchoredToIt = new SystemKey("a", "", "shared-entity");

            assertThat(MapVisibility.computeVisibilityContribution(centredOnTheEntity, false))
                .isNotEqualTo(
                    MapVisibility.computeVisibilityContribution(anchoredToIt, false));
        }

        @Test
        void visibilityContributionShiftsWhenASystemBecomesDecivilised() {
            // A live-to-dead flip on the same system - its draw class changing while
            // it stays on the map - must move its contribution via the deciv salt.
            assertThat(MapVisibility.computeVisibilityContribution(buildKeyOfIdAlone("a"), true))
                .isNotEqualTo(
                    MapVisibility.computeVisibilityContribution(buildKeyOfIdAlone("a"), false));
        }

        @Test
        void visibilityContributionIsNonZeroForAKeyThatHashesToZero() {
            // The avalanche has a fixed point at 0, so an unseeded 0-hash key would
            // contribute 0 and be invisible to the summed fingerprint - the system
            // could enter or leave the map without moving it. The key stating no arm
            // at all is the canonical 0-hash key; the seed spreads it to a non-zero value.
            assertThat("".hashCode())
                .isZero();
            assertThat(MapVisibility.computeVisibilityContribution(KEY_HASHING_TO_ZERO, false))
                .isNotZero();
        }

        @Test
        void summedFingerprintMovesWhenAZeroHashSystemJoinsTheDrawnSet() {
            // The fingerprint is a sum of contributions, so a system joining the
            // drawn set must change it - including a 0-hash key, whose contribution
            // has to be non-zero for its arrival to register in the sum.
            assertThat("".hashCode())
                .isZero();

            var before = MapVisibility.computeVisibilityContribution(buildKeyOfIdAlone("a"), false);
            var after = before
                + MapVisibility.computeVisibilityContribution(KEY_HASHING_TO_ZERO, false);

            assertThat(after)
                .isNotEqualTo(before);
        }

        @Test
        void visibilityContributionShiftsWhenAZeroHashSystemBecomesDecivilised() {
            // A draw-class flip must move the contribution even for a 0-hash key, so a
            // live-to-dead change on such a system still moves the summed fingerprint
            // rather than reading identically live and dead.
            assertThat("".hashCode())
                .isZero();
            assertThat(MapVisibility.computeVisibilityContribution(KEY_HASHING_TO_ZERO, true))
                .isNotEqualTo(
                    MapVisibility.computeVisibilityContribution(KEY_HASHING_TO_ZERO, false));
        }
    }

    // The whole rule over one system with no reveal applied, asked of a pass. That is where the
    // rule's two walks are composed - the membership test itself takes the answers rather than
    // the sector - so a case asking the rule for real has to ask it of the thing that assembles
    // them. Bound here once so the scenarios below differ only in the fixture they stage.
    private static boolean isDrawnUnderNoReveal(
            SectorAPI sector,
            StarSystemAPI system) {

        return MapVisibilityPass
            .over(sector, MapVisibilityRules.BASE)
            .isDrawn(system);
    }

    // The inhabitation read with no reveal applied, so the normal known-to-player gate stands.
    private static boolean isSystemInhabitedUnderNoReveal(
            SectorAPI sector,
            StarSystemAPI system) {

        return isSystemInhabitedUnder(sector, system, MapVisibilityRules.BASE);
    }

    // The inhabitation read over a sector, asked of a pass rather than restated here: the rule
    // takes two answers, and a case stating for itself which walks produce them would be
    // asserting against its own copy of the rule.
    private static boolean isSystemInhabitedUnder(
            SectorAPI sector,
            StarSystemAPI system,
            MapVisibilityRules visibilityRules) {

        return MapVisibilityPass
            .over(sector, visibilityRules)
            .isSystemInhabited(system);
    }

    // The key of a system carrying neither a centre nor an anchor, so the id is the whole of what
    // tells it apart. Minted rather than read off a staged system: a case about the fold states
    // the arms it means, and one taking them from the read under test would expect nothing.
    private static SystemKey buildKeyOfIdAlone(String systemId) {
        return new SystemKey(systemId, "", "");
    }

    // Wires a single-system sector whose economy returns the given markets for
    // that system - the read the inhabitation rule makes when judging colonies.
    private static SectorAPI buildSectorWith(StarSystemAPI system, MarketAPI... markets) {

        var sector = buildStarAnchoredSectorOf(system);

        listMarketsIn(sector, system, markets);

        return sector;
    }

    // A reachable system whose only star anchor is hidden on the map, its economy listing nothing -
    // so the inhabited path cannot admit it either unless a case hangs something on its entities.
    private static SectorAPI buildSectorWithHiddenStar(StarSystemAPI system) {
        return MapSectorFixture.buildSectorOf(buildHyperspaceWithHiddenStarAnchorFor(system), system);
    }

    // A sector whose hyperspace holds no star anchor, so no system reads as
    // star-visible: the route onto the map is the nebula draw or inhabitation.
    private static SectorAPI buildSectorWithoutStarAnchors(StarSystemAPI system) {
        return buildUnroutedSectorOf(system);
    }

    // A star anchor tagged hidden leading into the system, so the vanilla map
    // draws no star and the system reads as map-invisible.
    private static LocationAPI buildHyperspaceWithHiddenStarAnchorFor(StarSystemAPI system) {

        var anchorMock = buildStarAnchorTo(system);

        when(anchorMock.hasTag(Tags.STAR_HIDDEN_ON_MAP))
            .thenReturn(true);

        return buildHyperspaceHolding(anchorMock);
    }

    private static StarSystemAPI buildReachableSystem(String id) {
        // Wired into hyperspace by a jump point and its star drawn on the map, so
        // the access rule admits it.
        var systemMock = StarSystemFixture.buildSystemAt(id, 1f, 1f);

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

    // A system whose one market is the ruin of a colony. Placed among the system's entities as
    // well as its planets, that walk being how a colony set reaches an unlisted market at all -
    // wired to the planets alone, this would pose an empty system while reading as a ruined one.
    private static StarSystemAPI buildUnreachableSystemWithDecivilisedPlanet(String id) {

        var systemMock = buildUnreachableSystem(id);

        DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(systemMock);

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
