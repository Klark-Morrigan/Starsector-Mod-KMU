package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;
import kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldSystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsFor;

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
 *
 * <p>The same wiring answers the other cross-surface question the band raises, so it is posed here
 * too: whether the fill under a spotlight keeps the bloc the band is counting. That one is a band
 * against a fill rather than a band against a band, but it fails the same way and only at a level
 * that runs both.
 */
final class ColonyCellRibbonsIntegrationTest {

    private static final String SYSTEM_ID = "corvus";

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
            var holding = HolderPass.over(sector, BASE_FOG, HolderGrouping.identity());
            var inputs = buildInputsFor(holding);

            var heldPlan = new HeldSystemRibbonPlanner(
                    DominancePass.over(
                        holding,
                        SectorPoliticsFixtures.buildStabilityWeightedRules()),
                    inputs)
                .planSystemRibbon(system);

            var claimedPlan = new ClaimedSystemRibbonPlanner(
                    buildReaderReporting(buildContestWonBy(HEGEMONY, TRITACHYON)),
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

        @Test
        void countsTheBlocWhoseFillTheSpotlightKeeps() {
            // The band and the fill over one cell, asked together. Tri-Tachyon's only foothold is a
            // station the economy never registered: it raises no dominance footprint, so before the
            // presence read was widened the spotlight receded the very system whose band was drawing
            // Tri-Tachyon a run. Posed here rather than in either surface's own suite for the reason
            // the suite exists - each was self-consistent while disagreeing with the other.
            var sector = buildSectorWhereTritachyonIsUnregistered();
            var system = buildOnlySystem(sector);
            var holding = HolderPass.over(sector, BASE_FOG, HolderGrouping.identity());
            var pass = DominancePass.over(
                holding,
                SectorPoliticsFixtures.buildStabilityWeightedRules());

            var band = new HeldSystemRibbonPlanner(pass, buildInputsFor(holding))
                .planSystemRibbon(system);

            assertThat(band.segments())
                .extracting(RibbonSegment::colour)
                .contains(TRITACHYON_BRIGHT);
            assertThat(FilteredPolitics.resolveFilteredHolder(pass, TRITACHYON).contestedSystemIds())
                .contains(SYSTEM_ID);
        }
    }

    // A system the Hegemony holds through the economy's own listing, with Tri-Tachyon's one
    // foothold hung on an entity of the system that the listing never held. Both factions are
    // resolvable by id, since the spotlight resolve colours its bloc off the faction's palette.
    private static SectorAPI buildSectorWhereTritachyonIsUnregistered() {

        var tritachyon = buildFaction(TRITACHYON, TRITACHYON_BRIGHT);
        var sector = buildSectorWith(
            SYSTEM_ID,
            List.of(buildFaction(HEGEMONY, HEGEMONY_BRIGHT), tritachyon),
            buildVisibleMarket(buildFaction(HEGEMONY), LARGER_COLONY));

        placeMarketsOnSystemEntities(
            buildOnlySystem(sector),
            buildVisibleMarket(tritachyon, SMALLER_COLONY));

        return sector;
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
    private static SystemClaimBreakdown buildContestWonBy(
            String claimantFactionId,
            String rivalFactionId) {

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

    // The claim reader the planner is posed with, reporting that contest for the one system wired
    // above and nothing for any other.
    private static ClaimBreakdownReaderFake buildReaderReporting(SystemClaimBreakdown contest) {

        var readerFake = new ClaimBreakdownReaderFake();
        readerFake.setBreakdown(SYSTEM_ID, contest);

        return readerFake;
    }
}
