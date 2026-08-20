package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the coast shut in, and every way a pocket of it can come out wrong.
 *
 * <p>The half of the report that is about the VOID rather than about the cells: how much the
 * smoothing bought, what the reaches closed behind them, and the three faults a closed pocket
 * can carry - out past the coast that shut it in, no room left to draw, or ground inside the
 * coast that nothing drew at all.
 *
 * <p>Its own class because it grows on its own. Every rule about a pocket that has been settled
 * so far arrived as one more measure here, while the counts of cells and channels next door
 * have not moved - and a file that both of them live in is a file nobody can read the shape of.
 *
 * <p>Every measure is taken at both shapings where the two can differ, since a pocket that
 * exists at the void's true extent and vanishes a channel out is the one fault a single number
 * cannot show.
 */
final class CoastVoidReport {

    // Area percentiles worth naming when describing how big the trapped pockets are.
    private static final double[] REPORTED_PERCENTILES = {0.5, 0.9, 1.0};

    // The low end as well as the high, because what is being asked of the frontages is
    // whether the crossed cells sit at the crowded end of the population.
    private static final double[] FRONTAGE_PERCENTILES = {0.1, 0.5, 0.9};

    private CoastVoidReport() {
    }

    // What the smoothing takes out, as the two counts that say whether it did anything. Marks
    // is how many stretches of coast the cells actually make; points is how many the smoothed
    // line passes through. Equal counts mean the skip rules refused every candidate, which
    // reads on screen exactly like the smoothing being switched off.
    static void reportCoastlines(LaidCoast laid) {

        var traced = laid.traced();
        var points = 0;

        for (var coast : traced.coasts()) {
            points += coast.size();
        }

        System.out.printf(
            Locale.ROOT,
            "smoothed outer edges: %d, over %d stretches of coast, drawn through %d points; "
                + "%d runs visibly cross a cell, worst reach in %.1f (both have to be 0)%n",
            traced.coasts().size(),
            CoastMeasures.countCoastMarks(traced),
            points,
            CoastCrossings.findVisibleCrossings(traced, MapLook.RING_STROKE).size(),
            CoastCrossings.measureDeepestIncursion(traced));

        System.out.printf(
            Locale.ROOT,
            "how deep each one goes, worst first: %s%n",
            formatPenetrationDepths(CoastCrossings.findPenetrations(traced)));

        var frontages = new ArrayList<>(CoastMeasures.measureFrontages(traced));
        frontages.sort(Double::compare);

        System.out.printf(
            Locale.ROOT,
            "frontage offered, over every stretch: p10 %.0f / p50 %.0f / p90 %.0f%n",
            ReportFigures.findPercentile(frontages, FRONTAGE_PERCENTILES[0]),
            ReportFigures.findPercentile(frontages, FRONTAGE_PERCENTILES[1]),
            ReportFigures.findPercentile(frontages, FRONTAGE_PERCENTILES[2]));

        System.out.printf(
            Locale.ROOT,
            "each crossing as depth/frontage of the cell crossed: %s%n",
            formatAgainstDepth(CoastMeasures.measureCrossingFrontages(traced)));

        System.out.printf(
            Locale.ROOT,
            "each crossing as depth/stretches skipped across it (0 = neighbours): %s%n",
            formatAgainstDepth(CoastMeasures.measureCrossingGaps(traced)));

        reportTrappedVoid(laid);
    }

    // What the smoothing shut in behind it, as the pockets it becomes. A coast that traps
    // nothing has bought no pocket space and is only redrawing the cells' own outline, so the
    // count is the number that says whether the smoothing did the thing it exists to do -
    // and how many of them survive the channel is the number that says they can be drawn.
    private static void reportTrappedVoid(LaidCoast laid) {

        var traced = laid.traced();
        var pockets = findTrappedPockets(laid, VoidPockets.PocketShaping.WITH_CHANNEL);

        if (pockets.isEmpty()) {
            System.out.println("the coast traps no void at all");
            return;
        }

        var spans = new ArrayList<Double>(pockets.size());

        for (var pocket : pockets) {
            spans.add(pocket.pocket().span());
        }
        spans.sort(Double::compare);

        reportEachEmptyPocket(pockets);

        var spills = findSpillsOf(laid, pockets);

        System.out.printf(
            Locale.ROOT,
            "closest a pocket comes to the reach that closed it: %.0f (the channel, %.0f)%n",
            CoastPocketFaults.measureClosestApproach(pockets, traced.union().sites()),
            laid.parameters().borderInset());

        System.out.printf(
            Locale.ROOT,
            "%d runs of pocket outline lie outside the drawn coast, worst by %.0f "
                + "(has to be 0)%n",
            spills.size(),
            spills.isEmpty() ? 0 : spills.get(0).depth());

        reportEachSpill(spills);

        System.out.printf(
            Locale.ROOT,
            "void the coast traps: %d pockets, %d of them drawn once the channel is taken "
                + "out; span p50 %.0f / p90 %.0f / max %.0f%n",
            pockets.size(),
            countDrawn(pockets),
            ReportFigures.findPercentile(spans, REPORTED_PERCENTILES[0]),
            ReportFigures.findPercentile(spans, REPORTED_PERCENTILES[1]),
            ReportFigures.findPercentile(spans, REPORTED_PERCENTILES[2]));

        reportTrappedVoidAtTrueExtent(laid);
    }

