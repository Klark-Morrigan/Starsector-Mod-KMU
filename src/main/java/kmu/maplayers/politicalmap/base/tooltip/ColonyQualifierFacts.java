package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.visibility.ColonyKind;

/**
 * What a box has found out about one colony that its own arithmetic does not state: what sort of
 * place it is, whether it took the system, and how it is out of plain view.
 *
 * <p>Assembled by each hover family from whatever it holds, because the two hold these facts on
 * unlike types - a claim row carries an admission, a weighed dominance row a concealment flag, an
 * unweighed one its kind - and a shared read handed either of those carriers could serve one family
 * only. A small value each fills in is what lets one resolver answer for both.
 *
 * <p>The three ways a colony is out of sight are gathered into {@link ColonyConcealment} rather than
 * lying flat here, so no two facts of one type ever sit adjacent: the claim and the listing are the
 * only booleans left, and the concealment stands between them.
 *
 * <p>The colony's name is not among them, although one rule reads it: the line already carries it,
 * so a name here would be a second copy of the label a caller had just built the line from - free
 * to name a different colony than the line does.
 *
 * @param kind              what sort of place the colony is; null states nothing, an unstated kind
 *                          being no grounds for a finding
 * @param isHoldingTheClaim whether this colony is the one that took the system - the contest's own
 *                          finding, and so one only the claims box ever states
 * @param concealment       how the colony is out of plain view, if it is; null reads as a found
 *                          colony held in the open
 * @param isListedByEconomy whether the economy's own set holds this colony, as the walk that
 *                          selected it decided
 */
public record ColonyQualifierFacts(
    ColonyKind kind,
    boolean isHoldingTheClaim,
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
