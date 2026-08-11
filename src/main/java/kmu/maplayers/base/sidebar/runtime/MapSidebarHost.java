package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.PersistedSidebarFold;
import kmu.maplayers.base.sidebar.style.SidebarStyles;
import kmu.starsector.consolecommands.ConsoleCommandsOverlay;
import kmu.starsector.consolecommands.ConsoleOverlay;

import java.util.Set;

/**
 * The sector map's binding for the map-layer sidebar: it shows while the sector map is up in either of
 * its looks, and hangs the panel from the screen top-left. This is the on-map sibling of
 * {@link IntelSidebarHost}; the two differ only in gate, anchor, controller, look, and which frame edges
 * they stroke, and both feed the shared {@link SidebarRenderer} / {@link SidebarInput}.
 *
 * <p>Its look is the sector map's: a seamless tab strip with its bound key underlined, matching the
 * vanilla Sector/System tabs one tab-height above it, and a frame in the panel's own accent, this one
 * floating free with no neighbouring chrome to match.
 *
 * <p>The sector map showing is this host's half of the gate; standing down for a console that has taken
 * the keyboard is every host's alike and belongs to {@link BaseSidebarHost}.
 *
 * <p>The Starscape filter is not part of the gate. The layers paint through several terrain surfaces,
 * of which two draw in Starscape mode, so there is an overlay under these controls whichever look the
 * map wears and the filter says nothing about whether the sidebar has anything to drive.
 *
 * <p>Its panel reopens at the fold this save was left at, and a save that has never folded it opens out,
 * since the on-map sidebar is the player's primary way in to the map layers and has the screen width to
 * sit open.
 */
public final class MapSidebarHost extends BaseSidebarHost {
    
    /**
     * The one on-map host; the render and input listeners registered for the sector map reference it. This
     * is where the live console read is chosen, the host itself naming only the role.
     */
    public static final MapSidebarHost INSTANCE = new MapSidebarHost(ConsoleCommandsOverlay.INSTANCE);

    // How tall this screen's tab band stands: the on-map sidebar floats free beside the vanilla
    // Sector/System tabs and matches their weight. Content-space - the panel strokes its own top border
    // above the band, so the drawn strip stands the border width taller. Package-private so the number is
    // checkable without a live sector, which the style built from it needs to resolve its colours.
    static final float HEADER_BAND_HEIGHT = 19f;

    // Save-serialised key of this panel's resting fold; frozen once shipped, since renaming it silently
    // returns every existing save to the opening default.
    private static final String MAP_SIDEBAR_DOCKED_KEY = "$kmu_political_map_sidebar_docked";

    MapSidebarHost(ConsoleOverlay consoleOverlay) {
        // Opens out on a save that has never folded it: this panel is the player's primary way in to the
        // map layers and has the screen width to sit open, so out is the useful first sight of it. The
        // map screen's own pick goes with it, so its tab is unmoved by a switch on the intel screen.
        super(
            new PersistedSidebarFold(MAP_SIDEBAR_DOCKED_KEY, false),
            MapLayerRegistry.getMapSelection(),
            consoleOverlay);
    }

    @Override
    public TabPanelPlacement resolvePlacement() {
        // Reserves inset space for all four edges, the same full set resolveBorderEdges strokes, so the
        // reserved strips and the stroke never disagree.
        return LiveSidebarPlacement.resolveMapPlacement(
            buildTabStyle(),
            getController(),
            getLayerSelection(),
            BoxEdge.ALL);
    }

    @Override
    public WidgetStyle resolveWidgetStyle() {
        // The map's own look: the chosen colour scheme's accents over a black body, with the frame
        // taking that same accent since this panel floats free and has no neighbouring chrome to match.
        return SidebarStyles.buildAccentFramedStyle(buildTabStyle());
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

    @Override
    protected boolean isHostScreenShowing() {
        // Per-host rather than a host-blind "a map is showing somewhere": this panel anchors to the
        // sector map's own screen, so it needs that screen up and not merely a map on some other one.
        return CampaignMapView.isSectorMapShowing();
    }

    // The seamless strip, matching the vanilla Sector/System tabs this row hangs beneath.
    private static TabStyle buildTabStyle() {
        return SidebarStyles.buildStripTabStyle(HEADER_BAND_HEIGHT);
    }
}
