package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.math.geometry.Rectangle;
import kmlib.profiling.Timings;
import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.layout.ControlStripLayout;
import kmlib.starsector.ui.render.gl.NotchState;
import kmlib.starsector.ui.render.gl.TabPanelRenderer;
import kmlib.starsector.ui.render.gl.TabRenderStyle;
import kmlib.starsector.ui.render.gl.VanillaTabColors;
import kmlib.starsector.ui.render.gl.WidgetStyle;
import kmlib.starsector.ui.widgets.BoxBorder;

import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.style.SidebarPalettes;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

/**
 * Draws the political-map sidebar for one {@link SidebarHost} as a campaign UI listener: it gates on the
 * host, advances the host's collapse handle off a wall clock, resolves the host's placement, and hands it
 * to the reusable KMLib {@link TabPanelRenderer} to paint. The panel's actual paint - the bordered frame,
 * the vanilla-styled tab header, the body controls, the scrollbar, and the collapse handle - is the
 * renderer's; this class owns only the wiring KMLib cannot: when to draw (the host's gate), which colours
 * and fonts to draw in (a {@link WidgetStyle} built from the live player colours and settings), advancing
 * the collapse off real time (the campaign is paused while these screens are open, so a game-time delta
 * would freeze the fold), and the view-state log. One instance per host, so the sector map and the intel
 * screen each get their own frame clock and log dedupe.
 *
 * <p>Both screens are vanilla core-UI surfaces with no seam to attach a mod panel, so the sidebar is drawn
 * in UI coordinates through {@link CampaignUIRenderingListener} - specifically the above-tooltips pass, the
 * only one composited after the opaque core-UI screen, so nothing it draws occludes the panel. It draws the
 * placement the input listener also hit-tests, so what is drawn and what is clickable line up.
 */
