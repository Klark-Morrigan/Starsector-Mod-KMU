package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The reusable recede control: a caption line and the Mute and Desaturate checkboxes that drive a
 * {@link RecedePreferences} set, laid out as {@code <caption>  [ ] Muted  [ ] Desaturated}. Every
 * context that recedes background ground contributes this same control - the filter picker and the
 * alliances view each build it - so one control shape and one toggle-wiring live in one place.
 *
 * <p>The caller supplies the target preferences set - which ground its context recedes - and the
 * caption naming it, while the two checkbox labels and their toggle wiring are fixed here, so the
 * recede reads and behaves identically wherever it is placed and only the set it drives differs. Each
 * checkbox reads its lit state live from the passed set when the spec is built (specs are rebuilt each
 * frame), so a flip - from this checkbox or a reload of the save's stored choice - shows at once.
 */
public final class RecedeControl {

    private RecedeControl() {
    }

    /**
     * @param preferences  the recede set this control drives - the filter recede or the alliances
     *                     view's non-allied recede - read for its lit state and written on a click
     * @param captionLabel the resolved caption naming the ground this context recedes, shown ahead of
     *                     the two checkboxes
     * @return the recede control, top to bottom: the caption, the Mute checkbox (lit when the set's
     *         receded ground is dimmed), and the Desaturate checkbox (lit when it is recoloured)
     */
    public static List<ControlSpec> buildControls(RecedePreferences preferences, String captionLabel) {
        return List.of(
                new ControlSpec.Label(captionLabel),
                ControlSpec.Checkbox.lit(
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED),
                        preferences.isMuted(),
                        cellIndex -> toggleMuted(preferences)),
                ControlSpec.Checkbox.lit(
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED),
                        preferences.isDesaturated(),
                        cellIndex -> toggleDesaturated(preferences)));
    }

    // Flips the set's Mute toggle to the opposite of its current state, so the checkbox is a plain
    // on/off. The setter persists the choice and requests the overlay repaint.
    private static void toggleMuted(RecedePreferences preferences) {
        preferences.setMuted(!preferences.isMuted());
    }

    // Flips the set's Desaturate toggle to the opposite of its current state, matching the Mute
    // checkbox.
    private static void toggleDesaturated(RecedePreferences preferences) {
        preferences.setDesaturated(!preferences.isDesaturated());
    }
}
