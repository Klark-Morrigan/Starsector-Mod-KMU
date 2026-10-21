package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The banded stroke one cell draws inside its own ring, as the ordered runs it is made of:
 * every owner present in the cell in the order it was ranked, each contributing a segment per
 * stake it has and an interjection between two of its own.
 *
 * <p>What the band says is how a system splits - how many stakes, whose, and in what
 * proportion - with no glyph and no label. One shade carries every parting in it. Stakes of
 * one owner are parted by an interjection in that owner's dark shade, so a long run reads as
 * several stakes rather than as one large one; and where another owner's run follows, that
 * same dark shade closes the outgoing run, so a handover is punctuated as well as recoloured.
 * The divider belongs to the owner going out rather than to the one coming in, which is what
 * makes it read as that owner's stakes ending rather than as an unowned gap between two
 * rivals. The band's last run is left open, a divider having nothing to part it from.
 *
 * <p>A cell draws a ribbon when anything is present in it at all. What contest adds is the length
 * its runs are laid at: a contested cell keeps the player's authored lengths, and an uncontested
 * one takes whatever {@link UncontestedRibbonRuns} answers. Contest is one question - does the
 * band say something its own fill does not - asked of the two kinds of fill a cell can have.
 *
 * <p>With a painter, that is any other owner holding something and not standing with the painter.
 * A cell painted for an owner that holds nothing in the system still draws the one owner present,
 * alone, because the fill names the painter and the band names somebody else. With no painter - a
 * cell no owner's fill covers - there is nobody to be a rival of, so
 * the reading falls to how many sides are in it: one side alone is a footprint nothing contradicts,
 * and two are a contest.
 *
 * <p>Sides rather than owners, because two allies jointly holding a system are not fighting over it,
 * and a band laid at contested length there would say they were. Who stands with whom is the
 * {@link OwnerAffiliation} the caller hands over and never the fold the presences were counted
 * under: allies keep their own runs in their own colours, and all that changes is the length those
 * runs are laid at. Where nothing groups owners the affiliation stands nobody together, and every
 * cell is judged owner by owner.
 *
 * <p>Contest decides the lengths and never whether there is a band, which is what keeps the one
 * band a map must not be able to lose - the one saying a system is contested - out of reach of
 * every knob. A cell with a rival in it bands because the fill cannot report a contest; a cell with
 * a lone owner bands because the fill says whose the system is and not how much is in it. A cell
 * nothing is present in draws under neither reading: there is no footprint to report.
 *
 * <p>The painting owner draws in the ribbon like any other present owner. Its own segments are
 * what give the rivals beside them a scale: a lone rival segment against a long run of the
 * painter means something quite different from the same segment against a short one.
 *
 * <p>Pure data over a ranking and a count each, with no geometry and no colour lookup of its
 * own, so the rule depends on nothing live. Ranking order is the caller's: the runs come out
 * in the order the owners were handed over, so the ribbon cannot disagree with the fill about
 * who leads a system it re-ranked for itself.
 *
 * <p>A value: two plans of the same runs are equal. A class rather than a record so a plan laid
 * here can keep the list it was laid into, where a plan built from a caller's list copies it.
 */
public final class RibbonPlan {

    // The cell draws no band: nothing is present in it, so there is no footprint to report.
    public static final RibbonPlan NONE = new RibbonPlan(List.of());

    private final List<RibbonSegment> segments;

    /**
     * A plan of the given runs, copied, so a caller reusing the list it built from cannot lengthen
     * a ribbon already planned.
     *
     * @param segments the ribbon's runs in draw order, starting from the cell's top centre; empty
     *                 when the cell draws no ribbon at all
     */
    public RibbonPlan(List<RibbonSegment> segments) {
        this.segments = List.copyOf(segments);
    }

    // A plan over runs laid here, kept rather than copied: the list was built for this plan alone
    // and goes nowhere else, so a copy would guard against nobody. Reached through
    // adoptLaidSegments, which names what sets it apart from the public constructor.
    private RibbonPlan(ArrayList<RibbonSegment> laidSegments) {
        this.segments = Collections.unmodifiableList(laidSegments);
    }

