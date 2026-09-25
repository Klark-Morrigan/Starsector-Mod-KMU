package kmu.maplayers.ownermap.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.holding.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.ownermap.holding.HolderGroupingFixture.buildGroupOf;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildHiddenMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.NO_PAINTER;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.PERSEAN_DARK;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.VANISHED_BLOC;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.buildInputsOver;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanFixtures.buildSectorHolding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one counting rule every painting mechanic hands its cells to: which colonies of a
 * system earn a segment, and where a bloc's run falls in the band.
 *
 * <p>The counting cases are posed on the axes a colony can differ on and the rule refuses to read -
 * whether the economy lists it, whether it is held in concealment - beside the two it does read:
 * whether the player may be shown it at all, and whether anybody lives on it. Both of those are the
 * pass's own projection rather than rules of this class, so the reveal is posed through the pass
 * exactly as the map moves it, and the derelict is posed as the market vanilla builds one as.
 *
 * <p>The ordering cases are posed on a ranking that does not cover every bloc present, which is the
 * shape the count and the ranking produce between them: a mechanic ranks the blocs it scored, and
 * the habitation projection hands back blocs it never weighed.
 *
 * <p>The segment grammar itself has its own suite, so what is asserted here is the counts and the
 * order reaching it - read off the runs, those being the only place either is visible.
 */
final class ColonyCellRibbonsTest {

    private static final String SYSTEM_ID = "corvus";

    // The identity grouping, where a bloc is a single faction. The group case states its own.
    private static final HolderGrouping UNGROUPED = HolderGrouping.identity();

    // The size every posed colony carries. A band counts holdings rather than weighing them, so a
    // case varying it would vary nothing the rule can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanCellRibbon {

        @Test
        void drawsOneSegmentPerColonyABlocHoldsInTheSystem() {
            // Two Hegemony colonies and one of Tri-Tachyon's: three runs, the Hegemony's own parted
            // by its dark shade and closed off in it where the rival's run takes over.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, HEGEMONY, TRITACHYON);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsThePainterAloneWhereItIsTheOnlyBlocPresent() {
            // The single-holder cell, which most of the sector is: the fill has said whose system
            // it is, and the band says how much is in it.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void drawsNoRunForABlocRankedButPresentInNothing() {
            // A bloc the mechanic ranked and the system does not hold - a faction whose only colony
            // there is one the fog keeps back, say - raises no run at all rather than a run of no
            // width, so the band is the painter's own and carries no trace of the ranking.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

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

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
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

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutAColonyThePlayerHasNotFound() {
            // The one exclusion the rule keeps: a band counting out a colony the rest of the map
            // declines to show would state the very holding the fog is keeping back - and in a
            // system with nothing else of that faction's in it, name the faction hiding there. The
            // cell reads as the Hegemony's own, which is what the fog is telling the player.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildUndiscoveredHiddenMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
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

            var plan = planThrough(
                NO_PAINTER,
                List.of(),
                buildOnlySystem(sector),
                buildInputsOver(sector, UNGROUPED, BASE_FOG));

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

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
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

            var plan = planThrough(
                Optional.of(HEGEMONY),
                List.of(HEGEMONY, TRITACHYON),
                buildOnlySystem(sector),
                buildInputsOver(sector, UNGROUPED, UNDER_THE_REVEAL));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void keepsTheMechanicsRankingAheadOfTheBlocsItNeverRanked() {
            // The mechanic ranked the painter and Tri-Tachyon, and the Persean League is in the
            // system through a colony no score was computed from. The ranked blocs keep their places
            // and the unranked one draws behind them - a place among the ranking would state it took
            // part in a contest it never entered.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, PERSEAN, TRITACHYON);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3));
        }

        @Test
        void ordersTheBlocsNoMechanicRankedById() {
            // Two blocs the mechanic did not score have no order to inherit, so they take the one
            // order that cannot depend on which of them the walk reached first.
            var sector = buildSectorHolding(SYSTEM_ID, TRITACHYON, PERSEAN, HEGEMONY);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3),
                    new RibbonSegment(PERSEAN_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutAColonyWhoseOwnerCarriesNoId() {
            // A faction with no ID belongs to no bloc the map can name. Left out rather than pooled
            // under a nameless bloc, which would draw one run for several such owners' colonies at
            // once - so the band is the painter's own colony and nothing beside it.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(null), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
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

            var grouping = buildGroupOf(HEGEMONY, PERSEAN);
            var groupBlocId = grouping.resolveBlocId(HEGEMONY);

            var plan = planThrough(
                Optional.of(groupBlocId),
                List.of(groupBlocId, TRITACHYON),
                buildOnlySystem(sector),
                buildInputsOver(sector, grouping, BASE_FOG));

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

        @Test
        void countsTheColoniesOfACellNoFillCovers() {
            // A system two blocs are settled in that no layer paints for anybody. The counting is
            // the same as ever - the painter is the contest's question, not the count's - and neither
            // bloc is ranked, so both come out in ID order.
            var sector = buildSectorHolding(SYSTEM_ID, TRITACHYON, HEGEMONY);

            var plan = planThrough(
                NO_PAINTER,
                List.of(),
                buildOnlySystem(sector),
                buildInputsOver(sector, UNGROUPED, BASE_FOG));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesOutABlocWithNoShadesToDrawIn() {
            // A bloc whose colour faction has gone from the sector has nothing to paint a run in,
            // so it is left out rather than drawn colourless - and the band is the painter's own
            // colony, with no gap where the vanished bloc's run would have been.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, VANISHED_BLOC);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, VANISHED_BLOC), sector).segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }
    }

    // The one call most cases make: the identity grouping and the fog where the player finds it, leaving
    // a case to state the system and the ranking. The painter is named rather than optional here,
    // a case posing one having nothing to say about the cells no fill covers.
    private static RibbonPlan planFor(
            String paintingBlocId,
            List<String> rankedBlocIds,
            SectorAPI sector) {

        return planThrough(
            Optional.of(paintingBlocId),
            rankedBlocIds,
            buildOnlySystem(sector),
            buildInputsOver(sector, UNGROUPED, BASE_FOG));
    }

    private static RibbonPlan planThrough(
            Optional<String> paintingBlocId,
            List<String> rankedBlocIds,
            StarSystemAPI system,
            RibbonPlanInputs inputs) {

        return ColonyCellRibbons.planCellRibbon(paintingBlocId, system, rankedBlocIds, inputs);
    }
}
