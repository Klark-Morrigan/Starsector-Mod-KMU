package kmu.maplayers.politicalmap.base.sidebar;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

/**
 * The panel a body control was placed on: the sector its writes repaint, and the screen they are filed
 * under. Settled once where the body is built, and carried down to every control the body composes.
 *
 * <p>One value because the two never travel apart and a control resolving either for itself is the same
 * fault twice over. Found at the click instead of carried, the board would be whichever sector was
 * running by then and the screen whichever panel was showing - so a flip made on the sector map's panel
 * would repaint a map the player is not looking at, and land in the intel screen's slot. Both compile,
 * and both read as the feature working until the player sets the same option on each panel and finds one
 * of them moved.
 *
 * <p>It is context rather than an address: nothing here reads or writes memory, and the screen only
 * becomes an address when a control unpacks it into the store it drives.
 *
 * @param board       the refresh board of the sector this body was built for
 * @param memoryScope the screen whose panel opened this body
 */
public record BodyControlTarget(
    MapLayerRefreshBoard board,
    ScreenMemoryScope memoryScope) {
}
