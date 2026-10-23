package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonPlanBuilder;
import kmu.maplayers.base.ribbon.RibbonSegment;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;

import java.util.List;
import java.util.Optional;

/**
 * Lays an owner-painted cell's band: every owner present in the cell in the order it was ranked,
 * each contributing a segment per stake it has and an interjection between two of its own.
 *
 * <p>What the band says is how a system splits - how many stakes, whose, and in what proportion -
 * with no glyph and no label, so one shade carries every parting in it. The grammar, and why a
 * divider belongs to the owner going out, is <a href="README.md#laying-the-runs">the runs register</a>'s.
 *
 * <p>Contest decides the lengths and never whether there is a band, which is what keeps the one
 * band a map must not be able to lose - the one saying a system is contested - out of reach of
 * every knob. What contested means with a painter and without one, and why two allies are one side,
 * is <a href="README.md#which-cells-band-and-how-loudly">the same register</a>'s.
 *
 * <p>The painting owner draws in the ribbon like any other present owner. Its own segments are
 * what give the rivals beside them a scale: a lone rival segment against a long run of the
 * painter means something quite different from the same segment against a short one.
 *
 * <p>Pure over a ranking and a count each, with no geometry and no colour lookup of its own, so
 * the rule depends on nothing live. Ranking order is the caller's: the runs come out in the order
 * the owners were handed over, so the ribbon cannot disagree with the fill about who leads a
 * system it re-ranked for itself.
 *
 * <p>The owner tier's own rule rather than the band's: the plan it produces is
 * {@code base.ribbon}'s, and a layer banding a cell by anything but its owners lays its runs by a
 * rule of its own.
 */
public final class OwnerRibbonRuns {

    // Lays only; never instantiated.
    private OwnerRibbonRuns() {
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
     * @return the cell's runs in draw order, or {@link RibbonPlan#NONE} where the cell draws no band
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
            return layOwnerRuns(rankedPresences, rules.getContestedLengths());
        }

        // Nothing the fill does not already say is here, so what is left to report is the size of
        // the footprint - and there has to be one, which a cell painted for an owner holding nothing
        // in the system, with nobody else there either, has not.
        if (firstPresentOwnerId.isEmpty()) {
            return RibbonPlan.NONE;
        }
        return layOwnerRuns(rankedPresences, rules.getUncontestedLengths());
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

        var planBuilder = new RibbonPlanBuilder();
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
                planBuilder.appendSegment(createParting(outgoingOwner.palette(), lengths));
            }
            appendOwnerRun(planBuilder, presence, lengths);
            outgoingOwner = presence;
        }
        return planBuilder.buildPlan();
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
            RibbonPlanBuilder planBuilder,
            OwnerPresence presence,
            RibbonSegmentLengths lengths) {

        var palette = presence.palette();

        for (var stake = 0; stake < presence.stakeCount(); stake++) {

            if (stake > 0) {
                planBuilder.appendSegment(createParting(palette, lengths));
            }
            planBuilder.appendSegment(new RibbonSegment(
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
