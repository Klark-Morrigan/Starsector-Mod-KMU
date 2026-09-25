package kmu.maplayers.ownermap.tooltip;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.layout.CellTooltipRows;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.Optional;

/**
 * The line naming why no faction runs anything in a hovered system: a colony whose government has
 * collapsed reads "Decivilised", a system with nobody in it at all "Unpopulated".
 *
 * <p>The two are different facts, which is why one line states which. A collapsed colony is still
 * populated - what it lacks is anybody the owner map can attribute the place to - so calling
 * its system unpopulated would be false, and saying nothing would leave a cell drawn as settled
 * with no account of who is there.
 *
 * <p>A shared row rather than each tooltip's own empty state, because it is a fact about the
 * system, not about the layer looking at it - a layer that says nothing else about such a system
 * still has to say that much, and two layers wording or placing it differently would read as two
 * different facts. The row resolves empty for a governed system, so a body can offer the status
 * unconditionally and let the system decide whether it appears.
 *
 * <p>It is a banner rather than an entry for the same reason: what the system <em>is</em> holds over
 * everything the box goes on to say about it, so it is centred across the box like any other banner
 * that may head it, and neither reads as the opening row of the breakdown below.
 */
public final class SystemStatusRow {

    // An emptiness has no mark to show, so the line is words alone. Named rather than passing a bare
    // null, so the call below reads as a line with no crest rather than as a crest that failed to
    // resolve.
    private static final String NO_CREST = null;

    private SystemStatusRow() {
    }

    /**
     * Resolves the status line for a system nobody the player knows of lives in.
     *
     * <p>Answered off habitation rather than off the listing beneath it, because the line is a
     * statement about the system and not a heading for the breakdown: it says whether people live
     * here, while the breakdown names everything the player may be told about. The two part over
     * the derelict, and the parting is the true reading rather than the disagreement it looks
     * like - a system holding one derelict and nothing else is unpopulated space, and the box
     * beneath still names it. The same holds the other way with a colony beside the derelict: the
     * line reads populated on the colony's account and the box names both.
     *
     * <p>The rule arrives from the caller rather than being read here, which is what keeps this in
     * step with the body below it: a status resolved under a rule of its own would eventually
     * withhold a colony the breakdown went on to name, or count one it did not.
     *
     * <p>Withholding a colony never leaks. A colony the rule holds back fails the projection
     * outright, so its system keeps its status line and the absence of one never becomes a tell
     * that something is hiding there.
     *
     * <p>The colonies arrive read rather than as a sector to walk, so a box that has already read
     * the system - which every box drawing this line has, the standings beside it being ranked off
     * that very walk - heads itself for the cost of a projection rather than of a second traversal.
     * A caller with no walk behind it selects one and hands it over, so the cost of the walk is
     * visible where it is paid.
     *
     * <p>The collapse comes out of that same projection rather than off a walk of the system's
     * planets. A collapsed colony is one of the colonies habitation admits, so the line and the
     * breakdown beneath it are answered from one reading - where two readings could have headed a
     * system Decivilised over a box that named no such colony in it.
     *
     * @param colonies        the hovered system's colonies, as one walk of it reported
     * @param colonyKnowledge what the player may be told about a colony, passed by the caller so
     *                        the status agrees with whatever that caller's own reads admit
     * @return the Decivilised or Unpopulated row, or empty when the player knows of a governed
     *         colony here
     */
    public static Optional<TooltipRow.CentredRow> resolveStatusRow(
            Colonies colonies,
            ColonyKnowledge colonyKnowledge) {

        var inhabitingColonies = colonyKnowledge.readInhabitingColonies(colonies);

        // Habitation admits the collapsed colony along with the governed ones, so its emptiness is
        // not the question here: a system holding only a collapse is inhabited and still has nobody
        // running it.
        if (hasGovernedColony(colonyKnowledge, inhabitingColonies)) {
            return Optional.empty();
        }
        // Emptiness settles the rest, no second read of the kinds needed: habitation admits the
        // governed colonies and the collapsed one and nothing else, so with no governed colony
        // found anything still in hand is a collapse, and nothing in hand is nobody at all.
        var statusKey = inhabitingColonies.isEmpty()
            ? KmuStringKeys.OWNER_MAP_TOOLTIP_UNPOPULATED
            : KmuStringKeys.OWNER_MAP_TOOLTIP_DECIVILISED;

        // Set across the box, crestless: the status qualifies the whole system rather than being one
        // entry of a list, so it is spoken for the box the way any banner above it is - laid in the
        // columns instead, it would read as the first row of a breakdown that has none.
        return Optional.of(CellTooltipRows.buildBannerRow(
            NO_CREST,
            KmuStringKeys.get(statusKey)));
    }

    // Whether anybody is running a colony here. Stated as the exclusion of the one kind that
    // inhabits its place under nobody's authority, so every other kind reads as governed - which is
    // the same direction habitation itself is written in, and the safer one: a line calling a
    // governed system unpopulated is worse than one declining to.
    private static boolean hasGovernedColony(
            ColonyKnowledge colonyKnowledge,
            List<Colony> inhabitingColonies) {

        for (var colony : inhabitingColonies) {
            if (colonyKnowledge.readKindOf(colony) != ColonyKind.UNGOVERNED_COLONY) {
                return true;
            }
        }
        return false;
    }
}