    /**
     * Plans one cell's ribbon from the owners present in it, in the order they were ranked.
     *
     * @param paintingOwnerId the owner the cell's fill was painted for, whose presence alone is a
     *                        footprint rather than a contest; empty where no owner's fill covers
     *                        the cell, which is a case of its own rather than a painter every owner
     *                        happens to differ from
     * @param rankedPresences the owners present in the cell, already ranked as the fill was
     *                        decided, since the runs come out in exactly this order
     * @param affiliation     who among those owners stands together, which is what keeps two allies
     *                        in one system from banding as though they fought over it;
     *                        {@link OwnerAffiliation#NONE} where nothing groups owners
     * @param rules           how a band is laid: the run lengths, and how far they reach on a cell
     *                        nobody contests
     * @return the cell's runs in draw order, or {@link #NONE} where the cell draws no band
     */
    public static RibbonPlan planCellRibbon(
            Optional<String> paintingOwnerId,
            List<OwnerPresence> rankedPresences,
            OwnerAffiliation affiliation,
            RibbonPlanRules rules) {

        var firstPresentOwnerId = findFirstPresentOwnerId(rankedPresences);

        // Who the cell is judged against: the owner its fill names, or - where no fill covers it -
        // whichever owner is there first, since with nobody painted the question is not who the
        // rivals are but whether the owners present fall into more than one side at all.
        var judgedAgainstOwnerId = paintingOwnerId.or(() -> firstPresentOwnerId);

        if (isCellContested(judgedAgainstOwnerId, rankedPresences, affiliation)) {
            return layOwnerRuns(rankedPresences, rules.lengths());
        }

        // Nothing the fill does not already say is here, so what is left to report is the size of
        // the footprint - and there has to be one, which a cell painted for an owner holding nothing
        // in the system, with nobody else there either, has not.
        if (firstPresentOwnerId.isEmpty()) {
            return NONE;
        }
        return layOwnerRuns(rankedPresences, rules.resolveUncontestedLengths());
    }

