package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.math.geometry.Rectangle;
import kmlib.profiling.Timings;
import kmlib.starsector.ui.input.HoverFade;
import kmlib.starsector.ui.render.gl.panel.NotchState;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.render.gl.tabs.TabPanelRenderer;
import kmlib.starsector.ui.widgets.BoxBorder;

import kmu.settings.KmuMapLayerSettings;

import org.apache.log4j.Logger;

/**
 * Draws the map-layer sidebar for one {@link SidebarHost} as a campaign UI listener: it gates on the
 * host, advances the host's collapse handle off a wall clock, resolves the host's placement, and hands it
 * to the reusable KMLib {@link TabPanelRenderer} to paint. The panel's actual paint - the bordered frame,
 * the vanilla-styled tab header, the body controls, the scrollbar, and the collapse handle - is the
 * renderer's; this class owns only the wiring KMLib cannot: when to draw (the host's gate), advancing the
 * panel's animations off real time (the campaign is paused while these screens are open, so a game-time
 * delta would freeze the fold and every input motion alike), and the view-state log. One instance per
 * host, so the sector map and the intel screen each get their own frame clock and log dedupe.
 *
 * <p>Which colours and fonts to draw in is the host's: it hands over a {@link WidgetStyle} resolved from
 * the live player colours and settings, and this pass carries it through untouched. That is what lets two
 * screens' panels read as part of the chrome each sits in without a screen test anywhere in here - the
 * only per-screen readings left are the ones a host cannot make without the laid-out box.
 *
 * <p>The panel is drawn in UI coordinates through {@link CampaignUIRenderingListener} - specifically the
 * above-tooltips pass, the only one composited after the opaque core-UI screen, so nothing it draws
 * occludes the panel. It draws the placement the input listener also hit-tests, so what is drawn and what
 * is clickable line up.
 *
 * <p>Drawing it rather than composing it from vanilla widgets is not forced by the screens. Both map
 * widgets are containers that adopt a mod-owned panel, render it over the map surface, and route input to
 * the widgets inside it. What keeps this pass is that those containers are rebuilt each time their screen
 * opens, so a composed panel would need re-attaching against a widget this mod does not own, and that
 * several of the controls - the icon radios, the direction table, the glyph controls - have no vanilla
 * widget to compose from.
 */
public final class SidebarRenderer implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(SidebarRenderer.class);

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

            // The panel's input motions reset with that clock: a fade or a pulse left part-way through has
            // no elapsed time to wind it down on re-open, so it would paint as the tail of an interaction
            // the player never saw begin.
            host.getController().resetInputMotions();
            logViewStateOnChange("hidden; " + host.describeViewState());
            return;
        }

        // Read once and spent on both of the panel's animations below, which run either side of the layout:
        // a second read would charge the fold and the input motions different slices of the same frame.
        var elapsedSeconds = elapsedSinceLastFrame();

        // Step the collapse toward its target by this frame's real elapsed time before laying the panel out,
        // so the placement resolves at the freshly-advanced fold; the pace is the player's collapse-seconds
        // setting, with zero meaning an instant snap.
        host.getController().advanceCollapse(
            elapsedSeconds,
            KmuMapLayerSettings.getMapSidebarCollapseSeconds());

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
        // Step the panel's input motions - the hover fades, the tabs' click pulses, and their hotkey
        // blinks - against the placement just resolved, the one this frame draws, so the tab and the handle
        // that light are the ones the pointer is over now rather than the ones it was over before the panel
        // last moved. After the layout for exactly that reason, where the fold has to run before it. One
        // pace for every one of them, so the panel answers input at a single rhythm.
        host.getController().advanceInputMotions(
            placement,
            elapsedSeconds,
            HoverFade.DEFAULT_DURATIONS);

        var settings = Global.getSettings();
        var opacity = KmuMapLayerSettings.getMapSidebarBackgroundOpacity();

        // Logged before the draw, with the resolved footprint / screen / opacity, so a panel gated in but
        // never seen is diagnosed from the numbers rather than another run.
        logViewStateOnChange("showing; "
            + host.describeViewState()
            + "; screen="
            + settings.getScreenWidth()
            + "x"
            + settings.getScreenHeight()
            + " box="
            + formatRect(placement.body().box())
            + " opacity="
            + opacity);

        // The live notch state: the collapse fraction the layout above was resolved at, and how far the
        // handle's hover has faded against that same placement, so the drawn fold and the lit handle match
        // what the placement was built from.
        var notchState = new NotchState(
            host.getController().getCollapseFraction(),
            host.getController().getNotchHoverFraction());

        // The frame to stroke: the width the layout already reserved inset space for, taken off the
        // placement so the stroke cannot outgrow its own inset, and the edges the host keeps - it drops any
        // edge sitting flush against another panel (the intel overlay omits the borders it shares with the
        // visor) so the sidebar does not draw a second frame over that panel's own. Only the edges are
        // decided here, because only they need the resolved box to decide against.
        var border = new BoxBorder(
            placement.border().width(),
            host.resolveBorderEdges(placement));
        TabPanelRenderer.render(
            placement,
            // The host's own look, asked for here rather than composed: this pass paints whichever
            // screen's panel it was built for and has nothing to say about how that screen should read.
            // Resolved after the layout, though the two agree on the tab style by both taking the host's.
            host.resolveWidgetStyle(),
            border,
            host.getController().getTabInteractionSources(),
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

    // Logs the composed view-state line once per change; the dedupe keeps a steady state to one line while
    // every real transition prints a fresh one.
    private void logViewStateOnChange(String line) {
        if (line.equals(lastLoggedLine)) {
            return;
        }
        lastLoggedLine = line;
        LOG.debug("Map layer sidebar " + line);
    }

    private static String formatRect(Rectangle rect) {
        return "[" + rect.x() + "," + rect.y() + " " + rect.width() + "x" + rect.height() + "]";
    }
}
