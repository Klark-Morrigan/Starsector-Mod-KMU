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
 * <p>The name rides along because a word the colony is already called is not worth calling out
 * again, and the only thing that can say so is the label the line was built with.
 *
 * @param colonyName          what the line names the colony, as the account built it
 * @param kind                what sort of place the colony is; null states nothing, an unstated
 *                            kind being no grounds for a finding
 * @param isHoldingTheClaim   whether this colony is the one that took the system - the contest's
 *                            own finding, and so one only the claims box ever states
 * @param isDiscoveredByPlayer whether the player has found the colony's entity. Only a reveal puts
 *                            an unfound colony on a list at all, which is where the word it earns
 *                            has anywhere to appear
 * @param isHiddenMarket      whether the colony conceals itself rather than being held in the open
 * @param isListedByEconomy   whether the economy's own set holds this colony, as the walk that
 *                            selected it decided
 */
public record ColonyQualifierFacts(
    String colonyName,
    ColonyKind kind,
    boolean isHoldingTheClaim,
    boolean isDiscoveredByPlayer,
    boolean isHiddenMarket,
    boolean isListedByEconomy) {
}
