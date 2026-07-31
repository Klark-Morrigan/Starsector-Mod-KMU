package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.PersistedSidebarFold;

import java.util.Set;

/**
 * The sector map's binding for the map-layer sidebar: it shows while the sector map is up with the
 * starscape filter off and hangs the panel from the screen top-left. This is the on-map sibling of
 * {@link IntelSidebarHost}; the two differ only in gate, anchor, controller, and which frame edges they
 * stroke, and both feed the shared {@link SidebarRenderer} / {@link SidebarInput}.
 *
 * <p>Its panel reopens at the fold this save was left at, and a save that has never folded it opens out,
 * since the on-map sidebar is the player's primary way in to the map layers and has the screen width to
 * sit open.
 */
public final class MapSidebarHost extends BaseSidebarHost {
    /** The one on-map host; the render and input listeners registered for the sector map reference it. */
    public static final MapSidebarHost INSTANCE = new MapSidebarHost();

    // Save-serialised key of this panel's resting fold; frozen once shipped, since renaming it silently
    // returns every existing save to the opening default.
    private static final String MAP_SIDEBAR_DOCKED_KEY = "$kmu_political_map_sidebar_docked";

    private MapSidebarHost() {
        // Opens out on a save that has never folded it: this panel is the player's primary way in to the
        // map layers and has the screen width to sit open, so out is the useful first sight of it. The
        // map screen's own pick goes with it, so its tab is unmoved by a switch on the intel screen.
        super(new PersistedSidebarFold(MAP_SIDEBAR_DOCKED_KEY, false), MapLayerRegistry.getMapSelection());
    }

    @Override
    public boolean isOverlayShowing() {
        return CampaignMapView.isSectorMapWithStarscapeOff();
    }

    @Override
    public TabPanelPlacement resolvePlacement() {
        // Reserves inset space for all four edges, the same full set resolveBorderEdges strokes, so the
        // reserved strips and the stroke never disagree.
        return LiveSidebarPlacement.resolveMapPlacement(
            getController(),
            getLayerSelection(),
            BoxEdge.ALL);
    }

    @Override
    public Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement) {
        // The on-map sidebar floats free on the screen, touching no other panel's edge, so it frames all
        // four sides.
        return BoxEdge.ALL;
    }

    @Override
    public String describeViewState() {
        return CampaignMapView.describeViewState();
    }
}
