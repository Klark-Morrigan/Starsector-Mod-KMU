package kmu.maplayers.politicalmap.alliances;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The alliances view's recede adapter: the shared {@link RecedeControl} placed under the caption
 * {@code Non-allied factions are}, so the view recedes every faction outside an alliance through the
 * same Mute/Desaturate toggles every other context uses. It lives in the alliances package, beside
 * the view that owns it, because the caption - which ground this view recedes - is the view's own
 * choice; the checkboxes and their wiring belong to the shared control.
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
                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION));
    }
}
