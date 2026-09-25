package kmu.maplayers.ownermap.tooltip;

import kmu.maplayers.base.visibility.colonies.ColonyKind;

/**
 * What a box has found out about one colony that its own arithmetic does not state: what sort of
 * place it is, anything its layer finds first, and how it is out of plain view.
 *
 * <p>Assembled by each box from whatever it holds, because boxes hold these facts on unlike types,
 * and a shared read handed one box's carrier could serve that box only. A small value each fills in
 * is what lets one resolver answer for all of them.
 *
 * <p>The three ways a colony is out of sight are gathered into {@link ColonyConcealment} rather than
 * lying flat here, so the listing is the only boolean left.
 *
 * <p>The colony's name is not among them, although one rule reads it: the line already carries it,
 * so a name here would be a second copy of the label a caller had just built the line from - free
 * to name a different colony than the line does.
 *
 * @param kind              what sort of place the colony is; null states nothing, an unstated kind
 *                          being no grounds for a finding
 * @param leadingFinding    a finding the painting layer states ahead of every other, already
 *                          worded for the reader - what its own mechanic makes of this colony;
 *                          null states none
 * @param concealment       how the colony is out of plain view, if it is; null reads as a found
 *                          colony held in the open
 * @param isListedByEconomy whether the economy's own set holds this colony, as the walk that
 *                          selected it decided
 */
public record ColonyQualifierFacts(
    ColonyKind kind,
    String leadingFinding,
    ColonyConcealment concealment,
    boolean isListedByEconomy) {

    // What a colony nothing was read about is out of sight by, which is nothing at all. Absorbed
    // rather than refused because a box holding a row it has no concealment reading for is already
    // committed to drawing that row.
    private static final ColonyConcealment IN_PLAIN_SIGHT =
        new ColonyConcealment(true, false, false);

    /** Reads an absent concealment as a found colony held in the open. */
    public ColonyQualifierFacts {
        concealment = concealment == null
            ? IN_PLAIN_SIGHT
            : concealment;
    }
}
