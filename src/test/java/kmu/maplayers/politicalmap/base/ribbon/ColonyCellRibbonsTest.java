package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;
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

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PERSEAN = "persean";

    // The alliance the two allies fold into, named by the synthetic id an alliance record carries
    // rather than by either member: a bloc is its own thing, and its shades are its colour
    // faction's, resolved before a band ever asks for them.
    private static final String HEGEMONY_ALLIANCE = "hegemony_compact";

    // A bloc the map can no longer colour: it holds colonies, but its colour faction has gone from
    // the sector, so the palette read below has nothing to answer with.
    private static final String VANISHED = "vanished";

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    private static final Color TRITACHYON_DARK = new Color(20, 90, 80);
    private static final Color PERSEAN_BRIGHT = new Color(200, 180, 120);
    private static final Color PERSEAN_DARK = new Color(90, 70, 30);

    private static final FactionPalette HEGEMONY_PALETTE =
        new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK);

    // The shades every bloc in these cases draws in, read by bloc id exactly as the live map reads
    // them. A bloc absent from this map has no colour to resolve, which is the drop case.
    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            HEGEMONY_PALETTE,
            HEGEMONY_ALLIANCE,
            HEGEMONY_PALETTE,
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK),
            PERSEAN,
            new FactionPalette(PERSEAN_BRIGHT, PERSEAN_DARK))
        ::get;

    // The design's own proportions - a colony three widths long, parted by one width - paired with
    // the gate a player has left switched off, so a cell reads as banded only where a rival is in
    // it.
    private static final RibbonPlanRules STANDARD_RULES =
        new RibbonPlanRules(
            new RibbonSegmentLengths(3, 1),
            new UncontestedCellBands(false, false));

    // The faction view, where a bloc is a single faction. The alliance case states its own.
    private static final HolderGrouping NO_ALLIANCES = HolderGrouping.identity();

    // Whether the pass lifts the fog off colonies the player has not found - the dev reveal, which
    // is the one thing about a colony this rule's input does read.
    private static final boolean IS_FOG_LIFTED = true;
    private static final boolean IS_FOG_KEPT = false;

    // The size every posed colony carries. A band counts holdings rather than weighing them, so a
    // case varying it would vary nothing the rule can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanCellRibbon {

        @Test
        void drawsOneSegmentPerColonyABlocHoldsInTheSystem() {
            // Two Hegemony colonies and one of Tri-Tachyon's: three runs, the Hegemony's own parted
            // by its dark shade and closed off in it where the rival's run takes over.
            var hegemony = buildFaction(HEGEMONY);
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(hegemony, COLONY_SIZE),
                buildVisibleMarket(hegemony, COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonForABlocRankedButPresentInNothing() {
            // A bloc the mechanic ranked and the system does not hold - a faction whose only colony
            // there is one the fog keeps back, say - raises no run at all rather than a run of no
            // width, so the cell reads as the lone holder's it is.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

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

            var plan = planFor(
                HEGEMONY,
                List.of(HEGEMONY, TRITACHYON),
                buildInputsOver(sector, NO_ALLIANCES, IS_FOG_LIFTED),
                sector);

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(PERSEAN), COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE),
                buildVisibleMarket(buildFaction(PERSEAN), COLONY_SIZE),
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY), sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3),
                    new RibbonSegment(PERSEAN_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void foldsAlliedFactionsIntoOneRunInTheBlocsOwnShades() {
            // Two allies hold three colonies between them. The cell is painted for their bloc, so
            // the band is that bloc's three colonies in one run rather than two neighbouring runs -
            // and the rival beside them is what the presence gate opens on.
            var hegemony = buildFaction(HEGEMONY);
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(hegemony, COLONY_SIZE),
                buildVisibleMarket(hegemony, COLONY_SIZE),
                buildVisibleMarket(buildFaction(PERSEAN), COLONY_SIZE),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            var plan = planFor(
                HEGEMONY_ALLIANCE,
                List.of(HEGEMONY_ALLIANCE, TRITACHYON),
                buildInputsOver(sector, buildAllianceOf(HEGEMONY, PERSEAN), IS_FOG_KEPT),
                sector);

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
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction(HEGEMONY), COLONY_SIZE),
                buildVisibleMarket(buildFaction(VANISHED), COLONY_SIZE));

            assertThat(planFor(HEGEMONY, List.of(HEGEMONY, VANISHED), sector))
                .isEqualTo(RibbonPlan.NONE);
        }
    }

    // The one call most cases make: the faction view, the fog where the player finds it, and the
    // design's own segment lengths, leaving a case to state the system and the ranking.
    private static RibbonPlan planFor(
            String paintingBlocId,
            List<String> rankedBlocIds,
            SectorAPI sector) {

        return planFor(
            paintingBlocId,
            rankedBlocIds,
            buildInputsOver(sector, NO_ALLIANCES, IS_FOG_KEPT),
            sector);
    }

    private static RibbonPlan planFor(
            String paintingBlocId,
            List<String> rankedBlocIds,
            RibbonPlanInputs inputs,
            SectorAPI sector) {

        return ColonyCellRibbons.planCellRibbon(
            paintingBlocId,
            buildOnlySystem(sector),
            rankedBlocIds,
            inputs);
    }

    // The bake's inputs over a stubbed sector: the pass the colonies come out of, the shared
    // palettes, and the design's proportions.
    private static RibbonPlanInputs buildInputsOver(
            SectorAPI sector,
            HolderGrouping grouping,
            boolean shouldIncludeUndiscoveredMarkets) {

        return new RibbonPlanInputs(
            HolderPass.over(sector, shouldIncludeUndiscoveredMarkets, grouping),
            PALETTES,
            STANDARD_RULES);
    }

    // A grouping in which the two named factions share one alliance bloc under the alliance's own
    // id, coloured off the first of them - the shape the alliances view folds a bloc in.
    private static HolderGrouping buildAllianceOf(String colourFactionId, String memberFactionId) {
        return new HolderGrouping(
            Map.of(
                colourFactionId,
                HEGEMONY_ALLIANCE,
                memberFactionId,
                HEGEMONY_ALLIANCE),
            Map.of(HEGEMONY_ALLIANCE, colourFactionId),
            Map.of(HEGEMONY_ALLIANCE, "Hegemony Compact"));
    }
}
