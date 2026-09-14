package kmu.maplayers;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dead world every map surface reads as a system somebody once lived in: a planet whose market
 * carries vanilla's decivilised condition, surveyed far enough that the player can see it does.
 *
 * <p>Shared because six suites across three layers stage this same shape, and it is the one fixture
 * none of them can express through their own market vocabulary. A ruin is the condition-only shell a
 * colony leaves behind - stripped of its owner, its industries and its economy listing - so it is
 * not a variant of the colony builders each suite keeps, and every suite that wanted one wrote the
 * same stubs out again. Written out per suite they drift: two of the six stated the survey rule and
 * four left it to a mock default, so each copy went on agreeing with itself while the suites
 * disagreed about what a revealed ruin is.
 *
 * <p>It sits at {@code kmu.maplayers} rather than beside any layer's fixtures because the framework
 * suites need it too, and {@code maplayers.base} may not reach into the political map - see the
 * layering gate in {@code build.gradle}. This is above both, so both may take it.
 *
 * <p>Revealed rather than merely present, which is the whole of what makes it visible. Vanilla
 * gates a condition on the survey level of the market carrying it, so a world nobody has surveyed
 * reads as no ruin at all; the pair of stubs below is what lifts that gate.
 *
 * <p>The planet is wired as the market's own entity, and the market back onto it, because a ruin
 * reaches a map surface through the colony walk of its system's entities. Wired one way only, the
 * walk finds nothing and a case posing a ruined system would really be posing an empty one - and
 * would pass whichever answer the surface gave.
 */
public final class DecivilisedPlanetFixtures {

    private DecivilisedPlanetFixtures() {
    }

    /**
     * A surveyed planet carrying a revealed decivilised condition - the ruin of a colony the player
     * has already seen die.
     *
     * <p>Its market is the condition-only shell vanilla leaves behind, handed to the neutral
     * faction, which the decivilised condition is what parts from every bare rock's placeholder.
     *
     * @return the planet mock, wired to carry that market and to be carried by it
     */
    public static PlanetAPI buildRevealedDecivilisedPlanet() {
        return buildDecivilisedPlanet(MarketAPI.SurveyLevel.FULL);
    }

    /**
     * The same ruin on a world nobody has looked at closely enough to read: the condition is there
     * and the player has no way of knowing it.
     *
     * <p>Its planet is found all the same. Discovery and survey are independent axes, and this is
     * the world where they part - which is what a case about the survey reveal turns on.
     *
     * @return the planet mock, wired as its revealed counterpart is
     */
    public static PlanetAPI buildUnsurveyedDecivilisedPlanet() {
        return buildDecivilisedPlanet(MarketAPI.SurveyLevel.NONE);
    }

    /**
     * Hangs a revealed ruin on a system already built, for a suite whose system came from a sector
     * fixture rather than from a builder of its own.
     *
     * <p>The ruin is placed among the system's entities as well as its planets, that walk being how
     * a colony set reaches an unlisted market at all.
     *
     * @param system the system the ruin stands in; its planets and entities are replaced by the one
     *               ruin
     */
    public static void placeRevealedDecivilisedPlanetIn(StarSystemAPI system) {
        placePlanetIn(system, buildRevealedDecivilisedPlanet());
    }

    /**
     * Hangs a ruin nobody has surveyed on a system already built, for a case about the survey
     * reveal.
     *
     * @param system the system the ruin stands in; its planets and entities are replaced by it
     */
    public static void placeUnsurveyedDecivilisedPlanetIn(StarSystemAPI system) {
        placePlanetIn(system, buildUnsurveyedDecivilisedPlanet());
    }

    // The ruin at a stated survey level, which is the one axis the two builders above differ on.
    private static PlanetAPI buildDecivilisedPlanet(MarketAPI.SurveyLevel surveyLevel) {

        // The condition, the faction and the planet finish their own stubbing before the market's
        // opens, so Mockito does not see one stubbing nested inside another.
        var conditionMock = mock(MarketConditionAPI.class);

        when(conditionMock.requiresSurveying())
            .thenReturn(false);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn(Factions.NEUTRAL);
        when(factionMock.isNeutralFaction())
            .thenReturn(true);

        var planetMock = mock(PlanetAPI.class);
        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getFactionId())
            .thenReturn(Factions.NEUTRAL);
        when(marketMock.isPlanetConditionMarketOnly())
            .thenReturn(true);
        when(marketMock.hasCondition(Conditions.DECIVILIZED))
            .thenReturn(true);
        when(marketMock.getSurveyLevel())
            .thenReturn(surveyLevel);
        when(marketMock.getFirstCondition(Conditions.DECIVILIZED))
            .thenReturn(conditionMock);
        when(marketMock.getPrimaryEntity())
            .thenReturn(planetMock);
        when(planetMock.getMarket())
            .thenReturn(marketMock);

        return planetMock;
    }

    // Stands one ruin in a system, on both walks that could reach it. The entity walk is how a
    // colony set finds an unlisted market at all; the planet walk is what a suite reading the
    // system's worlds sees.
    //
    // The market is pointed back at the system as well, because a reader asking whether anybody
    // has seen this colony reads the place it stands in and compares it against the observation.
    // Left unstubbed the ruin stands in no system at all - hyperspace, where such a reader has
    // nothing to compare and so answers seen - and a case posing a ruin nobody has found would
    // really be posing one everybody has.
    private static void placePlanetIn(StarSystemAPI system, PlanetAPI planet) {

        var market = planet.getMarket();

        when(system.getPlanets())
            .thenReturn(List.of(planet));
        when(system.getAllEntities())
            .thenReturn(List.of(planet));
        when(market.getContainingLocation())
            .thenReturn(system);
    }
}
