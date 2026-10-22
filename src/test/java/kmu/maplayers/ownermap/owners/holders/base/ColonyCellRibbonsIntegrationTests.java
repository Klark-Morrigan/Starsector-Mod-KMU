package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.buildGroupOf;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.NO_PAINTER;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.buildInputsOver;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.buildSectorHolding;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.placeMarketsOnSystemEntities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins what a cell's band counts once a real pass walks the system: which colonies earn a segment
 * on the axes a colony can differ on, and that the band names an owner as the habitation read does.
 *
 * <p>The counting rule itself refuses to read any of these axes - whether the economy lists a
 * colony, whether it is held in concealment, whether the player has found it, whether anybody
 * lives on it. Each reaches the band only through the pass's habitation projection, so a case here
 * runs the rule over a real pass and its colony knowledge; neither alone shows the band and the map
 * beneath it agree. The reveal is posed through the pass exactly as the map moves it, and the
 * derelict as the market vanilla builds one as.
 *
 * <p>How the rule counts and orders what it is handed is pinned in {@link ColonyCellRibbonsTests}.
 */
final class ColonyCellRibbonsIntegrationTests {

    private static final String SYSTEM_ID = "corvus";

    // The size every posed colony carries. A band counts holdings rather than weighing them, so a
    // case varying it would vary nothing the rule can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanCellRibbon {

        @Test
        void countsAColonyTheEconomyDoesNotList() {
            // A station hanging on one of the system's entities without ever being registered -
            // the shape vanilla builds Galatia Academy in. No mechanic weighs it, and the hover boxes
            // name it, so the band that reports what is in the system reports it too.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            assertThat(planUnderTheFog(sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void countsAConcealedColonyThePlayerHasFound() {
            // A raided base is concealed and found at once. The player knows it is there, so it
            // earns a segment like any other colony - concealment being about the listing rather
            // than about what the player has been shown.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildHiddenMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            assertThat(planUnderTheFog(sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutAColonyThePlayerHasNotFound() {
            // The one exclusion the band keeps: counting out a colony the rest of the map declines
            // to show would state the very holding the fog is keeping back - and in a system with
            // nothing else of that faction's in it, name the faction hiding there. The cell reads
            // as the Hegemony's own, which is what the fog is telling the player.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildUndiscoveredHiddenMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            assertThat(planUnderTheFog(sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void countsAnUndiscoveredColonyWhereTheRevealLiftsTheFog() {
            // The same system under the dev reveal, which the fills are drawn under too: the band
            // counts what the map is showing rather than what the fog would have kept.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildUndiscoveredHiddenMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            var plan = ColonyCellRibbons.planCellRibbon(
                Optional.of(HEGEMONY),
                buildOnlySystem(sector),
                List.of(HEGEMONY, TRITACHYON),
                buildInputsOver(sector, HolderGrouping.identity(), UNDER_THE_REVEAL));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsNoBandForASystemHoldingOnlyAnAbandonedStation() {
            // A derelict raises no run, so a system holding one and nothing else bands not at
            // all - the same reading that leaves its cell drawn as empty backdrop. The hover box
            // over that cell still names it, which is the one place the band and the box are
            // meant to differ.
            //
            // Hung on a system entity rather than listed, because the economy listing is one of
            // the two things that make a station somebody's: a listed derelict is an outpost, and an
            // outpost counts.
            var sector = buildSectorWith(SYSTEM_ID);

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildAbandonedStationMarket(COLONY_SIZE));

            var plan = ColonyCellRibbons.planCellRibbon(
                NO_PAINTER,
                buildOnlySystem(sector),
                List.of(),
                buildInputsOver(sector, HolderGrouping.identity(), BASE_FOG));

            assertThat(plan.segments())
                .isEmpty();
        }

        @Test
        void countsTheColonyBesideAnAbandonedStationAndNotTheStation() {
            // The same derelict once somebody settles the system. One run for the colony, none for the
            // derelict - so the band reports the one holding there is rather than a system split
            // between a faction and a wreck.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildAbandonedStationMarket(COLONY_SIZE));

            assertThat(planUnderTheFog(sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void namesAColonysOwnerAsTheHabitationReadDoes() {
            // A market states its owner twice: the ID a transfer writes and the faction object.
            // Posed apart here, the band must draw the ID's owner, as the habitation read the
            // cell, the picker and the spotlight answer off does.
            var transferred = buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE);

            when(transferred.getFactionId())
                .thenReturn(TRITACHYON);

            var sector = buildSectorWith(SYSTEM_ID, transferred);
            var inputs = buildInputsOver(sector, HolderGrouping.identity(), BASE_FOG);
            var system = buildOnlySystem(sector);

            assertThat(ColonyCellRibbons.planCellRibbon(NO_PAINTER, system, List.of(), inputs).segments())
                .containsExactly(new RibbonSegment(TRITACHYON_BRIGHT, 3));
            assertThat(inputs.pass().readHabitationIn(system).blocIds())
                .containsExactly(TRITACHYON);
        }

        @Test
        void foldsAlliedFactionsIntoOneRunInTheBlocsOwnShades() {
            // Two allies hold three colonies between them. The cell is painted for their bloc, so
            // the band is that bloc's three colonies in one run rather than two neighbouring runs,
            // with the rival's own run following it.
            var sector = buildSectorHolding(
                SYSTEM_ID,
                HEGEMONY,
                HEGEMONY,
                PERSEAN,
                TRITACHYON);

            var plan = ColonyCellRibbons.planCellRibbon(
                Optional.of(GROUP_BLOC_ID),
                buildOnlySystem(sector),
                List.of(GROUP_BLOC_ID, TRITACHYON),
                buildInputsOver(sector, buildGroupOf(HEGEMONY, PERSEAN), BASE_FOG));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }
    }

    // The one call most cases make: a Hegemony cell with the Hegemony ranked ahead of Tri-Tachyon,
    // the identity grouping, and the fog where the player finds it, leaving a case to state the
    // system.
    private static RibbonPlan planUnderTheFog(SectorAPI sector) {

        return ColonyCellRibbons.planCellRibbon(
            Optional.of(HEGEMONY),
            buildOnlySystem(sector),
            List.of(HEGEMONY, TRITACHYON),
            buildInputsOver(sector, HolderGrouping.identity(), BASE_FOG));
    }
}
