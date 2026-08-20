package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How the void divides into places, and how the rule that divides it was settled.
 *
 * <p>A pocket the size of a corridor is not a place, and one the size of a sector is not one
 * either. What is reported here is the whole of that argument: each pocket as the sections it
 * came out as, and a sweep of the threshold across its range so the setting the viewer opens
 * on is a choice with the alternatives printed beside it.
 *
 * <p>Its own class because it answers a different question from the fault reports next door.
 * Those ask whether a shape is right; this one asks whether the division is a good one, which
 * is a matter of judgement and needs the population printed rather than a number that has to
 * be zero.
 */
final class VoidDivisionReport {

    private static final double PERCENT_SCALE = 100.0;

    // Shares to sweep the division across, so the knob has a starting range instead of being
    // a bare slider. Spread over the whole span rather than clustered near the default,
    // because both ends of it are wrong in a different way and seeing where each one sets in
    // is the point.
    private static final double[] SWEPT_SHARES = {0, 0.2, 0.4, 0.6, 0.8, 1.0};

    private VoidDivisionReport() {
    }

    // Every pocket, one line each. A summary count says how many pockets came out one way
    // or another; it cannot say WHICH, and every question worth asking of this map so far has
    // turned out to be about a particular pocket.
    static void reportEachPocket(List<VoidPockets.VoidPocket> pockets) {

        System.out.println(
            "  pocket        at          span  cells  cuts   shaping     "
                + "sections            cut widths");

        for (var index = 0; index < pockets.size(); index++) {

            var pocket = pockets.get(index);
            var centre = pocket.centre();

            System.out.printf(
                Locale.ROOT,
                "  %-6d %7.0f,%-7.0f %6.0f %4d %5d   %-10s  %-18s  %s%n",
                index,
                centre[0],
                centre[1],
                pocket.span(),
                pocket.adjacentCells().size(),
                pocket.division().cuts().size(),
                describeShaping(pocket),
                describeSections(pocket),
                describeCutWidths(pocket));
        }
    }

    // The longest way across any one of a pocket's sections. Sections come back largest by
    // area, which is what they are chosen by, and largest by area is not always longest.
    private static double measureLongestSection(VoidPockets.VoidPocket pocket) {

        var longest = 0.0;

        for (var section : pocket.division().sections()) {
            longest = Math.max(longest, VoidSections.measureWidestSpan(section));
        }
        return longest;
    }

    // Every section's span, largest section first. The shape of the list is the answer: a run of
    // similar numbers is an even division, and one large number followed by small ones is a
    // pocket that had slivers taken off it rather than being divided.
    private static String describeSections(VoidPockets.VoidPocket pocket) {

        var spans = new ArrayList<Double>();

        for (var section : pocket.division().sections()) {
            spans.add(VoidSections.measureWidestSpan(section));
        }
        return ReportFigures.joinRounded(spans, "");
    }

    // How wide the corridor is at each cut. The number that says whether a cut is a pinch
    // or a jump: a cut across a genuine neck is a small fraction of a section, and one that
    // reads as leaping across open void is a large one.
    private static String describeCutWidths(VoidPockets.VoidPocket pocket) {

        var widths = new ArrayList<Double>();

        for (var cut : pocket.division().cuts()) {
            widths.add(cut.width());
        }
        return ReportFigures.joinRounded(widths, "");
    }

    // What became of one pocket, in two words: who absorbed it, and what there was left to
    // draw. A pocket with no outline is a mark on the map rather than a shape, and one drawn
    // in more than one piece pinched in two when the channel came out of it.
    private static String describeShaping(VoidPockets.VoidPocket pocket) {

        var drawn = pocket.outlines().isEmpty()
            ? "mark"
            : "x" + pocket.outlines().size();

        return (pocket.absorbingOwner() != null ? "owned/" : "void/") + drawn;
    }

    // Whether the division actually divides. A pocket is cut until nothing in it is longer
    // than a section, so the count of cuts says nothing on its own - what matters is what is
    // left. A section still over length is a piece the cells offered nowhere to cut, which is
    // the one failure this construction can have.
    static void reportSectioning(List<VoidPockets.VoidPocket> pockets) {

        var summary = summariseDivision(pockets);

        System.out.printf(
            Locale.ROOT,
            "%d pockets want dividing into sections of %.0f, no cut leaving under %.0f%% of "
                + "one: %d cuts taken, %d still hold a section over length, longest %.0f%n",
            summary.toDivide(),
            ShippedMap.SECTION_LENGTH,
            ShippedMap.MIN_SECTION_SHARE * PERCENT_SCALE,
            summary.cuts(),
            summary.overLength(),
            summary.longestSection());
    }

    // How the division answers to the one knob that decides it. Three numbers say the whole
    // story: how many cuts were taken, how wide the worst of them was, and how long the worst
    // section left over was. A low share takes many narrow cuts and still leaves one huge
    // piece, because it is shaving the tips; a high share takes few wide ones, because
    // nothing but a chord across the open middle can leave that much on both sides. Where
    // those two failures stop overlapping is where the knob wants to sit.
    static void reportShareSweep(SectorFixture fixture) {

        System.out.println("  share   cuts   widest cut   longest section");

        for (var share : SWEPT_SHARES) {

            var summary = summariseDivision(VoidPockets.findVoidPockets(
                fixture.getSites(),
                fixture.getOwnerBySite(),
                new VoidPockets.PocketRules(
                    ShippedMap.KNOBS,
                    new VoidSections.SectionRules(ShippedMap.SECTION_LENGTH, share),
                    VoidPockets.PocketShaping.WITH_CHANNEL)));

            System.out.printf(
                Locale.ROOT,
                "  %4.0f%%  %5d   %10.0f   %15.0f%n",
                share * PERCENT_SCALE,
                summary.cuts(),
                summary.widestCut(),
                summary.longestSection());
        }
    }

    // One walk over the pockets that want dividing, for both the line above and every row of
    // the sweep. Shared rather than written twice because the sweep's row at the share the
    // rest of the report runs at IS that line, and two walks could report it two ways.
    private static DivisionSummary summariseDivision(List<VoidPockets.VoidPocket> pockets) {

        var toDivide = 0;
        var cuts = 0;
        var overLength = 0;
        var widestCut = 0.0;
        var longestSection = 0.0;

        for (var pocket : pockets) {

            if (pocket.span() <= ShippedMap.SECTION_LENGTH) {
                continue;
            }

            toDivide++;
            cuts += pocket.division().cuts().size();

            for (var cut : pocket.division().cuts()) {
                widestCut = Math.max(widestCut, cut.width());
            }

            var longestHere = measureLongestSection(pocket);

            if (longestHere > ShippedMap.SECTION_LENGTH) {
                overLength++;
            }

            longestSection = Math.max(longestSection, longestHere);
        }
        return new DivisionSummary(toDivide, cuts, overLength, widestCut, longestSection);
    }

    /**
     * What one run of the division came to, across every pocket long enough to want it.
     *
     * @param toDivide       how many pockets spanned more than a section
     * @param cuts           how many cuts were taken across all of them
     * @param overLength     how many still hold a section longer than one
     * @param widestCut      the widest corridor any cut crossed - the number that says
     *                       whether cuts landed at pinches or were thrown across open void
     * @param longestSection the longest way across any section left
     */
    private record DivisionSummary(
        int toDivide,
        int cuts,
        int overLength,
        double widestCut,
        double longestSection) {
    }
}
