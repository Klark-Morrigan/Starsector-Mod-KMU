package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldSystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the claim the shared counting rule exists to make: two mechanics counting one system report
 * the same colonies.
 *
 * <p>Neither planner alone can show it. Each is perfectly consistent with itself while counting a
 * different set from the other - which is what the two of them did, one reporting the colonies its
 * weights were summed over and the other the markets its contest had scored, so a station the
 * economy does not list reached neither band while both hover boxes named it. The two answers only
 * become comparable at a level that runs both.
 *
 * <p>An integration test rather than a unit one for that reason: the counts are reached through
 * each mechanic's own live read - a dominance pass over an economy, a finished contest - so posing
 * "the same system, counted twice" means wiring a system both can be asked about.
 *
 * <p>The system is wired so the two mechanics would disagree if either still counted for itself: a
 * station the economy does not list, which no weight can be computed from and no contest ever
 * scored, sits beside the colonies both do see.
 */
final class ColonyCellRibbonsIntegrationTest {

    private static final String SYSTEM_ID = "corvus";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    private static final Color TRITACHYON_DARK = new Color(20, 90, 80);

    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK),
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK))
        ::get;

    private static final RibbonPlanRules STANDARD_RULES =
        new RibbonPlanRules(
            new RibbonSegmentLengths(3, 1),
            new UncontestedCellBands(false, false));

    // Undiscovered colonies left out, which is the shipped reveal; neither mechanic's case here
    // turns on it.
    private static final boolean WITHOUT_DEV_REVEAL = false;

    // No memory flag imposed a claimant, so the contest settled the system on its own.
    private static final String NO_DECREE = null;

    // Every faction posed here may claim a system.
    private static final boolean IS_TERRITORIAL = true;

    // The scores the contest is posed at. Only their order matters - the band reports counts - so
    // the claimant leads and the rival trails.
    private static final int LEADING_SCORE = 30;
    private static final int TRAILING_SCORE = 10;

    // Colony sizes chosen so the two blocs cannot tie on weight. A tie would send the dominance
    // resolve to the proximity tie-break, which reads orbit geometry this system is not wired with.
    private static final int LARGER_COLONY = 6;
    private static final int SMALLER_COLONY = 4;

    @Nested
    class PlanSystemRibbon {

        @Test
        void countsOneSystemAlikeThroughBothPaintingMechanics() {
            // The Hegemony holds two colonies and takes the system on both mechanics; Tri-Tachyon
            // holds one listed colony and one station the economy never registered. Both bands
            // report two colonies each, in the same order, so the readout a player sees does not
            // depend on which layer they are looking at.
            var sector = buildContestedSector();
            var system = buildOnlySystem(sector);
            var holding = HolderPass.over(sector, WITHOUT_DEV_REVEAL, HolderGrouping.identity());
            var inputs = new RibbonPlanInputs(holding, PALETTES, STANDARD_RULES);

            var heldPlan = new HeldSystemRibbonPlanner(
                    DominancePass.over(
                        holding,
                        SectorPoliticsFixtures.buildStabilityWeightedRules()),
                    inputs)
                .planSystemRibbon(system);

            var claimedPlan = new ClaimedSystemRibbonPlanner(
                    new ClaimBreakdownReaderFake(buildContestWonBy(HEGEMONY, TRITACHYON)),
                    inputs)
                .planSystemRibbon(system);

            assertThat(heldPlan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
            assertThat(claimedPlan)
                .isEqualTo(heldPlan);
        }
    }

    // A system the Hegemony leads on both mechanics, with one of Tri-Tachyon's two colonies
    // registered with the economy and the other merely hanging on an entity of the system.
    private static SectorAPI buildContestedSector() {

        var hegemony = buildFaction(HEGEMONY);
        var sector = buildSectorWith(
            SYSTEM_ID,
            buildVisibleMarket(hegemony, LARGER_COLONY),
            buildVisibleMarket(hegemony, LARGER_COLONY),
            buildVisibleMarket(buildFaction(TRITACHYON), SMALLER_COLONY));

        placeMarketsOnSystemEntities(
            buildOnlySystem(sector),
            buildVisibleMarket(buildFaction(TRITACHYON), SMALLER_COLONY));

        return sector;
    }

    // The contest as its reader would report it for that system: the claimant ahead of the one
    // rival the mechanic weighed anything for.
    private static SystemClaimBreakdown buildContestWonBy(String claimantFactionId, String rivalFactionId) {
        return new SystemClaimBreakdown(
            NO_DECREE,
            claimantFactionId,
            List.of(
                ClaimStandingFixture.buildStandingOnOneMarket(
                    claimantFactionId,
                    LEADING_SCORE,
                    IS_TERRITORIAL),
                ClaimStandingFixture.buildStandingOnOneMarket(
                    rivalFactionId,
                    TRAILING_SCORE,
                    IS_TERRITORIAL)));
    }

    // The contest this planner is posed over, however it is asked for it. Its second read - the
    // decree flag - is not what this case is about and is answered as unset.
    private record ClaimBreakdownReaderFake(SystemClaimBreakdown contest)
        implements ClaimBreakdownReader {

        @Override
        public SystemClaimBreakdown readBreakdown(StarSystemAPI system) {
            return contest;
        }

        @Override
        public String readCoreFactionId(StarSystemAPI system) {
            return null;
        }
    }
}
