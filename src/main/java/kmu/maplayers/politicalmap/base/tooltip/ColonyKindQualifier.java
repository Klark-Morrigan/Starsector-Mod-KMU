package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.colonies.ColonyKind;

import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * What a box calls out at the end of a colony's line for being the kind of place it is.
 *
 * <p>One read for both hover families, because the two name the same colonies of the same system:
 * a kind resolved separately at each could have the domination box call a world dead while the
 * claims box lists it as living, which is the one disagreement a shared account cannot survive.
 *
 * <p>Only the dead world says anything. A colony, an outpost and a derelict are each already told
 * by what the line carries - a weight, a nought, the glyph the map marks them with - while a ruin
 * and a hulk arrive identically: unowned, off-economy and listed at nought. The qualifier is the
 * only thing parting them, which is why it is stated rather than left to the reader.
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
     * Resolves what the colony's kind calls out at the end of its line.
     *
     * @param kind what kind of place the colony is; null states nothing, an unstated kind being no
     *             grounds for a finding
     * @return the wording, or empty where the kind says nothing a line has to carry
     */
    public static Optional<String> resolveKindQualifier(ColonyKind kind) {

        return kind == ColonyKind.DEAD_COLONY
            ? Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED))
            : Optional.empty();
    }
}
