package kmu.maplayers.politicalmap.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;

/**
 * Everything a hover box reaches past the sector to reach the game, stood in at the state an
 * integration suite reads it under: the colours it draws in, and the two live settings reads behind
 * its rules.
 *
 * <p>Held as one fixture because a box touches all three before it composes anything, so a suite
 * asserting on any of them stands all three up - and each suite writing that itself restates both the
 * values and the order they must be installed and taken down in. Written out per suite they drift:
 * one poses a weighting another poses differently, and each copy goes on agreeing with itself while
 * the suites disagree about what "the shipped state" is.
 *
 * <p>The state is the shipped one throughout - the fog alone and the shared stability-only weighting.
 * The plain faction view's identity grouping is not a seam here: a box is built with the grouping of
 * the view that injects it, so a suite states it where it builds the box. A suite whose subject is one
 * of these poses it itself and does not take this: the point here is to be the
 * background a suite about something else does not have to state.
 *
 * <p>The palette is installed first and cleared last, its own fake owning the settings proxy that
 * {@code Misc}'s class initialiser reads - so the shades and the strings stand up together and no
 * second {@code Misc} mock can collide with them.
 */
public final class PoliticalMapBoxSeamsFake {

    private static MockedStatic<MapVisibilityRules> visibilityRulesMock;
    private static MockedStatic<DominanceRules> dominanceRulesMock;

    private PoliticalMapBoxSeamsFake() {
    }

    /**
     * Stands every seam up at the shipped state, so a case states only what it is about.
     *
     * <p>The two rules are live LunaLib reads, unreachable from the test JVM. The colony rule stands
     * in as the fog alone, which is what every colony a suite stages is posed under unless it says
     * otherwise; the weighting stands in as the shared stability-only rule, so two surfaces read in
     * one case cannot be weighing under different maps.
     */
    public static void installSeams() {

        CellTooltipPaletteFake.installPalette();

        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        dominanceRulesMock = Mockito.mockStatic(DominanceRules.class);
        dominanceRulesMock
            .when(DominanceRules::readFromLunaSettings)
            .thenReturn(buildStabilityWeightedRules());
    }

    /** Takes every seam back down in the order it was raised, so a suite leaves no statics mocked. */
    public static void clearSeams() {

        dominanceRulesMock.close();
        visibilityRulesMock.close();
        CellTooltipPaletteFake.clearPalette();
    }
}
