package kmu.maplayers.ownermap.render.rebuild;

/**
 * The content key's owners half: only what reaches the resolve of who owns what.
 *
 * <p>Of the sidebar picks that is the spotlight alone - the two recedes, the name format and the
 * outline reach the paint and nothing before it. The view's terms stay in whole, since the rule a
 * view resolves owners by can read LunaLib fields, and a view that groups owners together moves its
 * owners whenever those groups move. A style pick moves the content key and leaves this one where it
 * was, which is what lets the rebuild it owes keep the owners.
 *
 * @param viewKey         the terms shared with the content key
 * @param selectedOwnerId the spotlit owner, or null off filter
 */
record OwnersKey(
    ViewKey viewKey,
    String selectedOwnerId) {
}
