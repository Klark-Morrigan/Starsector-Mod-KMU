package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * <p>A cell draws a ribbon when it is contested, or when it holds anything at all and
 * {@link UncontestedCellBands} admits the uncontested ones. What makes a cell contested is one
 * question - does the band say something its own fill does not - asked of the two kinds of fill a
 * cell can have.
 *
 * <p>With a painter, that is any other bloc holding something. A system claimed by decree whose
 * decreed bloc holds nothing there still draws the one bloc present, alone, because the fill names
 * the decreed bloc and the band names somebody else. With no painter - an unclaimed cell, which no
 * bloc's fill covers - there is nobody to be a rival of, so the reading falls to the count: one
 * bloc alone is a footprint nothing contradicts, and two are a contest.
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

    // The cell draws no band: nothing is present in it, or nothing contests it - its painter alone
    // is there, or a lone bloc is and no fill names anyone - and the uncontested cells are not
    // admitted.
    public static final RibbonPlan NONE = new RibbonPlan(List.of());

    // What makes a painterless cell contested. With no fill naming anyone, a lone bloc is a
    // footprint nothing already said rather than a rivalry, so it takes a second bloc.
    private static final int CONTESTING_BLOC_COUNT = 2;

    // A cell no bloc holds anything in, which has no footprint to report under either arm.
    private static final int NO_BLOCS = 0;

    public RibbonPlan {
        segments = List.copyOf(segments);
    }

    /**
     * Plans one cell's ribbon from the blocs present in it, in the order they were ranked.
     *
     * @param paintingBlocId  the bloc the cell's fill was painted for, whose presence alone is
     *                        worth a band only where the uncontested cells are admitted; empty
     *                        where no bloc's fill covers the cell, which is a case of its own
     *                        rather than a painter every bloc happens to differ from
     * @param rankedPresences the blocs present in the cell, already ranked as the fill was
     *                        decided, since the runs come out in exactly this order
     * @param rules           how a band is laid: the run lengths, and what a cell nobody
     *                        contests draws
     * @return the cell's runs in draw order, or {@link #NONE} where the cell draws no band
     */
    public static RibbonPlan planCellRibbon(
            Optional<String> paintingBlocId,
            List<BlocPresence> rankedPresences,
            RibbonPlanRules rules) {

        if (isCellContested(paintingBlocId, rankedPresences)) {
            return layBlocRuns(rankedPresences, rules.lengths());
        }
        var uncontestedBands = rules.uncontestedBands();

        // Nothing the fill does not already say is here, so the band is the player's to ask for -
        // and there has to be something for it to report, which a decreed system its decreed bloc
        // holds nothing in has not.
        if (!uncontestedBands.isBandDrawn()
                || countPresentBlocs(rankedPresences) == NO_BLOCS) {
            return NONE;
        }
        return layBlocRuns(
            rankedPresences,
            uncontestedBands.resolveRunLengths(rules.lengths()));
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
            if (!presence.holdsSomething()) {
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

    // Whether the band would say anything the cell's own fill does not, which is the first arm
    // of the gate.
    //
    // Branched on whether there is a painter at all rather than letting an absent one stand in as
    // an id no bloc carries: that sentinel leaves every bloc a rival, so a lone haven no fill
    // covers would band at contested length as though it were fought over.
    private static boolean isCellContested(
            Optional<String> paintingBlocId,
            List<BlocPresence> rankedPresences) {

        return paintingBlocId
            .map(blocId -> hasRivalPresence(blocId, rankedPresences))
            .orElseGet(() -> countPresentBlocs(rankedPresences) >= CONTESTING_BLOC_COUNT);
    }

    // Whether any bloc but the painter holds something in the cell.
    private static boolean hasRivalPresence(
            String paintingBlocId,
            List<BlocPresence> rankedPresences) {

        for (var presence : rankedPresences) {
            if (presence.holdsSomething() && !presence.blocId().equals(paintingBlocId)) {
                return true;
            }
        }
        return false;
    }

    // How many blocs hold something in the cell, which both the painterless contest and the second
    // arm of the gate are read off. Counted over the blocs that hold something rather than over the
    // list, a bloc ranked and holding nothing being one a mechanic listed rather than one the cell
    // has in it - a decreed system its decreed bloc holds nothing in being the case that produces
    // one.
    private static int countPresentBlocs(List<BlocPresence> rankedPresences) {

        var presentBlocs = 0;

        for (var presence : rankedPresences) {
            if (presence.holdsSomething()) {
                presentBlocs++;
            }
        }
        return presentBlocs;
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
