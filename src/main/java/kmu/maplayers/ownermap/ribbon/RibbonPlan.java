package kmu.maplayers.ownermap.ribbon;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.ownermap.holding.BlocAffiliation;

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
 * <p>A cell draws a ribbon when anything is present in it at all. What contest adds is the length
 * its runs are laid at: a contested cell keeps the player's authored lengths, and an uncontested
 * one takes whatever {@link UncontestedRibbonRuns} answers. Contest is one question - does the
 * band say something its own fill does not - asked of the two kinds of fill a cell can have.
 *
 * <p>With a painter, that is any other bloc holding something and not standing with the painter.
 * A cell painted for a bloc that holds nothing in the system still draws the one bloc present,
 * alone, because the fill names the painter and the band names somebody else. With no painter - a
 * cell no bloc's fill covers - there is nobody to be a rival of, so
 * the reading falls to how many sides are in it: one side alone is a footprint nothing contradicts,
 * and two are a contest.
 *
 * <p>Sides rather than blocs, because two allies jointly holding a system are not fighting over it,
 * and a band laid at contested length there would say they were. Who stands with whom is the
 * {@link BlocAffiliation} the caller hands over and never the fold the presences were counted
 * under: allies keep their own runs in their own colours, and all that changes is the length those
 * runs are laid at. Where nothing groups factions the affiliation stands nobody together, and every
 * cell is judged bloc by bloc.
 *
 * <p>Contest decides the lengths and never whether there is a band, which is what keeps the one
 * band a map must not be able to lose - the one saying a system is contested - out of reach of
 * every knob. A cell with a rival in it bands because the fill cannot report a contest; a cell with
 * a lone holder bands because the fill says whose the system is and not how much is in it. A cell
 * nothing is present in draws under neither reading: there is no footprint to report.
 *
 * <p>The painting bloc draws in the ribbon like any other present bloc. Its own segments are
 * what give the rivals beside them a scale: a lone rival segment against a long run of the
 * holder means something quite different from the same segment against a short one.
 *
 * <p>Pure data over a ranking and a count each, with no geometry and no colour lookup of its
 * own, so the rule depends on nothing live. Ranking order is the caller's: the runs come out
 * in the order the blocs were handed over, so the ribbon cannot disagree with the fill about
 * who leads a system it re-ranked for itself.
 *
 * @param segments the ribbon's runs in draw order, starting from the cell's top centre;
 *                 empty when the cell draws no ribbon at all
 */
