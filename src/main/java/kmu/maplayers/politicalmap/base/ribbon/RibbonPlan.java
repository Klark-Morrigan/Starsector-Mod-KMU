package kmu.maplayers.politicalmap.base.ribbon;

import java.util.ArrayList;
import java.util.List;

/**
 * The banded stroke one cell draws inside its own ring, as the ordered runs it is made of:
 * every bloc present in the cell in the order it was ranked, each contributing a segment per
 * market it holds and an interjection between two of its own.
 *
 * <p>What the band says is how a system splits - how many colonies, whose, and in what
 * proportion - with no glyph and no label. One shade carries every parting in it. Markets of
 * one bloc are parted by an interjection in that bloc's dark shade, so a long run reads as
 * several holdings rather than as one large one; and where another bloc's run follows, that
 * same dark shade closes the outgoing run, so a handover is punctuated as well as recoloured.
 * The divider belongs to the bloc going out rather than to the one coming in, which is what
 * makes it read as that bloc's holdings ending rather than as an unowned gap between two
 * rivals. The band's last run is left open, a divider having nothing to part it from.
 *
 * <p>A cell draws a ribbon exactly when some bloc other than the one it was painted for is
 * present in it. Phrasing the gate on the painter rather than on "two or more blocs" is what
 * makes the sparse cases fall out right: a system claimed by decree whose decreed bloc holds
 * nothing there draws the one bloc that is present, alone, because that single bloc is still
 * something the fill does not already say. The converse is the gate's real work - a
 * single-holder system says nothing the fill has not said, and a large part of the sector is
 * single-holder systems, all of which stay bare.
 *
 * <p>The painting bloc draws in the ribbon like any other present bloc. Its own segments are
 * what give the rivals beside them a scale: a lone rival segment against a long run of the
 * holder means something quite different from the same segment against a short one.
 *
 * <p>Pure data over a ranking and a count each, with no geometry and no colour lookup of its
 * own, so the rule is settled on literals. Ranking order is the caller's: the runs come out
 * in the order the blocs were handed over, so the ribbon cannot disagree with the fill about
 * who leads a system it re-ranked for itself.
 *
 * @param segments the ribbon's runs in draw order, starting from the cell's top centre;
 *                 empty when the cell draws no ribbon at all
 */
public record RibbonPlan(
    List<RibbonSegment> segments) {

    // The cell draws no band: either nothing but the painter is present, or nothing is.
    public static final RibbonPlan NONE = new RibbonPlan(List.of());

    public RibbonPlan {
        segments = List.copyOf(segments);
    }

    /**
     * Plans one cell's ribbon from the blocs present in it, in the order they were ranked.
     *
     * @param paintingBlocId  the bloc the cell's fill was painted for, whose presence alone
     *                        is not worth a band; an id no listed bloc carries - a cell
     *                        painted for nobody - simply leaves every bloc a rival
     * @param rankedPresences the blocs present in the cell, already ranked as the fill was
     *                        decided, since the runs come out in exactly this order
     * @param lengths         how far a market's segment and an interjection run
     * @return the cell's runs in draw order, or {@link #NONE} where no bloc but the painter
     *         is present
     */
    public static RibbonPlan planCellRibbon(
            String paintingBlocId,
            List<BlocPresence> rankedPresences,
            RibbonSegmentLengths lengths) {

        if (!hasRivalPresence(paintingBlocId, rankedPresences)) {
            return NONE;
        }

        var segments = new ArrayList<RibbonSegment>();
        BlocPresence outgoingBloc = null;
        for (var presence : rankedPresences) {
            // A bloc holding nothing counted lays no run, so it opens no handover either: a
            // divider laid for it would part two blocs across a run that is not there, and
            // the bloc actually going out would go unclosed.
            if (presence.marketCount() <= 0) {
                continue;
            }
            if (outgoingBloc != null) {
                appendHandoverDivider(segments, outgoingBloc, lengths);
            }
            appendBlocRun(segments, presence, lengths);
            outgoingBloc = presence;
        }
        return new RibbonPlan(segments);
    }

    /**
     * How far the whole ribbon runs, in ribbon widths.
     *
     * <p>The budget the drawn size is settled against: a cell whose perimeter cannot hold
     * this many widths shrinks what a width is worth until it can, rather than wrapping the
     * band or cutting it short.
     *
     * @return the total length of every run, or zero where the cell draws no ribbon
     */
    public int sumLengthUnits() {

        var lengthUnits = 0;
        for (var segment : segments) {
            lengthUnits += segment.lengthUnits();
        }
        return lengthUnits;
    }

    // Whether any bloc but the painter holds something in the cell. A bloc counted at nothing
    // is not presence: it holds no market the score was decided on, so there is nothing about
    // it for a band to report, and admitting it would draw an empty ribbon on a cell that
    // deserves none.
    private static boolean hasRivalPresence(
            String paintingBlocId,
            List<BlocPresence> rankedPresences) {

        for (var presence : rankedPresences) {
            if (presence.marketCount() > 0 && !presence.blocId().equals(paintingBlocId)) {
                return true;
            }
        }
        return false;
    }

    // Lays down one bloc's run: a bright segment per market, parted by a dark interjection.
    // The interjection goes before every market after the first, which is what keeps it
    // strictly between two of one bloc's markets - so a run neither opens nor closes on one,
    // and whether it is closed off at all is the caller's to decide from what follows it.
    private static void appendBlocRun(
            List<RibbonSegment> segments,
            BlocPresence presence,
            RibbonSegmentLengths lengths) {

        var palette = presence.palette();
        for (var market = 0; market < presence.marketCount(); market++) {
            if (market > 0) {
                segments.add(new RibbonSegment(
                    palette.secondaryColour(),
                    lengths.interjectionLengthUnits()));
            }
            segments.add(new RibbonSegment(
                palette.primaryColour(),
                lengths.marketLengthUnits()));
        }
    }

    // Closes the run just laid, in the shade of the bloc that laid it, because another bloc's
    // run follows. Drawn in the outgoing bloc's own dark colour rather than in a neutral one
    // so the divider reads as that bloc's punctuation - its holdings ending - rather than as
    // a gap belonging to nobody between two rivals.
    //
    // The same length as the parting inside a run, off the same knob: two dividers of
    // different lengths in one band would look like a statement about the blocs they part
    // rather than like the boundary they are.
    private static void appendHandoverDivider(
            List<RibbonSegment> segments,
            BlocPresence outgoingBloc,
            RibbonSegmentLengths lengths) {

        segments.add(new RibbonSegment(
            outgoingBloc.palette().secondaryColour(),
            lengths.interjectionLengthUnits()));
    }
}
