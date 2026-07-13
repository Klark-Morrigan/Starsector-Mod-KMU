package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.layout.ControlStripLayout;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.render.gl.PanelRenderer;
import kmlib.starsector.ui.render.gl.PanelStyle;
import kmlib.starsector.ui.render.gl.VanillaTabColors;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

import java.awt.Color;

/**
 * Drives the on-map layer sidebar as a campaign UI listener: it gates when the panel shows, resolves the
 * placement, and hands it to the reusable KMLib {@link PanelRenderer} to paint. The panel's actual paint -
 * the bordered frame, the vanilla-styled tab strip, the body controls, and the scrollbar - is the
 * renderer's; this class owns only the wiring KMLib cannot: when to draw (the sector map with the
 * starscape filter off), which colours and fonts to draw in (a {@link PanelStyle} built from the live
 * player colours and settings), flushing the body controls' deferred settings writes when the overlay
 * closes, and the view-state log.
 *
 * <p>The sector map is a vanilla core-UI tab with no seam to attach a mod panel, so the sidebar is drawn
 * in UI coordinates through {@link CampaignUIRenderingListener} - specifically the above-tooltips pass,
 * the only one composited after the opaque core-UI map, so nothing the map draws occludes it. It draws
 * the {@link LiveSidebarPlacement} the input listener also hit-tests, so what is drawn and what is
 * clickable line up.
 */
public final class MapLayerSidebar implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(MapLayerSidebar.class);

    // The body face: the insignia body font, a graphics/fonts basename the font cache resolves to a
    // loadable path. The tab face is the resolver's, since it both measures and draws the tabs.
    private static final String BODY_FONT = "insignia15LTaa";

    // The whole panel's backdrop: the bordered box fills its footprint with this, and the tab strip and
    // body controls draw their player-colour accents over it, so the general fill stays black and only lit
    // elements carry colour.
    private static final Color PANEL_FILL = Color.BLACK;

    // View-state trace. The panel has no error state - when a signal blocks it, it is simply absent - so
    // the log is the only place "why hidden" or "drawn where" is answerable. Deduped on the whole line: a
    // steady state is one line, every change a fresh one. Null to start, so the first pass logs and thereby
    // proves the listener is registered and fires.
    private String lastLoggedLine;

    // Whether the overlay was on screen last pass, so the transition to off can be caught. The body
    // controls write their settings through LunaLib's deferred path (live value, batched disk write), so
    // leaving the overlay is when a map session's edits are flushed to disk.
    private boolean wasOverlayShowing;

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the map screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Fires from a panel inside the same tree as the core map screen, so the opaque map covers
        // anything drawn here. The panel draws in the above-tooltips pass instead.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // The only pass composited after the entire map screen (and its tooltips), so it is the sole layer
        // the opaque core-UI map cannot occlude - the panel has to draw here.
        boolean isOverlayShowing = CampaignMapView.isSectorMapWithStarscapeOff();
        // On the pass the overlay turns off - the player closed the map or switched on the starscape -
        // persist the body controls' deferred writes, collapsing the map session's edits into one write.
        if (wasOverlayShowing && !isOverlayShowing) {
            KmuLunaSettings.flushPendingWrites();
        }
        wasOverlayShowing = isOverlayShowing;
        if (!isOverlayShowing) {
            logViewStateOnChange("hidden; " + CampaignMapView.describeViewState());
            return;
        }
        // The same placement the input listener hit-tests, resolved from one source so the drawn box and
        // the clickable box line up. Null means the tab font could not load - the layout snaps tabs to
        // measured text and cannot run without it - so the panel stays absent, logged once.
        var placement = LiveSidebarPlacement.resolveCurrentPlacement();
        if (placement == null) {
            logViewStateOnChange("hidden; tab font '" + LiveSidebarPlacement.TAB_FONT
                    + "' unavailable");
            return;
        }
        var settings = Global.getSettings();
        var borderWidth = KmuLunaSettings.getPoliticalMapSidebarBorderWidth();
        var opacity = KmuLunaSettings.getPoliticalMapSidebarBackgroundOpacity();
        // Logged before the draw, with the resolved footprint / screen / opacity, so a panel gated in but
        // never seen is diagnosed from the numbers rather than another run.
        logViewStateOnChange("showing; " + CampaignMapView.describeViewState() + "; screen="
                + settings.getScreenWidth() + "x" + settings.getScreenHeight()
                + " box=" + formatRect(placement.box()) + " opacity=" + opacity);
        PanelRenderer.render(placement, buildStyle(), borderWidth, selectedTabIndex(), opacity);
    }

    // The map sidebar's look, built each frame from the live player colours: a black backdrop, the base
    // and bright player accents, the map's own tab colour scheme, the orbitron tab face at the layout's
    // tab size, and the insignia body face.
    private static PanelStyle buildStyle() {
        return new PanelStyle(PANEL_FILL, Misc.getBasePlayerColor(), Misc.getBrightPlayerColor(),
                VanillaTabColors.mapTabs(), LiveSidebarPlacement.TAB_FONT,
                ControlStripLayout.TAB_FONT_SIZE,
                BODY_FONT);
    }

    // The active layer's position in the registry order the tabs are laid out in, so the selected tab the
    // strip lights matches the active pick.
    private static int selectedTabIndex() {
        return MapLayerRegistry.getLayers().indexOf(MapLayerRegistry.getActiveLayer());
    }

    // Logs the composed view-state line once per change; the dedupe keeps a steady state to one line while
    // every real transition prints a fresh one.
    private void logViewStateOnChange(String line) {
        if (line.equals(lastLoggedLine)) {
            return;
        }
        lastLoggedLine = line;
        LOG.debug("Political map layer sidebar " + line);
    }

    private static String formatRect(Rectangle rect) {
        return "[" + rect.x() + "," + rect.y() + " " + rect.width() + "x" + rect.height() + "]";
    }
}
