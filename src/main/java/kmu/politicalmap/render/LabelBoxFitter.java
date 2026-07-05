package kmu.politicalmap.render;

import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.Spans;
import kmlib.math.solving.Bisection;
import kmlib.math.solving.Picks;

import kmu.politicalmap.render.model.ClusterAnchor;

/**
 * Sizes the largest label box that fits one candidate placement: the tallest font the
 * region holds, how many lines the name stacks into, and the clear line the box centres
 * on. The sizing half of the anchor search, apart from the candidate generation and
 * selection in {@link ClusterAnchorsBuilder} - one placement in, one box out.
 *
 * <p>The fit is a monotone growth on the per-line font height, clamped to
 * {@code [minFontHeight, maxFontHeight]} - the readability floor and the oversize
 * ceiling, both per line so they mean the same at any line count. A font of height
 * {@code F} stacked into {@code lineCount} lines occupies a band of thickness
 * {@code F * ((lineCount - 1) * lineSpacing + 1)}; the {@link NameLengthModel} says how
 * much length the name needs at that line height. A taller font fattens the band (which
 * fits in fewer places, so its clear length only shrinks) while the name's needed
 * length grows - the largest readable font is therefore at the largest height whose
 * band still holds the name, found by {@link Bisection}. Trying every line count and
 * keeping the tallest font is what lets a length-poor but girth-rich placement win by
 * stacking lines instead of shrinking; on a tie the lower line count holds
 * ({@link Picks} keeps the incumbent), so a name goes multi-line only when stacking
 * buys a strictly larger font.
 *
 * <p>The name model is injected, not baked in: the fitter never asks how long the name
 * actually is, only the model does, so the same fit serves the font-measured names and
 * the aspect stand-in alike.
 */
final class LabelBoxFitter {

    // How many times the font-height search halves its interval - enough to land the
    // fitted height within a fraction of a world unit, since each step doubles precision
    // and the font clamp spans a few thousand units at most.
    private static final int FONT_HEIGHT_BISECTION_STEPS = 20;

    private final double minFontHeight;
    private final double maxFontHeight;
    private final int maxLines;
    private final double lineSpacing;
    private final double iconClearance;
    private final double endInsetDistance;
    private final NameLengthModel nameLength;

    LabelBoxFitter(double minFontHeight, double maxFontHeight, int maxLines, double lineSpacing,
            double iconClearance, double endInsetDistance, NameLengthModel nameLength) {
        this.minFontHeight = minFontHeight;
        this.maxFontHeight = maxFontHeight;
        this.maxLines = maxLines;
        this.lineSpacing = lineSpacing;
        this.iconClearance = iconClearance;
        this.endInsetDistance = endInsetDistance;
        this.nameLength = nameLength;
    }

    // Sizes the largest name that fits the placement, as the box it occupies, or null
    // when even the minimum-height font cannot hold a name at any line count. Keeps
    // the line count whose box carries the tallest font, so more lines are chosen only
    // when they buy a strictly bigger font by spending the placement's spare girth.
    BoxFit fitLargestBox(Placement placement) {
        BoxFit best = null;
        for (var lineCount = 1; lineCount <= maxLines; lineCount++) {
            best = Picks.pickHigher(best, fitForLineCount(placement, lineCount),
                    BoxFit::fontHeight);
        }
        return best;
    }

    // Fits one candidate band against the rings, the icons, and the end inset, widened to
    // the given half thickness. Exposed for the search's near-miss diagnostic, which reads
    // the pre-margin clear span of a minimum-height band; the fit proper reaches it
    // through the line-count sizing below.
    BandSpan fitBand(Placement placement, double halfThickness) {
        var interiorSpans = Polygons.findBandInteriorSpans(placement.rings(),
                placement.throughX(), placement.throughY(), placement.direction()[0],
                placement.direction()[1], halfThickness);
        if (interiorSpans.isEmpty()) {
            return new BandSpan(null, null);
        }
        var clear = Spans.findLongestClearSubsegment(interiorSpans, placement.throughX(),
                placement.throughY(), placement.direction()[0], placement.direction()[1],
                placement.icons(), iconClearance);
        if (clear == null) {
            return new BandSpan(null, null);
        }
        var start = clear[0] + endInsetDistance;
        var end = clear[1] - endInsetDistance;
        // An interval shorter than twice the end inset leaves no room for a name between
        // the margins; only the pre-margin clear span survives, for the red diagnostic.
        return start < end ? new BandSpan(clear, new double[] {start, end})
                : new BandSpan(clear, null);
    }

    // Sizes the box for one fixed line count by growing the font to the largest height
    // whose band still holds the name, then reading that band's clear span back. Null
    // when even the minimum font cannot hold the name.
    private BoxFit fitForLineCount(Placement placement, int lineCount) {
        var linesFactor = (lineCount - 1) * lineSpacing + 1.0;
        if (!bandHoldsName(placement, minFontHeight, lineCount, linesFactor)) {
            return null;
        }
        var fontHeight = Bisection.findLargestPassing(minFontHeight, maxFontHeight,
                FONT_HEIGHT_BISECTION_STEPS,
                candidate -> bandHoldsName(placement, candidate, lineCount, linesFactor));
        var thickness = fontHeight * linesFactor;
        var span = fitBand(placement, thickness / 2.0).insetSpan();
        return new BoxFit(placement.toSegment(span), thickness, lineCount, fontHeight);
    }

    // Whether a font of the given height, stacked into lineCount lines, has room along
    // the placement for the name: the band those lines occupy must have a clear
    // (border-, icon-, and margin-trimmed) length at least the length the model says the
    // name needs at that line height.
    private boolean bandHoldsName(Placement placement, double fontHeight, int lineCount,
            double linesFactor) {
        var band = fitBand(placement, fontHeight * linesFactor / 2.0);
        if (band.insetSpan() == null) {
            return false;
        }
        var clearLength = band.insetSpan()[1] - band.insetSpan()[0];
        return clearLength >= nameLength.requiredLengthFor(fontHeight, lineCount);
    }

    /**
     * One band fit's spans, both along the candidate direction as {@code {tStart, tEnd}}:
     * the clear span is the roomiest border- and icon-clear stretch a band of the given
     * thickness has before the end margin (the near-miss the red diagnostic shows), and
     * the inset span the same pulled in by the end inset at both ends (the room a name
     * actually gets), or null when the margin leaves nothing. The clear span is null only
     * when the band finds no icon-clear interior at all.
     */
    record BandSpan(double[] clearSpan, double[] insetSpan) {
    }

    /**
     * One fitted label box: its clear span as a world segment (the line the anchor
     * carries), the band girth that fits along it, the line count the name is stacked
     * into, and the per-line font height the fit achieved - the quantity the search
     * maximises, before it docks steep candidates by the slope penalty.
     */
    record BoxFit(ClusterAnchor.AxisSegment segment, double thickness, int lineCount,
            double fontHeight) {
    }
}
