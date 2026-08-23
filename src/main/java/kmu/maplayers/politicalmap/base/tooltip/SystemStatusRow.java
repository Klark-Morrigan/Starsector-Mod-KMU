package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.Colony;
import kmlib.starsector.colonies.ColonyKind;
import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.util.KmuStrings;

import java.util.List;
import java.util.Optional;

/**
 * The line naming why nobody lives in a hovered system: a dead colony the player has already seen
 * reads "Decivilised", any other system nobody lives in "Unpopulated".
 *
 * <p>A shared row rather than each tooltip's own empty state, because the emptiness is a fact about
 * the system, not about the layer looking at it - a layer that says nothing else about a dead system
 * still has to say that much, and two layers wording or placing it differently would read as two
 * different facts. The row resolves empty for a populated system, so a body can offer the status
 * unconditionally and let the system decide whether it appears.
 *
 * <p>It is a banner rather than an entry for the same reason: what the system <em>is</em> holds over
 * everything the box goes on to say about it, so it is centred across the box like the decree that may
 * head it, and neither reads as the opening row of the breakdown below.
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
     * like - a system holding one hulk and nothing else is unpopulated space, and the box beneath
     * still names the hulk. The same holds the other way with a colony beside the hulk: the line
     * reads populated on the colony's account and the box names both.
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
     * A caller with no walk behind it selects one and hands it over, which is the same work it was
     * paying before and is now visible where it is paid.
     *
     * <p>The ruin comes out of that same projection rather than off a walk of the system's planets.
     * A dead world is one of the colonies habitation admits, so the line and the breakdown beneath
     * it are answered from one reading - where two readings could have headed a system Decivilised
     * over a box that named nothing dead in it.
     *
     * @param colonies         the hovered system's colonies, as one walk of it reported
     * @param colonyVisibility what the player may be shown of a colony, passed by the caller so
     *                         the status agrees with whatever that caller's own reads admit
     * @return the Decivilised or Unpopulated row, or empty when the player knows of somebody
     *         living here
     */
    public static Optional<TooltipRow.CentredRow> resolveStatusRow(
            Colonies colonies,
            ColonyVisibility colonyVisibility) {

        var inhabitingColonies = colonies.readInhabitingColonies(colonyVisibility);

        // Habitation admits the dead world along with the living colonies, so its emptiness is not
        // the question here: a system whose ruins are all that is left of it holds one and is still
        // a system nobody lives in. What settles the line is which of the two the projection found.
        if (hasLivingColony(inhabitingColonies)) {
            return Optional.empty();
        }
        var statusKey = hasDeadColony(inhabitingColonies)
            ? KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED
            : KmuStrings.POLITICAL_MAP_TOOLTIP_UNPOPULATED;

        // Set across the box, crestless: the status qualifies the whole system rather than being one
        // entry of a list, so it is spoken for the box the way the decree above it is - laid in the
        // columns instead, it would read as the first row of a breakdown that has none.
        return Optional.of(CellTooltipRows.buildBannerRow(
            NO_CREST,
            KmuStrings.get(statusKey)));
    }

    // Whether anybody is there now. Stated as the exclusion of the one kind that inhabits its place
    // without anybody being on it, so a kind added later reads as somebody living there unless it
    // says otherwise - which is the same direction habitation itself is written in, and the safer
    // one: a line calling a settled system unpopulated is worse than one declining to.
    private static boolean hasLivingColony(List<Colony> inhabitingColonies) {

        for (var colony : inhabitingColonies) {
            if (colony.kind() != ColonyKind.DEAD_COLONY) {
                return true;
            }
        }
        return false;
    }

    // Whether what is left of the place is ruins. Asked only once nobody is found living there, so
    // a dead world beside a living colony never heads the box - the system is populated, and the
    // breakdown beneath names the ruins along with everything else.
    private static boolean hasDeadColony(List<Colony> inhabitingColonies) {

        for (var colony : inhabitingColonies) {
            if (colony.kind() == ColonyKind.DEAD_COLONY) {
                return true;
            }
        }
        return false;
    }
}