public record RibbonPlan(
    List<RibbonSegment> segments) {

    // The cell draws no band: nothing is present in it, so there is no footprint to report.
    public static final RibbonPlan NONE = new RibbonPlan(List.of());

    public RibbonPlan {
        segments = List.copyOf(segments);
    }

    /**
     * Plans one cell's ribbon from the blocs present in it, in the order they were ranked.
     *
     * @param paintingBlocId  the bloc the cell's fill was painted for, whose presence alone is a
     *                        footprint rather than a contest; empty where no bloc's fill covers
     *                        the cell, which is a case of its own rather than a painter every bloc
     *                        happens to differ from
     * @param rankedPresences the blocs present in the cell, already ranked as the fill was
     *                        decided, since the runs come out in exactly this order
     * @param affiliation     who among those blocs stands together, which is what keeps two allies
     *                        in one system from banding as though they fought over it;
     *                        {@link BlocAffiliation#NONE} where nothing groups factions
     * @param rules           how a band is laid: the run lengths, and how far they reach on a cell
     *                        nobody contests
     * @return the cell's runs in draw order, or {@link #NONE} where the cell draws no band
     */
    public static RibbonPlan planCellRibbon(
            Optional<String> paintingBlocId,
            List<BlocPresence> rankedPresences,
            BlocAffiliation affiliation,
            RibbonPlanRules rules) {

        var firstPresentBlocId = findFirstPresentBlocId(rankedPresences);

        // Who the cell is judged against: the bloc its fill names, or - where no fill covers it -
        // whichever bloc is there first, since with nobody painted the question is not who the
        // rivals are but whether the blocs present fall into more than one side at all.
        var judgedAgainstBlocId = paintingBlocId.or(() -> firstPresentBlocId);

        if (isCellContested(judgedAgainstBlocId, rankedPresences, affiliation)) {
            return layBlocRuns(rankedPresences, rules.lengths());
        }

        // Nothing the fill does not already say is here, so what is left to report is the size of
        // the footprint - and there has to be one, which a cell painted for a bloc holding nothing
        // in the system, with nobody else there either, has not.
        if (firstPresentBlocId.isEmpty()) {
            return NONE;
        }
        return layBlocRuns(rankedPresences, rules.resolveUncontestedLengths());
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
    // Reached from both readings and given its lengths rather than choosing them, since all the
    // two differ on is how long the runs are - not what a band is made of. A second copy for the
    // uncontested cells would let their bands drift into a different shape from the contested ones,
    // which is the one comparison the readout rests on.
    private static RibbonPlan layBlocRuns(
            List<BlocPresence> rankedPresences,
            RibbonSegmentLengths lengths) {

        var segments = new ArrayList<RibbonSegment>();
        BlocPresence outgoingBloc = null;

        for (var presence : rankedPresences) {
            // A bloc holding nothing counted lays no run, so it opens no handover either: a
            // divider laid for it would part two blocs across a run that is not there, and
            // the bloc actually going out would go unclosed.
            if (!presence.hasMarkets()) {
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

    // Whether the band would say anything the cell's own fill does not, which is what decides the
    // lengths its runs are laid at: somebody holding something in the cell stands apart from the
    // bloc it is judged against.
    //
    // One reading serves both kinds of cell because an affiliation is a fold. Blocs that all stand
    // with one bloc all stand with each other, so asking every bloc about the one it is judged
    // against settles whether there is a second side, and on a painted cell that same question is
    // already the one worth asking: is anybody here a rival of the bloc named by the fill.
    //
    // Empty only where the cell has neither a painter nor a bloc in it, which is nothing to report
    // rather than a contest. A cell no fill covers is anchored on a bloc genuinely present rather
    // than on an ID no bloc carries: that sentinel leaves every bloc a rival, so a lone haven no
    // fill covers would band at contested length as though it were fought over.
    private static boolean isCellContested(
            Optional<String> judgedAgainstBlocId,
            List<BlocPresence> rankedPresences,
            BlocAffiliation affiliation) {

        return judgedAgainstBlocId
            .map(blocId -> hasRivalPresence(blocId, rankedPresences, affiliation))
            .orElse(false);
    }

    // Whether any bloc holding something in the cell is neither the judged bloc itself nor standing
    // with it. An ally is not a rival: the two of them hold the system between them, and a fill
    // naming one of them says nothing the other contradicts.
    //
    // The judged bloc drops out on its own ID, which is what lets an anchor drawn from the cell's
    // own presences ask about the rest without being counted as a side against itself.
    private static boolean hasRivalPresence(
            String judgedAgainstBlocId,
            List<BlocPresence> rankedPresences,
            BlocAffiliation affiliation) {

        for (var presence : rankedPresences) {

            if (presence.hasMarkets()
                    && !presence.blocId().equals(judgedAgainstBlocId)
                    && !affiliation.areBlocsAllied(judgedAgainstBlocId, presence.blocId())) {

                return true;
            }
        }
        return false;
    }

    // The first bloc of the ranking that holds something in the cell, which answers two questions
    // at once: whether there is any footprint to report at all, and which bloc a cell no fill
    // covers judges the rest against.
    //
    // Read over the blocs that hold something rather than over the list, a bloc ranked and holding
    // nothing being one a mechanic listed rather than one the cell has in it - a mechanic that
    // paints a system for a bloc holding nothing there produces one. Anchored on such a bloc, a
    // cell would be judged against somebody who is not in it.
    private static Optional<String> findFirstPresentBlocId(List<BlocPresence> rankedPresences) {

        for (var presence : rankedPresences) {
            if (presence.hasMarkets()) {
                return Optional.of(presence.blocId());
            }
        }
        return Optional.empty();
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
    // are the same bloc's dark shade at the same length, and stating that twice would let one
    // boundary drift into saying more than the other - which would read as a statement about the
    // blocs being parted rather than as the boundary it is.
    private static RibbonSegment createParting(
            FactionPalette palette,
            RibbonSegmentLengths lengths) {

        return new RibbonSegment(
            palette.secondaryColour(),
            lengths.interjectionLengthUnits());
    }
}
