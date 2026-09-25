package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.owners.SectorOwnershipFixtures;
import kmu.maplayers.ownermap.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldSystemRibbonPlanner;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxSeamsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildHolderPassOver;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.buildInputsFor;
import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.stubFaction;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxReads.readDominanceSections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the agreement on the faction and alliance views that the claims layer's own suite pins for
 * its box: every faction the band under a cell draws a run for is one the box over that cell names
 * and the spotlight picker offers.
 *
 * <p>No surface alone can show it. The band counts the shared colony set while the box lists what
 * the weighing produced and the picker lists what it totalled, so the three answer through
 * different reads of one system - and they parted company exactly where a faction held nothing the
 * economy lists: every term of a dominance weight is economy-fed, so such a faction raised no
 * footprint, took no standing, took no stats entry, and went unnamed while the band beneath the
 * cursor drew its run.
 *
 * <p>The system is wired as that case: one faction holding a registered colony beside another
 * present through an unregistered station alone, which is what vanilla builds Galatia Academy as.
 * Which heading each lands under is the box's own business and follows from the ranking - what this
 * asserts is the one thing no single suite can, that the surfaces name one set of factions.
 *
 * <p>Each reads the system through a pass of its own, as a live bake, the hover above it and the
 * sidebar beside it do, so the agreement is between separate readings rather than between several
 * views of one hand-built answer.
 */
final class DominancePresenceReadoutIntegrationTest {

    private static final String SYSTEM_ID = "galatia";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point here: what the case reads back is which factions the box named at all.
    private static final String NO_CREST = null;

    // Colony sizes. Only the registered colony's is ever weighed - the unregistered one has no
    // industries, conditions or stability for the arithmetic to read - so the two differ merely to
    // read apart.
    private static final int REGISTERED_COLONY = 6;
    private static final int UNREGISTERED_STATION = 4;

    // What the design lays a colony's run and the parting after it at, restated as the literals a
    // case reads a band back in rather than taken off the rules the band was planned under.
    private static final int COLONY_RUN = 3;
    private static final int PARTING_RUN = 1;

    @BeforeEach
    void installBoxSeams() {
        PoliticalMapBoxSeamsFake.installSeams();
    }

    @AfterEach
    void clearBoxSeams() {
        PoliticalMapBoxSeamsFake.clearSeams();
    }

    @Nested
    class ComposeBody {

        @Test
        void namesEveryFactionTheBandBeneathTheCellDrawsARunFor() {
            // One system, two surfaces, one reading: the band draws a run apiece for the registered
            // colony and the unregistered station, and the box names both the faction the pass
            // weighed and the one it never could.
            var sector = buildSectorHoldingAnUnregisteredStation();
            var system = buildOnlySystem(sector);
            var holding = buildHolderPassOver(sector);

            var band = new HeldSystemRibbonPlanner(
                    DominancePass.createOver(holding, buildStabilityWeightedRules()),
                    buildInputsFor(holding))
                .planSystemRibbon(system);

            var sections = readDominanceSections(sector, system, FACTIONS);

            assertThat(band.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, COLONY_RUN),
                    new RibbonSegment(HEGEMONY_DARK, PARTING_RUN),
                    new RibbonSegment(TRITACHYON_BRIGHT, COLONY_RUN));

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Dominated by:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon");
        }
    }

    @Nested
    class AggregateDominanceStats {

        @Test
        void listsEveryBlocTheBandBeneathTheCellDrawsARunFor() {
            // The third surface, and the last one that answered off the weights. The picker's rows
            // are this fold's own keys, so a bloc it leaves out is a bloc the player cannot pick
            // out however plainly the band beneath the cursor draws its run - and the unregistered
            // holder is exactly the bloc no weight was ever worked out for.
            var sector = buildSectorHoldingAnUnregisteredStation();
            var holding = buildHolderPassOver(sector);

            var band = new HeldSystemRibbonPlanner(
                    DominancePass.createOver(holding, buildStabilityWeightedRules()),
                    buildInputsFor(holding))
                .planSystemRibbon(buildOnlySystem(sector));

            assertThat(band.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, COLONY_RUN),
                    new RibbonSegment(HEGEMONY_DARK, PARTING_RUN),
                    new RibbonSegment(TRITACHYON_BRIGHT, COLONY_RUN));

            assertThat(DominanceStatsAggregator.aggregateDominanceStats(
                    DominancePass.createOver(holding, buildStabilityWeightedRules()))
                .statsByBlocId())
                .containsOnlyKeys(HEGEMONY, TRITACHYON);
        }
    }

    // The Galatia shape: a system one faction holds a registered colony in and another is present in
    // through a station the economy's listing never held. The unregistered holder therefore raises
    // no footprint, and before the widening it reached no block of the box at all.
    private static SectorAPI buildSectorHoldingAnUnregisteredStation() {

        var registeredColony = buildVisibleMarket(buildFaction(HEGEMONY), REGISTERED_COLONY);
        var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID, registeredColony);

        placeMarketsOnSystemEntities(
            buildOnlySystem(sector),
            registeredColony,
            buildVisibleMarket(buildFaction(TRITACHYON), UNREGISTERED_STATION));

        stubFaction(sector, HEGEMONY, "The Hegemony", NO_CREST);
        stubFaction(sector, TRITACHYON, "Tri-Tachyon", NO_CREST);

        return sector;
    }

}
