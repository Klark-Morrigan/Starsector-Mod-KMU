package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.FOG_KEPT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.FOG_LIFTED;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_ALLIANCE;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.VANISHED_BLOC;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsOver;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildSectorHolding;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one counting rule both painting mechanics hand their cells to: which colonies of a
 * system earn a segment, and where a bloc's run falls in the band.
 *
 * <p>The counting cases are posed on the axes a colony can differ on and the rule refuses to read -
 * whether the economy lists it, whether it is held in concealment - beside the one axis it does
 * read, which is whether the player may be shown it at all. That last one is the pass's own
 * projection rather than a rule of this class, so the reveal is posed through the pass exactly as
 * the map moves it.
 *
 * <p>The ordering cases are posed on a ranking that does not cover every bloc present, which is the
 * shape the widening produces: a mechanic ranks the blocs it scored, and the shared set hands back
 * blocs it never weighed.
 *
 * <p>The segment grammar itself has its own suite, so what is asserted here is the counts and the
 * order reaching it - read off the runs, those being the only place either is visible.
 */
final class ColonyCellRibbonsTest {

    private static final String SYSTEM_ID = "corvus";

    // The faction view, where a bloc is a single faction. The alliance case states its own.
    private static final HolderGrouping NO_ALLIANCES = HolderGrouping.identity();

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
        void drawsNoRibbonWhereThePainterIsTheOnlyBlocPresent() {
            // The single-holder cell, which most of the sector is: the fill already says whose
            // system it is, so a band of the painter alone would only repeat it.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonForABlocRankedButPresentInNothing() {
            // A bloc the mechanic ranked and the system does not hold - a faction whose only colony
            // there is one the fog keeps back, say - raises no run at all rather than a run of no
            // width, so the cell reads as the lone holder's it is.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void countsAColonyTheEconomyDoesNotList() {
            // A station hanging on one of the system's entities without ever being registered -
            // the shape vanilla builds Galatia Academy in. No mechanic weighs it, and both boxes
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
            // declines to show would state the very holding the fog is keeping back - and on a cell
            // with nothing else in it, name the faction hiding there.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildUndiscoveredHiddenMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, TRITACHYON), sector))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void countsAnUnfoundColonyWhereTheRevealLiftsTheFog() {
            // The same system under the dev reveal, which the fills are drawn under too: the band
            // counts what the map is showing rather than what the fog would have kept.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildUndiscoveredHiddenMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            var plan = planThrough(
                HEGEMONY,
                List.of(HEGEMONY, TRITACHYON),
                buildOnlySystem(sector),
                buildInputsOver(sector, NO_ALLIANCES, FOG_LIFTED));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void keepsTheMechanicsRankingAheadOfTheBlocsItNeverRanked() {
            // The shape the widening produces: the mechanic ranked the painter and Tri-Tachyon, and
            // the Persean League is in the system through a colony no score was computed from. The
            // ranked blocs keep their places and the unranked one draws behind them - a place among
            // the ranking would claim it took part in a contest it never entered.
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
            // Two blocs neither mechanic scored have no order to inherit, so they take the one
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
            // A faction with no id belongs to no bloc the map can name. Left out rather than pooled
            // under a nameless bloc, which would draw one run for several such owners' colonies at
            // once - and with only the painter left, the cell draws nothing at all.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(null), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void foldsAlliedFactionsIntoOneRunInTheBlocsOwnShades() {
            // Two allies hold three colonies between them. The cell is painted for their bloc, so
            // the band is that bloc's three colonies in one run rather than two neighbouring runs -
            // and the rival beside them is what the presence gate opens on.
            var sector = buildSectorHolding(
                SYSTEM_ID,
                HEGEMONY,
                HEGEMONY,
                PERSEAN,
                TRITACHYON);

            var plan = planThrough(
                HEGEMONY_ALLIANCE,
                List.of(HEGEMONY_ALLIANCE, TRITACHYON),
                buildOnlySystem(sector),
                buildInputsOver(sector, buildAllianceOf(HEGEMONY, PERSEAN), FOG_KEPT));

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
        void leavesOutABlocWithNoShadesToDrawIn() {
            // A bloc whose colour faction has gone from the sector has nothing to paint a run in,
            // so it is left out rather than drawn colourless - and with only the painter left, the
            // cell falls through the gate and draws nothing at all.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, VANISHED_BLOC);

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, VANISHED_BLOC), sector))
                .isEqualTo(RibbonPlan.NONE);
        }
    }

    // The one call most cases make: the faction view and the fog where the player finds it, leaving
    // a case to state the system and the ranking.
    private static RibbonPlan planFor(
            String paintingBlocId,
            List<String> rankedBlocIds,
            SectorAPI sector) {

        return planThrough(
            paintingBlocId,
            rankedBlocIds,
            buildOnlySystem(sector),
            buildInputsOver(sector, NO_ALLIANCES, FOG_KEPT));
    }

    private static RibbonPlan planThrough(
            String paintingBlocId,
            List<String> rankedBlocIds,
            StarSystemAPI system,
            RibbonPlanInputs inputs) {

        return ColonyCellRibbons.planCellRibbon(paintingBlocId, system, rankedBlocIds, inputs);
    }
}
