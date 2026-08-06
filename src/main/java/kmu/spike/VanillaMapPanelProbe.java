package kmu.spike;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import kmlib.starsector.ui.map.probes.ShownMapTab;

import org.apache.log4j.Logger;

import java.awt.Color;

/**
 * Attaches one mod-owned panel of ordinary vanilla widgets to whichever map widget is on screen,
 * as a live reference for what vanilla's own controls look like and how they behave there.
 *
 * <p>The map widget is a container, not a surface: the map itself is one child of it, and the
 * filter checkboxes vanilla shows over the intel screen's map are a sibling laid on top of that
 * child. A mod panel added to the same container is treated no differently - it renders over the
 * map, it is offered input, and the vanilla widgets inside it dispatch their own presses. One
 * attachment covers both the {@code Sector} and {@code System} views, since those swap what the
 * map surface draws rather than the container's child list.
 *
 * <p>Kept because the map-layer sidebar is drawn in GL and styled to match the surrounding
 * chrome: this panel puts genuine vanilla widgets at the sidebar's own sizes on the same screen,
 * so the styling can be compared against the real thing rather than against a screenshot.
 *
 * <p>Off by default, and gated on the game's dev mode besides, so neither a player build nor an
 * ordinary dev session draws it. Flip {@link #IS_ENABLED} to re-enable.
 */
public final class VanillaMapPanelProbe implements CampaignUIRenderingListener {

    private static final Logger LOG = Global.getLogger(VanillaMapPanelProbe.class);

    // Whether the reference panel is drawn at all. Off, because it is wanted only while vanilla's
    // look is being compared against the GL sidebar's, and a panel sitting on the map the rest of
    // the time is one more thing between the player and the map. A constant rather than a setting:
    // a settings row would put a developer's comparison aid on a player-facing tab.
    private static final boolean IS_ENABLED = false;

    // Ids echoed back through the plugin's button callback, so a press names which control sent
    // it rather than only that something was pressed. Deliberately unprefixed: the mod's own
    // prefix marks a settings field id, and these are callback tokens that back no setting.
    private static final String AREA_CHECKBOX_ID = "spikeAreaCheckbox";
    private static final String BUTTON_ID = "spikeButton";

    private static final float AREA_CHECKBOX_HEIGHT = 24f;
    private static final float BUTTON_HEIGHT = 20f;
    private static final float ELEMENT_PAD = 6f;
    private static final float PANEL_HEIGHT = 120f;
    private static final float PANEL_WIDTH = 240f;

    // Insets from the map widget's own top-RIGHT corner. That corner rather than the top-left
    // because the map-layer sidebar this panel is compared against hangs from the left one, and a
    // reference sitting under the thing it is a reference for can be compared with neither.
    private static final float PANEL_INSET_X = 12f;
    private static final float PANEL_INSET_Y = 60f;

    // Width the controls are laid out to: the panel's, less the pad they are inset by on both
    // sides, so neither control is clipped by the panel it sits in.
    private static final float CONTROL_WIDTH = PANEL_WIDTH - ELEMENT_PAD * 2f;

    // The map widget the panel was handed to, which is what "already attached" is tested against.
    // The widget rather than the panel, because the map widget is rebuilt each time its screen
    // opens: a held panel says nothing about whether it is still a child of anything on screen.
    private UIComponentAPI attachedMapTab;

