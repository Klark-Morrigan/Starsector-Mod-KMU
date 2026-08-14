package kmu.maplayers.politicalmap.dominance.alliances;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The alliances view's recede adapter: the reusable {@link RecedeControl} bound to the view's own
 * non-allied recede set and placed under the caption {@code Non-allied factions are}, so the view
 * recedes every faction outside an alliance through its own Mute/Desaturate toggles - independent of
 * the filter recede the picker drives. It lives in the alliances package, beside the view that owns
 * it, because both the set and the caption - what this view recedes - are the view's own
 * choice; the checkbox shape and its wiring belong to the shared control.
 */
public final class AllianceBodyControls {

    private AllianceBodyControls() {
    }

    /**
     * @return the alliances view's recede control captioned {@code Non-allied factions are}: the
     *         Mute checkbox (lit when non-allied factions are dimmed) and the Desaturate checkbox
     *         (lit when they are recoloured to the desaturation profile)
     */
    public static List<ControlSpec> buildControls() {
        return RecedeControl.buildControls(
            RecedePreferences.ALLIANCE_NON_ALLIED,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION));
    }
}
