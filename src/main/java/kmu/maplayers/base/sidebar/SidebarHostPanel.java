package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.maplayers.base.layer.ScreenLayerPicks;

import java.util.Set;

/**
 * Everything a host supplies about its own sidebar panel except where that panel anchors: how the row
 * looks, the transient scroll and fold it is standing at, the screen's own picks, and which frame
 * edges it reserves. Bundled because all four travel together from one host to one placement, and
 * because the anchor is the only thing the two hosts genuinely differ in - stated as a group, that
 * difference is what is left in the signature rather than something to pick out of a row of arguments.
 *
 * <p>Two hosts each hold one of these, which is what keeps the on-map and intel panels apart: separate
 * scroll and fold, separate picks, and separate looks, over one layout.
 *
 * @param tabStyle      the host's tab look, the value its band is snapped to and painted from
 * @param controller    the panel's scroll and collapse state
 * @param screenPicks   the screen's own picks: the active layer, read for the lit tab and body and
 *                      written when a tab is clicked, and the show-or-hide state beside it, which
 *                      settles which tabs this screen is offered
 * @param borderedEdges which frame edges the host reserves and strokes; a host drawn flush against a
 *                      neighbour drops the shared edges so the box collapses the strip they would occupy
 */
public record SidebarHostPanel(
    TabStyle tabStyle,
    TabPanelController controller,
    ScreenLayerPicks screenPicks,
    Set<BoxEdge> borderedEdges) {
}
