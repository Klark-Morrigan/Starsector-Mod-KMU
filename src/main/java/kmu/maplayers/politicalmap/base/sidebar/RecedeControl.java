package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The reusable recede control: a caption line and the Mute and Desaturate checkboxes that drive the
 * shared {@link RecedePreferences}, laid out as {@code <caption>  [ ] Muted  [ ] Desaturated}. Every
 * context that recedes background ground contributes this same control under its own caption, so one
 * control and one toggle set back them all.
 *
 * <p>The caller supplies only the caption - what its context calls the ground being receded - while
 * the two checkbox labels and their toggle wiring are fixed here, so the recede reads and behaves
 * identically wherever it is placed. Each checkbox reads its lit state live from the shared
 * preferences when the spec is built (specs are rebuilt each frame), so a flip - from this checkbox
 * or a reload of the save's stored choice - shows at once.
 */
public final class RecedeControl {

    private RecedeControl() {
    }

    /**
     * @param captionLabel the resolved caption naming the ground this context recedes, shown ahead
     *                     of the two checkboxes
     * @return the recede control, top to bottom: the caption, the Mute checkbox (lit when receded
     *         ground is dimmed), and the Desaturate checkbox (lit when it is recoloured)
     */
    public static List<ControlSpec> buildControls(String captionLabel) {
        return List.of(
                ControlSpec.createLabel(captionLabel),
                ControlSpec.createCheckbox(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED),
                        RecedePreferences.isMuted(),
                        cellIndex -> toggleMuted()),
                ControlSpec.createCheckbox(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED),
                        RecedePreferences.isDesaturated(),
                        cellIndex -> toggleDesaturated()));
    }

    // Flips the Mute toggle to the opposite of its current state, so the checkbox is a plain on/off.
    // The setter persists the choice and requests the overlay repaint.
    private static void toggleMuted() {
        RecedePreferences.setMuted(!RecedePreferences.isMuted());
    }

    // Flips the Desaturate toggle to the opposite of its current state, matching the Mute checkbox.
    private static void toggleDesaturated() {
        RecedePreferences.setDesaturated(!RecedePreferences.isDesaturated());
    }
}
