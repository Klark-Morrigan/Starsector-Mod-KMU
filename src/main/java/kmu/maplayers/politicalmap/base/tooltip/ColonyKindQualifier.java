package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.colonies.ColonyKind;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * What a box calls out at the end of a colony's line for being the kind of place it is.
 *
 * <p>One read for both hover families, because the two name the same colonies of the same system:
 * a kind resolved separately at each could have the domination box call a world collapsed while the
 * claims box lists it as governed, which is the one disagreement a shared account cannot survive.
 *
 * <p>It applies the wording as well as resolving it, so the sharing reaches as far as it claims to.
 * A box left to layer the answer onto its own line would be a second statement of which end of the
 * line a kind speaks at and in which shade - and two of those are what a shared read was supposed
 * to have ruled out.
 *
 * <p>Only the collapsed colony says anything. A colony, an outpost and a derelict are each already
 * told by what the line carries - a weight, a nought, the glyph the map marks them with - while a
 * collapse and a hulk arrive identically: unowned, off-economy and listed at nought. The qualifier
 * is the only thing parting them, which is why it is stated rather than left to the reader.
 *
 * <p>A qualifier rather than a note, so it reads in the finding's shade: what the place <em>is</em>
 * is something the box has found out about it, where the remark beside it - how current the news
 * is - is the box talking about its own account. A reader scanning for findings should meet the
 * first and pass over the second.
 *
 * <p>The wording is the status row's own, stated once for the layer. A system headed
 * {@code Decivilised} and a line calling its world the same thing are one fact at two altitudes,
 * and two strings would eventually part.
 */
public final class ColonyKindQualifier {

    private ColonyKindQualifier() {
    }

    /**
     * Runs a colony's line on into what its kind calls out, where the kind states anything.
     *
     * <p>Layered after any remark the line already carries, and the two cannot displace each other:
     * they are drawn in different shades at opposite ends of the line - the remark quiet, run on
     * after the name; the qualifier a finding, at the end.
     *
     * @param line the colony's line as the box has built it so far
     * @param kind what kind of place the colony is; null states nothing, an unstated kind being no
     *             grounds for a finding
     * @return the line, called out where the kind has something to say and untouched otherwise
     */
    public static CellTooltipEntryLine qualifyByKind(
            CellTooltipEntryLine line,
            ColonyKind kind) {

        return resolveKindQualifier(kind)
            .map(line::qualifiedWith)
            .orElse(line);
    }

    // What the kind calls out, or nothing. Private because the wording is only ever wanted to put
    // on a line, and a caller free to take the words alone is a caller free to put them somewhere
    // else on the box.
    private static Optional<String> resolveKindQualifier(ColonyKind kind) {

        return kind == ColonyKind.UNGOVERNED_COLONY
            ? Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED))
            : Optional.empty();
    }
}
