package kmu.maplayers;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dead world every map surface reads as a system somebody once lived in: a planet whose market
 * carries vanilla's decivilised condition, surveyed far enough that the player can see it does.
 *
 * <p>Shared because six suites across three layers stage this same shape, and it is the one fixture
 * none of them can express through their own market vocabulary. A ruin is not an owned colony - no
 * faction holds it and no colony read reports it - so it is not a variant of the colony builders
 * each suite keeps, and every suite that wanted one wrote the same three stubs out again. Written
 * out per suite they drift: two of the six stated the survey rule and four left it to a mock
 * default, so each copy went on agreeing with itself while the suites disagreed about what a
 * revealed ruin is.
 *
 * <p>It sits at {@code kmu.maplayers} rather than beside any layer's fixtures because the framework
 * suites need it too, and {@code maplayers.base} may not reach into the political map - see the
 * layering gate in {@code build.gradle}. This is above both, so both may take it.
 *
 * <p>Revealed rather than merely present, which is the whole of what makes it visible. Vanilla
 * gates a condition on the survey level of the market carrying it, so a world nobody has surveyed
 * reads as no ruin at all; the pair of stubs below is what lifts that gate.
 */
public final class DecivilisedPlanetFixtures {

    private DecivilisedPlanetFixtures() {
    }

    /**
     * A surveyed planet carrying a revealed decivilised condition - the ruin of a colony the player
     * has already seen die.
     *
     * <p>Its market is the condition-only placeholder vanilla leaves behind, owned by nobody, so no
     * colony read admits it and only the ruin arm of an inhabitation read reports its system as
     * settled.
     *
     * @return the planet mock, wired to carry that market
     */
    public static PlanetAPI buildRevealedDecivilisedPlanet() {

        // The condition and the market finish their own stubbing before the planet's opens, so
        // Mockito does not see one stubbing nested inside another.
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

    /**
     * Hangs a revealed ruin on a system already built, for a suite whose system came from a sector
     * fixture rather than from a builder of its own.
     *
     * @param system the system the ruin stands in; its planets are replaced by the one ruin
     */
    public static void placeRevealedDecivilisedPlanetIn(StarSystemAPI system) {

        var planets = List.of(buildRevealedDecivilisedPlanet());

        when(system.getPlanets())
            .thenReturn(planets);
    }
}