    /**
     * @return the ribbon's runs in draw order, starting from the cell's top centre; empty when the
     *         cell draws no ribbon at all
     */
    public List<RibbonSegment> segments() {
        return segments;
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

    @Override
    public boolean equals(Object other) {
        return other instanceof RibbonPlan plan && segments.equals(plan.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return "RibbonPlan[segments=" + segments + "]";
    }

    // The plan over a list laid by this class, kept as it is.
    private static RibbonPlan adoptLaidSegments(ArrayList<RibbonSegment> laidSegments) {
        return new RibbonPlan(laidSegments);
    }

    // Lays the whole band: every owner's run in the order handed over, each closed off by its own
    // dark shade wherever another owner's run follows it.
    //
    // Reached from both readings and given its lengths rather than choosing them, since all the
    // two differ on is how long the runs are - not what a band is made of. A second copy for the
    // uncontested cells would let their bands drift into a different shape from the contested ones,
    // which is the one comparison the readout rests on.
    private static RibbonPlan layOwnerRuns(
            List<OwnerPresence> rankedPresences,
            RibbonSegmentLengths lengths) {

        var segments = new ArrayList<RibbonSegment>();
        OwnerPresence outgoingOwner = null;

        for (var presence : rankedPresences) {
            // An owner holding nothing counted lays no run, so it opens no handover either: a
            // divider laid for it would part two owners across a run that is not there, and
            // the owner actually going out would go unclosed.
            if (!presence.hasStakes()) {
                continue;
            }
            // The divider closing the run just laid is the outgoing owner's own, not the
            // incoming one's: it is that owner's stakes ending, and drawn in the newcomer's
            // shade it would read as a gap belonging to whoever arrives next.
            if (outgoingOwner != null) {
                segments.add(createParting(outgoingOwner.palette(), lengths));
            }
            appendOwnerRun(segments, presence, lengths);
            outgoingOwner = presence;
        }
        return adoptLaidSegments(segments);
    }

    // Whether the band would say anything the cell's own fill does not, which is what decides the
    // lengths its runs are laid at: somebody holding something in the cell stands apart from the
    // owner it is judged against.
    //
    // One reading serves both kinds of cell because an affiliation is a fold. Owners that all stand
    // with one owner all stand with each other, so asking every owner about the one it is judged
    // against settles whether there is a second side, and on a painted cell that same question is
    // already the one worth asking: is anybody here a rival of the owner named by the fill.
    //
    // Empty only where the cell has neither a painter nor an owner in it, which is nothing to report
    // rather than a contest. A cell no fill covers is anchored on an owner genuinely present rather
    // than on an ID no owner carries: that sentinel leaves every owner a rival, so a lone haven no
    // fill covers would band at contested length as though it were fought over.
    private static boolean isCellContested(
            Optional<String> judgedAgainstOwnerId,
            List<OwnerPresence> rankedPresences,
            OwnerAffiliation affiliation) {

        return judgedAgainstOwnerId
            .map(ownerId -> hasRivalPresence(ownerId, rankedPresences, affiliation))
            .orElse(false);
    }

    // Whether any owner holding something in the cell is neither the judged owner itself nor standing
    // with it. An ally is not a rival: the two of them hold the system between them, and a fill
    // naming one of them says nothing the other contradicts.
    //
    // The judged owner drops out on its own ID, which is what lets an anchor drawn from the cell's
    // own presences ask about the rest without being counted as a side against itself.
    private static boolean hasRivalPresence(
            String judgedAgainstOwnerId,
            List<OwnerPresence> rankedPresences,
            OwnerAffiliation affiliation) {

        for (var presence : rankedPresences) {

            if (presence.hasStakes()
                    && !presence.ownerId().equals(judgedAgainstOwnerId)
                    && !affiliation.areOwnersAllied(judgedAgainstOwnerId, presence.ownerId())) {

                return true;
            }
        }
        return false;
    }

    // The first owner of the ranking that holds something in the cell, which answers two questions
    // at once: whether there is any footprint to report at all, and which owner a cell no fill
    // covers judges the rest against.
    //
    // Read over the owners that hold something rather than over the list, an owner ranked and holding
    // nothing being one a mechanic listed rather than one the cell has in it - a mechanic that
    // paints a system for an owner holding nothing there produces one. Anchored on such an owner, a
    // cell would be judged against somebody who is not in it.
    private static Optional<String> findFirstPresentOwnerId(List<OwnerPresence> rankedPresences) {

        for (var presence : rankedPresences) {
            if (presence.hasStakes()) {
                return Optional.of(presence.ownerId());
            }
        }
        return Optional.empty();
    }

    // Lays down one owner's run: a bright segment per stake, parted by a dark interjection.
    // The interjection goes before every stake after the first, which is what keeps it
    // strictly between two of one owner's stakes - so a run neither opens nor closes on one,
    // and whether it is closed off at all is the caller's to decide from what follows it.
    private static void appendOwnerRun(
            List<RibbonSegment> segments,
            OwnerPresence presence,
            RibbonSegmentLengths lengths) {

        var palette = presence.palette();

        for (var stake = 0; stake < presence.stakeCount(); stake++) {

            if (stake > 0) {
                segments.add(createParting(palette, lengths));
            }
            segments.add(new RibbonSegment(
                palette.primaryColour(),
                lengths.stakeLengthUnits()));
        }
    }

    // The band's one kind of boundary, made once for both the places that need it: between two
    // of an owner's stakes, and closing that owner's whole run where another owner's follows. Both
    // are the same owner's dark shade at the same length, and stating that twice would let one
    // boundary drift into saying more than the other - which would read as a statement about the
    // owners being parted rather than as the boundary it is.
    private static RibbonSegment createParting(
            ColourPair palette,
            RibbonSegmentLengths lengths) {

        return new RibbonSegment(
            palette.secondaryColour(),
            lengths.interjectionLengthUnits());
    }
}