    // The same void with nothing given up. Reported beside the drawn shaping because the two
    // differ in what they can show: at their true extent the pockets with no room for a
    // channel still have an outline, so a count that falls between the two says the channel
    // closed something over rather than that anything went wrong.
    private static void reportTrappedVoidAtTrueExtent(LaidCoast laid) {

        var pockets = findTrappedPockets(laid, VoidPockets.PocketShaping.AT_TRUE_EXTENT);
        var spills = findSpillsOf(laid, pockets);

        System.out.printf(
            Locale.ROOT,
            "at their true extent: %d pockets, %d of them drawn, %d runs outside the drawn "
                + "coast, worst by %.0f (has to be 0)%n",
            pockets.size(),
            countDrawn(pockets),
            spills.size(),
            spills.isEmpty() ? 0 : spills.get(0).depth());

        reportEachSpill(spills);
        reportUndrawnVoid(laid);
    }

    // The coast's pockets at one shaping, with every site taken as unowned - a report about
    // the SHAPES, which move with a colouring if one is handed in.
    private static List<WalledPocket> findTrappedPockets(
            LaidCoast laid,
            VoidPockets.PocketShaping shaping) {

        return CoastPockets.findCoastPockets(
            laid.traced(),
            CoastPockets.markEverySiteUnowned(laid.sites()),
            new VoidPockets.PocketRules(laid.parameters(), ShippedMap.SECTION_RULES, shaping));
    }

    // Every run of pocket outline lying outside the coast that shut it in - judged against the
    // rings the map actually draws, which is the only line that answers the question.
    private static List<CoastPocketFaults.Spill> findSpillsOf(
            LaidCoast laid,
            List<WalledPocket> pockets) {

        return CoastPocketFaults.findSpills(
            pockets, Coastlines.collectCoastRings(laid.traced()));
    }

    // How many pockets have anything to draw, which the channel is what decides.
    private static int countDrawn(List<WalledPocket> pockets) {

        var drawn = 0;

        for (var walled : pockets) {

            if (!walled.pocket().outlines().isEmpty()) {
                drawn++;
            }
        }
        return drawn;
    }

    // Every pocket the channel left nothing to draw for, named rather than only counted. One
    // wider than the channel would be a hole in the map, so the span beside it is what says
    // whether the channel really closed it over.
    private static void reportEachEmptyPocket(List<WalledPocket> pockets) {

        for (var walled : pockets) {

            if (!walled.pocket().outlines().isEmpty()) {
                continue;
            }

            System.out.printf(
                Locale.ROOT,
                "  nothing left to draw for the pocket at %.0f,%.0f, %.0f across, "
                    + "walled by %d reaches%n",
                walled.pocket().centre()[0],
                walled.pocket().centre()[1],
                walled.pocket().span(),
                walled.reaches().size());
        }
    }

    // The patches of map that nothing draws, which is the fault a reader sees first and the
    // one no construction can report on its own.
    //
    // Asked of the shaping the viewer opens on, because that is the picture being complained
    // about: the bands a fill gives up against the coast and against the cells are taken out
    // of the question, so what is left is ground inside the coast that should have been
    // painted and was not.
    private static void reportUndrawnVoid(LaidCoast laid) {

        var unfilled = UndrawnVoid.findUnfilledVoid(
            laid, ShippedMap.SECTION_RULES, VoidPockets.PocketShaping.WITH_CHANNEL);

        System.out.printf(
            Locale.ROOT,
            "%d patches inside the coast that nothing draws (has to be 0)%n",
            unfilled.size());

        for (var patch : unfilled) {
            System.out.printf(Locale.ROOT, "  unfilled %s%n", patch);
        }
    }

    // Every spilling run named, since one is a case to look at and a count is not: where it
    // starts, how many points of outline are out there, and how far out the worst of them is.
    private static void reportEachSpill(List<CoastPocketFaults.Spill> spills) {

        for (var spill : spills) {
            System.out.printf(Locale.ROOT, "  spill %s%n", spill);
        }
    }

    // Each crossing as its depth beside one other number about it. Shared by every such
    // report, because what varies between them is which number is asked for, not how a list
    // of them reads.
    private static String formatAgainstDepth(List<double[]> rows) {

        var listed = new ArrayList<String>(rows.size());

        for (var row : rows) {
            listed.add(String.format(Locale.ROOT, "%.0f/%.0f", row[0], row[1]));
        }
        return ReportFigures.joinSpaced(listed, "none");
    }

    // Every one of them rather than a summary, because the question is whether they are one
    // population or two - a graze along a border the run is already leaving from, against a
    // run cutting a cell in half - and a mean or a worst case cannot tell those apart.
    private static String formatPenetrationDepths(List<CoastCrossings.Penetration> penetrations) {

        var depths = new ArrayList<Double>(penetrations.size());

        for (var penetration : penetrations) {
            depths.add(penetration.depth());
        }
        depths.sort(Comparator.reverseOrder());

        return ReportFigures.joinRounded(depths, "none");
    }
}