    // Says once per session that the map widget would not take a child, rather than on every
    // frame the probe retries.
    private boolean hasLoggedAttachFailure;

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Nothing is drawn by the probe itself in any pass; the panel is a real widget and the
        // game draws it.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Same.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // A render pass used purely as a frame tick, because the attachment has to be re-made
        // whenever the map widget is rebuilt and this is the pass already known to run on every
        // frame both map screens are up. The probe draws nothing here.
        attachPanelToShownMapTab();
    }

    // Puts the panel on whichever map widget is showing, and drops the reference when none is.
    // Re-attaches rather than re-uses when the widget changes, since each opening of a map screen
    // builds a new widget and the panel given to the previous one goes with it.
    private void attachPanelToShownMapTab() {

        // Both gates first, so a game not showing the panel does no widget-tree work per frame.
        if (!isPanelEnabled() || !Global.getSettings().isDevMode()) {
            return;
        }

        UIComponentAPI mapTab;
        try {
            mapTab = ShownMapTab.resolveShownMapTab();
        } catch (RuntimeException e) {
            // The reach into the core UI failed outright. Nothing to attach to, and the probe is
            // not worth a crash on a screen it has no business on.
            forgetAttachment();
            return;
        }

        if (mapTab == null) {
            // Every screen showing no map. The widget the panel was on is gone with its screen,
            // so the pair is dropped and the next opening attaches a fresh panel.
            forgetAttachment();
            return;
        }

        if (mapTab == attachedMapTab) {
            return;
        }

        attachPanelTo(mapTab);
    }

    // Builds a panel of plain vanilla widgets and makes it a child of the given map widget.
    private void attachPanelTo(UIComponentAPI mapTab) {

        // Being a panel is what makes the map widget able to adopt a child at all. A map widget
        // that is not one is the premise holding, and is worth saying once.
        if (!(mapTab instanceof UIPanelAPI mapPanel)) {
            logAttachFailureOnce("the shown map widget is not a UIPanelAPI, so it can adopt "
                + "no child panel");
            return;
        }

        var sector = Global.getSector();
        var faction = sector == null ? null : sector.getPlayerFaction();
        if (faction == null) {
            return;
        }

        // The player's own UI colours, so the widgets are asked to wear the look the surrounding
        // chrome wears. Whether they actually do is one of the things the run has to show.
        var baseColour = faction.getBaseUIColor();
        var darkColour = faction.getDarkUIColor();

        var panel = Global.getSettings().createCustom(
            PANEL_WIDTH, PANEL_HEIGHT, new VanillaMapPanelProbePlugin());
        var element = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);

        // A heading, a cut-corner button and an area checkbox: between them they stand for the
        // three things the GL toolkit hand-draws - styled text, the tab-shaped control, and the
        // filled toggle. The button takes the same cut style vanilla gives the map's own tabs.
        element.addSectionHeading(
            "KMU vanilla panel probe", baseColour, darkColour, Alignment.MID, ELEMENT_PAD);
        element.addButton(
            "Cut-corner button",
            BUTTON_ID,
            baseColour,
            darkColour,
            Alignment.MID,
            CutStyle.TOP,
            CONTROL_WIDTH,
            BUTTON_HEIGHT,
            ELEMENT_PAD);
        element.addAreaCheckbox(
            "Area checkbox",
            AREA_CHECKBOX_ID,
            baseColour,
            darkColour,
            Color.WHITE,
            CONTROL_WIDTH,
            AREA_CHECKBOX_HEIGHT,
            ELEMENT_PAD);

        panel.addUIElement(element).inTL(0f, 0f);

        // Asked for explicitly: an element added to a custom panel is laid out, but the call that
        // makes it take input is separate, and input is half of what the probe is asking about.
        panel.updateUIElementSizeAndMakeItProcessInput(element);

        mapPanel.addComponent(panel).inTR(PANEL_INSET_X, PANEL_INSET_Y);

        // Last in the child list, which is what puts it over the map surface: the container draws
        // its children in order and offers input to them in reverse, so the panel has to be on top
        // to be seen and to be clicked.
        mapPanel.bringComponentToTop(panel);

        attachedMapTab = mapTab;
        hasLoggedAttachFailure = false;

        LOG.info("Vanilla map panel probe: attached a panel to " + mapTab.getClass().getName());
    }

    // Drops the held widget. It is discarded with the screen that built it, so the panel needs no
    // removal - only the probe's own reference has to go, or a later widget allocated at the same
    // identity could be taken for one already carrying a panel.
    private void forgetAttachment() {
        attachedMapTab = null;
    }

    // Reads the enablement constant through a call rather than testing it inline, so the disabled
    // branch stays live code the compiler still checks instead of being folded away - a reference
    // that has quietly stopped compiling is worth nothing on the day it is switched back on.
    private static boolean isPanelEnabled() {
        return IS_ENABLED;
    }

    private void logAttachFailureOnce(String reason) {
        if (hasLoggedAttachFailure) {
            return;
        }
        hasLoggedAttachFailure = true;
        LOG.warn("Vanilla map panel probe: cannot attach - " + reason);
    }
}
