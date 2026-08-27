package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.visibility.ColonyKind;

/**
 * What a box has found out about one colony that its own arithmetic does not state: what sort of
 * place it is, whether it took the system, and the three ways it may be out of plain view.
 *
 * <p>Assembled by each hover family from whatever it holds, because the two hold these facts on
 * unlike types - a claim row carries an admission, a weighed dominance row a concealment flag, an
 * unweighed one its kind - and a shared read handed either of those carriers could serve one family
 * only. A small value each fills in is what lets one resolver answer for both.
 *
 * <p>The colony's name is not among them, although one rule reads it: the line already carries it,
 * so a name here would be a second copy of the label a caller had just built the line from - free
 * to name a different colony than the line does.
 *
 * @param kind                 what sort of place the colony is; null states nothing, an unstated
 *                             kind being no grounds for a finding
 * @param isHoldingTheClaim    whether this colony is the one that took the system - the contest's
 *                             own finding, and so one only the claims box ever states
 * @param isDiscoveredByPlayer whether the player has found the colony's entity. Only a reveal puts
 *                             an unfound colony on a list at all, which is where the word it earns
 *                             has anywhere to appear
 * @param isHiddenMarket       whether the colony conceals itself rather than being held in the open
 * @param isOpenlyKnownMarket  whether that concealment is public knowledge - a landmark keeping no
 *                             comm directory rather than a base hiding from anyone. Its own fact
 *                             beside the concealment rather than a correction to it, the colony
 *                             being concealed in every sense a visibility rule cares about
 * @param isListedByEconomy    whether the economy's own set holds this colony, as the walk that
 *                             selected it decided
 */
public record ColonyQualifierFacts(
    ColonyKind kind,
    boolean isHoldingTheClaim,
    boolean isDiscoveredByPlayer,
    boolean isHiddenMarket,
    boolean isOpenlyKnownMarket,
    boolean isListedByEconomy) {
}
