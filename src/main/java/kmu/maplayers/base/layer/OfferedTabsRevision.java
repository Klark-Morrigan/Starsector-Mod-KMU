package kmu.maplayers.base.layer;

import java.util.List;

/**
 * Everything the row a screen offers is composed from, gathered into one value so a caller can ask
 * whether that row could have moved without building it.
 *
 * <p>For comparing, never for reading. A holder keeps the revision it last acted on and compares the
 * current one against it; what the parts are is
 * {@link ScreenLayerTabs#resolveTabbedLayers}' business, and a caller picking one out would be
 * re-deriving the row from its ingredients somewhere else.
 *
 * <p>Built beside that method rather than by whoever wants it, which is the whole point: a fourth
 * ingredient added to the row and not to this would leave every holder skipping the change. One file
 * names both, so the two move together or not at all.
 *
 * <p>Cheap because the two collections are replaced wholesale rather than mutated - the registry
 * hands out a fresh list on each registration, and the held arrangement is a fresh record on each
 * write - so the comparison walks two short lists and stops.
 *
 * @param arrangement        the player's own order and the tabs they took off
 * @param rosterLayers       every registered layer, in registration order
 * @param isControlStanding  whether this screen has a control of its own, which withholds a tab
 */
public record OfferedTabsRevision(
    MapLayerArrangement arrangement,
    List<MapLayer> rosterLayers,
    boolean isControlStanding) {
}
