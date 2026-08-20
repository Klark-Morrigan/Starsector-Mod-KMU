package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.ColonyVisibility;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsFor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the held planner's two jobs: saying whether the dominance mechanic speaks for a system at
 * all, and - where it does - counting that system under the very pass the fills were resolved
 * under.
 *
 * <p>The first job is the one worth guarding, because a second reader depends on the answer.
 * {@link HeldOrClaimedSystemRibbonPlanner} asks this planner first and falls through to the claim
 * mechanic only where it declines, so "declines" is not an internal detail here - it is the signal
 * that hands a cell to the other mechanic. A planner that answered with a present-but-empty plan
 * where it meant to decline would silently take every claim-painted cell's band away, and one that
 * declined where it held something would let the claim contest count a system dominance already
 * owns.
 *
 * <p>An integration test rather than a unit one: the decline is reached through the economy read
 * the pass performs, so posing "no bloc holds a counted market here" means wiring an economy that
 * holds none rather than stubbing a seam that says so. The counting grammar beneath it - how many
 * widths a run takes, where a divider falls - is {@link HeldCellRibbonsTest}'s, and is read here
 * only far enough to show that the ranked counts reached it.
 */
final class HeldSystemRibbonPlannerIntegrationTest {

    private static final String SYSTEM_ID = "corvus";

    // Undiscovered colonies left out, which is the shipped rule; no case here turns on it.
    private static final ColonyVisibility WITHOUT_DEV_REVEAL = ColonyVisibility.BASE_FOG;

    // Colony sizes chosen so the two blocs cannot tie on weight. A tie would send the resolve to
    // the proximity tie-break, which reads orbit geometry none of these systems is wired with -
    // so the sizes are what keep these cases about the planner rather than about a fallback.
    private static final int LARGER_COLONY = 6;
    private static final int SMALLER_COLONY = 4;

    @Nested
    class PlanHeldSystemRibbon {

        @Test
        void declinesASystemWithNoSectorToReadItFrom() {
            // Before the sector stands up. Declining rather than planning an empty band is what
            // keeps the composition from reading this as "dominance holds nothing here" and
            // handing the cell to the claim contest on the strength of a missing sector.
            assertThat(planFor(null, systemOf(buildSectorHeldBy(HEGEMONY))))
                .isEmpty();
        }

        @Test
        void declinesASystemThatIsNotThere() {
            assertThat(planFor(buildSectorHeldBy(HEGEMONY), null))
                .isEmpty();
        }

        @Test
        void declinesWhileTheEconomyHasNotStoodUp() {
            // Mid-load: the systems are walkable but the economy behind their markets is absent,
            // so there is nothing to count and the read stops at its guard rather than faulting
            // on the way into one.
            var sector = SectorPoliticsFixtures.buildEconomylessSectorWithSystem(SYSTEM_ID);

            assertThat(planFor(sector, systemOf(sector)))
                .isEmpty();
        }

        @Test
        void declinesASystemNoBlocHoldsAMarketIn() {
            // The economy is up and the system is in it, holding nothing. This is the decline the
            // composition actually rides on: an uninhabited or purely claim-held system, which the
            // claim mechanic then counts.
            var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID);

            assertThat(planFor(sector, systemOf(sector)))
                .isEmpty();
        }

        @Test
        void countsAContestedSystemUnderThePassThatPaintedIt() {
            // Two blocs present, so the contest gate opens. The Hegemony's two colonies outweigh
            // Tri-Tachyon's one, so it both wins the system and leads the band - and its run is
            // closed off in its own dark shade where the rival's begins.
            var sector = buildSectorContestedBy(HEGEMONY, TRITACHYON);

            assertThat(planFor(sector, systemOf(sector)))
                .map(RibbonPlan::segments)
                .contains(List.of(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3)));
        }

        @Test
        void declinesNothingWhereOneBlocHoldsTheWholeSystem() {
            // A lone holder is present, so the mechanic speaks for the system - reporting the
            // holder's own footprint rather than a contest. Present rather than empty is the whole
            // distinction: an empty answer here would hand a system dominance owns outright to the
            // claim contest.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                buildColony(HEGEMONY, LARGER_COLONY));

            assertThat(planFor(sector, systemOf(sector)))
                .map(RibbonPlan::segments)
                .contains(List.of(new RibbonSegment(HEGEMONY_BRIGHT, 3)));
        }
    }

    @Nested
    class PlanSystemRibbon {

        @Test
        void flattensADeclinedSystemToNoBand() {
            // The planner face answers every system, so a decline it cannot express becomes the
            // bandless plan - which is what a view resolving this planner alone draws for a
            // system the held mechanic does not paint.
            var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID);

            assertThat(buildPlanner(sector).planSystemRibbon(systemOf(sector)))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void handsBackThePlanOfASystemItCounts() {
            var sector = buildSectorContestedBy(HEGEMONY, TRITACHYON);

            assertThat(buildPlanner(sector).planSystemRibbon(systemOf(sector)).segments())
                .startsWith(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }
    }

    // The planner under a pass reading the shared stability-weighted rule under the plain faction
    // grouping, which is the pass a faction-view rebuild resolves.
    //
    // The ranking and the counting take the one reading of the sector, as they do in a live bake:
    // built apart, the weights could be read off one walk of a system and its colonies off another.
    private static HeldSystemRibbonPlanner buildPlanner(SectorAPI sector) {

        var holding = HolderPass.over(sector, WITHOUT_DEV_REVEAL, HolderGrouping.identity());

        return new HeldSystemRibbonPlanner(
            DominancePass.over(holding, SectorPoliticsFixtures.buildStabilityWeightedRules()),
            buildInputsFor(holding));
    }

    private static Optional<RibbonPlan> planFor(SectorAPI sector, StarSystemAPI system) {
        // The planner is built around whichever sector is posed, including none at all, since the
        // absent sector is one of the cases - so the guard is reached through the real field
        // rather than through an argument it does not have.
        return buildPlanner(sector).planHeldSystemRibbon(system);
    }

    private static StarSystemAPI systemOf(SectorAPI sector) {
        return SectorPoliticsFixtures.buildOnlySystem(sector);
    }

    // A system two blocs hold, the first with two colonies and the second with one, so the first
    // leads on weight and the band has both a within-bloc parting and a handover to report.
    private static SectorAPI buildSectorContestedBy(String leadingBlocId, String trailingBlocId) {
        return SectorPoliticsFixtures.buildSectorWith(
            SYSTEM_ID,
            buildColony(leadingBlocId, LARGER_COLONY),
            buildColony(leadingBlocId, LARGER_COLONY),
            buildColony(trailingBlocId, SMALLER_COLONY));
    }

    private static MarketAPI buildColony(String factionId, int size) {
        return SectorPoliticsFixtures.buildVisibleMarket(
            SectorPoliticsFixtures.buildFaction(factionId),
            size);
    }

    // A sector holding one system with a single colony of the named bloc, for the cases whose
    // sector or system is the thing being withheld and whose contents therefore never matter.
    private static SectorAPI buildSectorHeldBy(String blocId) {
        return SectorPoliticsFixtures.buildSectorWith(
            SYSTEM_ID,
            buildColony(blocId, LARGER_COLONY));
    }
}