public final class SidebarRenderer implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(SidebarRenderer.class);

    // The body face: the insignia body font. The tab face is the resolver's, since it both measures
    // and draws the tabs.
    private static final StarsectorFont BODY_FONT = StarsectorFont.VANILLA_INSIGNIA_15;

    // The collapse fraction a fully docked body reports; the eased curve lands exactly on it at the end,
    // so an equality-or-above test reads "settled at the docked rail" rather than "still folding".
    private static final float FULLY_DOCKED_FRACTION = 1f;

    // The screen this renderer draws the sidebar on: its gate, placement, controller, and view-state text.
    private final SidebarHost host;

    // View-state trace. The panel has no error state - when a signal blocks it, it is simply absent - so
    // the log is the only place "why hidden" or "drawn where" is answerable. Deduped on the whole line: a
    // steady state is one line, every change a fresh one. Null to start, so the first pass logs and thereby
    // proves the listener is registered and fires.
    private String lastLoggedLine;

    // Wall-clock nanos at the previous drawn frame, so the collapse animation advances by real elapsed
    // time. A wall clock rather than the campaign's own because these screens are open on a paused game
    // where advance() does not tick, so a game-time delta would freeze the fold mid-fold. Zero means "no
    // previous frame" - the first frame and every re-open after the overlay is hidden - so that frame
    // advances by nothing rather than by the whole gap since the screen was last open.
    private long previousFrameNanos;

    public SidebarRenderer(SidebarHost host) {
        this.host = host;
    }

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Fires from a panel inside the same tree as the core screen, so the opaque screen covers anything
        // drawn here. The panel draws in the above-tooltips pass instead.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {

        // The only pass composited after the entire core screen (and its tooltips), so it is the sole layer
        // the opaque core-UI screen cannot occlude - the panel has to draw here.
        boolean isOverlayShowing = host.isOverlayShowing();
        if (!isOverlayShowing) {

            // Drop the frame clock so the next re-open advances from nothing rather than by the whole gap
            // the screen was closed, which would otherwise snap a half-folded panel straight to its end.
            previousFrameNanos = 0L;
            logViewStateOnChange("hidden; " + host.describeViewState());
            return;
        }

        // Step the collapse toward its target by this frame's real elapsed time before laying the panel out,
        // so the placement resolves at the freshly-advanced fold; the pace is the player's collapse-seconds
        // setting, with zero meaning an instant snap.
        host.getController().advanceCollapse(
                elapsedSinceLastFrame(),
                KmuLunaSettings.getPoliticalMapSidebarCollapseSeconds());

        // Offer the freshly-advanced fold to the host's fold selection, which decides for itself whether
        // that end is worth storing. Here rather than in the input pass because a fold is only unambiguous
        // once it settles, which happens frames after the handle press that started it.
        recordSettledFold(host);

        // The same placement the input listener hit-tests, resolved from one source so the drawn box and the
        // clickable box line up. Null means there is nothing to draw - the tab font could not load, or the
        // host's anchor is gone - so the panel stays absent, logged once.
        var placement = host.resolvePlacement();
        if (placement == null) {
            logViewStateOnChange("hidden; placement unavailable; " + host.describeViewState());
            return;
        }
        var settings = Global.getSettings();
        var borderWidth = KmuLunaSettings.getPoliticalMapSidebarBorderWidth();
        var opacity = KmuLunaSettings.getPoliticalMapSidebarBackgroundOpacity();

        // Logged before the draw, with the resolved footprint / screen / opacity, so a panel gated in but
        // never seen is diagnosed from the numbers rather than another run.
        logViewStateOnChange("showing; " + host.describeViewState() + "; screen="
                + settings.getScreenWidth() + "x" + settings.getScreenHeight()
                + " box=" + formatRect(placement.body().box()) + " opacity=" + opacity);

        // The live notch state: the collapse fraction the layout above was resolved at, and whether the
        // input pass latched the pointer over the notch this frame, so the drawn fold and the lit handle
        // match what the placement was built from.
        var notchState = new NotchState(
                host.getController().getCollapseFraction(),
                host.getController().isNotchHovered());
                
        // The frame to stroke: the settings width, and the edges the host keeps - it drops any edge sitting
        // flush against another panel (the intel overlay omits the borders it shares with the visor) so the
        // sidebar does not draw a second frame over that panel's own.
        var border = new BoxBorder(borderWidth, host.resolveBorderEdges(placement));
        TabPanelRenderer.render(
                placement,
                buildStyle(),
                border,
                notchState,
                opacity);
    }

    // Offers the host's fold selection the end its panel has settled at, and nothing at all while the panel
    // is still folding. Which end that is comes from the two things the panel publishes: the collapse
    // fraction, which reaches its docked end exactly, and the fully-expanded flag, which already separates
    // a panel resting open from one that has just turned away from that end without moving yet.
    static void recordSettledFold(SidebarHost host) {
        var settledFold = resolveSettledFold(
                host.getController().getCollapseFraction(),
                host.getController().isFullyExpanded());
        if (settledFold != null) {
            host.getFoldSelection().recordFold(settledFold);
        }
    }

    // Which end the panel has settled at, or null while it has reached neither and there is nothing to
    // record.
    static Boolean resolveSettledFold(float collapseFraction, boolean isFullyExpanded) {
        if (collapseFraction >= FULLY_DOCKED_FRACTION) {
            return Boolean.TRUE;
        }
        return isFullyExpanded ? Boolean.FALSE : null;
    }

    // Real seconds since the previous drawn frame, off the wall clock so the fold keeps animating on the
    // paused screen. A zeroed frame clock - the first frame and every re-open - reports no elapsed time, so
    // a re-opened panel resumes from where it was rather than jumping by the whole time the screen was shut.
    private float elapsedSinceLastFrame() {
        var now = System.nanoTime();
        var elapsed = previousFrameNanos == 0L
                ? 0f
                : (float) Timings.convertNanosToSeconds(now - previousFrameNanos);
        previousFrameNanos = now;
        return elapsed;
    }

    // The sidebar's look, built each frame from the live player colours: a black body backdrop (faded
    // by the opacity setting), the base and bright player accents, the map's own tab colour scheme, the
    // orbitron tab face at the layout's tab size, the insignia body face, and the collapse handle's
    // chevron shades for the colour the player picked for it.
    private static WidgetStyle buildStyle() {
        var accent = StarsectorUiColor.VANILLA_PLAYER_BASE.resolve();
        var brightAccent = StarsectorUiColor.VANILLA_PLAYER_BRIGHT.resolve();
        return new WidgetStyle(
                // The body backdrop is black; the opacity setting fades it, so the body reads as a
                // translucent-black pane the map shows through rather than a solid block. Black, not the
                // player-dark tint, so the body stays neutral - only the tabs header, accents, and the
                // notch carry colour. This is the body fill alone; the tabs' own fills live in the
                // TabRenderStyle below, a separate field, so the body's colour never couples to the
                // header's.
                // The header also opts out of this opacity fade and paints opaque (see
                // TabPanelRenderer.HEADER_OPACITY), so the tabs read solid over the faded body.
                StarsectorUiColor.BLACK.resolve(), // Panel fill.
                accent,
                brightAccent,
                BODY_FONT,
                new TabRenderStyle(
                        VanillaTabColors.mapTabs(),
                        new TextFace(
                                LiveSidebarPlacement.TAB_FONT,
                                ControlStripLayout.TAB_FONT_SIZE)),
                SidebarPalettes.resolveNotchColors(
                        KmuLunaSettings.getPoliticalMapSidebarChevronColor(),
                        accent,
                        brightAccent));
    }

    // Logs the composed view-state line once per change; the dedupe keeps a steady state to one line while
    // every real transition prints a fresh one.
    private void logViewStateOnChange(String line) {
        if (line.equals(lastLoggedLine)) {
            return;
        }
        lastLoggedLine = line;
        LOG.debug("Political map sidebar " + line);
    }

    private static String formatRect(Rectangle rect) {
        return "[" + rect.x() + "," + rect.y() + " " + rect.width() + "x" + rect.height() + "]";
    }
}
