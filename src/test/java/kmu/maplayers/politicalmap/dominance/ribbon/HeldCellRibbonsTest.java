package kmu.maplayers.politicalmap.dominance.ribbon;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.ribbon.BlocPaletteReader;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the order a held cell's band comes out in, and what its runs are counted from.
 *
 * <p>The counting is settled before this rule sees it - the dominance pass banked a market count
 * per bloc as it scored the system - so what is stated here is the ordering, which is the whole
 * of what this side adds. The band has to open on the bloc the cell is painted for whatever gave
 * that bloc the system, and rank the rest exactly as the standings box ranks them, or the band
 * and the fill it sits inside disagree about who leads.
 *
 * <p>The cases are posed on the two ways that can go wrong: a painter that does not lead on
 * weight, which the dominance rule's tie-breaks below the weights genuinely produce, and two
 * rivals level on weight, where only the id separates them. Each poses its footprints in an
 * order the assertion does not expect back, since an ordering rule is invisible against inputs
 * already in the order it would produce.
 */
final class HeldCellRibbonsTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PERSEAN = "persean";

    // A bloc the map can no longer colour: it holds markets, but its colour faction has gone from
    // the sector, so the palette read below has nothing to answer with.
    private static final String VANISHED = "vanished";

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    private static final Color TRITACHYON_DARK = new Color(20, 90, 80);
    private static final Color PERSEAN_BRIGHT = new Color(200, 180, 120);
    private static final Color PERSEAN_DARK = new Color(90, 70, 30);

    // The shades every bloc in these cases draws in, read by bloc id exactly as the live map reads
    // them. A bloc absent from this map has no colour to resolve, which is the drop case.
    private static final BlocPaletteReader PALETTES = Map.of(
            HEGEMONY,
            new FactionPalette(HEGEMONY_BRIGHT, HEGEMONY_DARK),
            TRITACHYON,
            new FactionPalette(TRITACHYON_BRIGHT, TRITACHYON_DARK),
            PERSEAN,
            new FactionPalette(PERSEAN_BRIGHT, PERSEAN_DARK))
        ::get;

    // The design's own proportions - a market three widths long, parted by one width - paired
    // with the colours every case reads its runs back in.
    private static final RibbonPlanInputs STANDARD_INPUTS =
        new RibbonPlanInputs(PALETTES, new RibbonSegmentLengths(3, 1));

    // The two weights below the combined one. Held constant across every case: the ordering reads
    // the combined weight alone, so a case varying either would vary nothing the rule can see.
    private static final int ANY_LARGEST_MARKET_WEIGHT = 4000;
    private static final int ANY_PLANET_WEIGHT = 4000;

    @Nested
    class PlanHeldCellRibbon {

        @Test
        void drawsNoRibbonWhereThePainterIsTheOnlyBlocPresent() {
            // The single-holder cell, which most of the sector is: the fill already says whose
            // system it is, so a band of the painter alone would only repeat it.
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(2, 9000));

            assertThat(planFor(HEGEMONY, footprints))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsOneSegmentPerMarketTheFootprintBanked() {
            // The count comes off the footprint the fill was decided from, so three colonies are
            // three runs, parted by the bloc's dark shade and closed off in it where the rival's
            // run takes over.
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(3, 9000));
            footprints.put(TRITACHYON, buildFootprint(1, 2000));

            assertThat(planFor(HEGEMONY, footprints).segments())
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
        void leadsWithThePainterEvenWhereARivalOutweighsIt() {
            // The dominance rule can hand a system to a bloc that leads on none of the weights -
            // a dead heat settled by the market nearest the system centre, or by id - and the band
            // has to open on the bloc the cell is actually painted for regardless.
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(TRITACHYON, buildFootprint(1, 8000));
            footprints.put(HEGEMONY, buildFootprint(1, 3000));

            assertThat(planFor(HEGEMONY, footprints).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void ranksTheRivalsBehindThePainterByDescendingWeight() {
            // Behind the leader the band reads as the standings box does, so the heavier rival is
            // the nearer one.
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(PERSEAN, buildFootprint(1, 2000));
            footprints.put(HEGEMONY, buildFootprint(1, 9000));
            footprints.put(TRITACHYON, buildFootprint(1, 5000));

            assertThat(planFor(HEGEMONY, footprints).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3));
        }

        @Test
        void breaksAWeightTieBetweenRivalsByBlocId() {
            // Two rivals level on weight are separated by id and nothing else, so the order never
            // depends on which of them the economy walk reached first.
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(1, 9000));
            footprints.put(TRITACHYON, buildFootprint(1, 5000));
            footprints.put(PERSEAN, buildFootprint(1, 5000));

            assertThat(planFor(HEGEMONY, footprints).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3),
                    new RibbonSegment(PERSEAN_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void dropsABlocWithNoShadesToDrawIn() {
            // A bloc whose colour faction has gone from the sector has nothing to paint a run in,
            // so it is left out rather than drawn colourless - and with only the painter left, the
            // cell falls through the gate and draws nothing at all.
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(2, 9000));
            footprints.put(VANISHED, buildFootprint(1, 5000));

            assertThat(planFor(HEGEMONY, footprints))
                .isEqualTo(RibbonPlan.NONE);
        }
    }

    // One bloc's footprint as the dominance pass banked it: the colony count the band reports and
    // the combined weight the ordering behind the painter reads.
    private static MarketFootprint buildFootprint(int marketCount, int totalWeight) {
        return new MarketFootprint(
            marketCount,
            totalWeight,
            ANY_LARGEST_MARKET_WEIGHT,
            ANY_PLANET_WEIGHT);
    }

    private static RibbonPlan planFor(
            String paintingBlocId,
            Map<String, MarketFootprint> footprintByBlocId) {

        return HeldCellRibbons.planHeldCellRibbon(
            paintingBlocId,
            footprintByBlocId,
            STANDARD_INPUTS);
    }
}
