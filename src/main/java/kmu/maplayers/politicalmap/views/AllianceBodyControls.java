package kmu.maplayers.politicalmap.views;

import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.ownermap.sidebar.BodyControlTarget;
import kmu.maplayers.ownermap.sidebar.RecedeControl;
import kmu.util.KmuStringKeys;

import java.util.List;

/**
 * The alliances view's recede adapter: the reusable {@link RecedeControl} bound to the view's own
 * non-allied recede set and placed under the caption {@code Non-allied factions are}, so the view
 * recedes every faction outside an alliance through its own Mute/Desaturate toggles - independent of
 * the filter recede the picker drives. It lives in {@code views}, beside the view that owns it,
 * because both the set and the caption - what this view recedes - are the view's own choice; the
 * checkbox shape and its wiring belong to the shared control.
 */
public final class AllianceBodyControls {

    private AllianceBodyControls() {
    }

    /**
     * @param target the panel the tab is being built on, carried through to the two checkboxes so a flip
     *               repaints that sector's map and is filed as that screen's own
     * @return the alliances view's recede control captioned {@code Non-allied factions are}: the
     *         Mute checkbox (lit when non-allied factions are dimmed) and the Desaturate checkbox
     *         (lit when they are recoloured to the desaturation profile)
     */
    public static List<ControlSpec> buildControls(BodyControlTarget target) {
        return RecedeControl.buildControls(
            AlliancesView.NON_ALLIED_RECEDE,
            KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION),
            target);
    }
}
