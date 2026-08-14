package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

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
 * <p>A cell draws a ribbon when some bloc other than the one it was painted for is present in it,
 * or when the cell holds anything at all and {@link UncontestedCellBands} admits the uncontested
 * ones. Phrasing the first arm on the painter rather than on "two or more blocs" is what makes the
 * sparse cases fall out right: a system claimed by decree whose decreed bloc holds nothing there
 * draws the one bloc that is present, alone, because that single bloc is still something the fill
 * does not already say.
 *
 * <p>Two arms rather than one rewritten rule, because they answer different questions. A cell with
 * a rival in it bands because the fill cannot report a contest, which is what the whole readout is
 * for; a cell with a lone holder bands because the player asked to see footprints. Folded into a
 * single settings-dependent rule, the switch over the second would reach the first as well - and
 * the one band a map must not be able to lose is the one saying a system is contested. A cell
 * nothing is present in draws under neither arm: there is no footprint to report.
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
     * @param paintingBlocId    the bloc the cell's fill was painted for, whose presence alone is
     *                          worth a band only where the uncontested cells are admitted; an id
     *                          no listed bloc carries - a cell painted for nobody - simply leaves
     *                          every bloc a rival
     * @param rankedPresences   the blocs present in the cell, already ranked as the fill was
     *                          decided, since the runs come out in exactly this order
     * @param lengths           how far a market's segment and an interjection run
     * @param uncontestedBands  what a cell nobody but its painter holds anything in draws: whether
     *                          it bands at all, and at what run length
     * @return the cell's runs in draw order, or {@link #NONE} where the cell draws no band
     */
    public static RibbonPlan planCellRibbon(
            String paintingBlocId,
            List<BlocPresence> rankedPresences,
            RibbonSegmentLengths lengths,
            UncontestedCellBands uncontestedBands) {

        if (hasRivalPresence(paintingBlocId, rankedPresences)) {
            return layBlocRuns(rankedPresences, lengths);
        }
        // Nothing but the painter is here, so the band is the player's to ask for - and there has
        // to be something for it to report, which a decreed system its decreed bloc holds nothing
        // in has not.
        if (!uncontestedBands.isBandDrawn() || !hasAnyPresence(rankedPresences)) {
            return NONE;
        }
        return layBlocRuns(rankedPresences, uncontestedBands.resolveRunLengths(lengths));
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

    // Lays the whole band: every bloc's run in the order handed over, each closed off by its own
    // dark shade wherever another bloc's run follows it.
    //
    // Reached from both arms of the gate and given its lengths rather than choosing them, since
    // what the two arms differ on is which cells band and how long their runs are - not what a band
    // is made of. A second copy for the uncontested cells would let their bands drift into a
    // different shape from the contested ones, which is the one comparison the readout rests on.
    private static RibbonPlan layBlocRuns(
            List<BlocPresence> rankedPresences,
            RibbonSegmentLengths lengths) {

        var segments = new ArrayList<RibbonSegment>();
        BlocPresence outgoingBloc = null;
        for (var presence : rankedPresences) {
            // A bloc holding nothing counted lays no run, so it opens no handover either: a
            // divider laid for it would part two blocs across a run that is not there, and
            // the bloc actually going out would go unclosed.
            if (presence.marketCount() <= 0) {
                continue;
            }
            // The divider closing the run just laid is the outgoing bloc's own, not the
            // incoming one's: it is that bloc's holdings ending, and drawn in the newcomer's
            // shade it would read as a gap belonging to whoever arrives next.
            if (outgoingBloc != null) {
                segments.add(createParting(outgoingBloc.palette(), lengths));
            }
            appendBlocRun(segments, presence, lengths);
            outgoingBloc = presence;
        }
        return new RibbonPlan(segments);
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

    // Whether the cell holds anything at all, which is what the second arm of the gate is stated
    // over: a footprint is what it offers to report, and a cell whose painter is there by decree
    // alone has none - so admitting it would lay a band of no runs on a system holding nothing.
    private static boolean hasAnyPresence(List<BlocPresence> rankedPresences) {

        for (var presence : rankedPresences) {
            if (presence.marketCount() > 0) {
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
                segments.add(createParting(palette, lengths));
            }
            segments.add(new RibbonSegment(
                palette.primaryColour(),
                lengths.marketLengthUnits()));
        }
    }

    // The band's one kind of boundary, made once for both the places that need it: between two
    // of a bloc's markets, and closing that bloc's whole run where another bloc's follows. Both
    // are the same bloc's dark shade at the same length, and stating that twice would let a
    // later edit make one boundary say more than the other - which the design reads as a claim
    // about the blocs being parted rather than as the boundary it is.
    private static RibbonSegment createParting(
            FactionPalette palette,
            RibbonSegmentLengths lengths) {

        return new RibbonSegment(
            palette.secondaryColour(),
            lengths.interjectionLengthUnits());
    }
}
