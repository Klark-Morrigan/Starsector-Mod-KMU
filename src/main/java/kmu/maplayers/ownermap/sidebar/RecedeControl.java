package kmu.maplayers.ownermap.sidebar;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.controls.specs.CheckboxSpec;
import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.controls.specs.LabelSpec;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.ownermap.preferences.RecedePreferences;
import kmu.util.KmuStringKeys;

import java.util.List;

/**
 * The reusable recede control: a caption line and the Mute and Desaturate checkboxes that drive a
 * {@link RecedePreferences} set, laid out as {@code <caption>  [ ] Muted  [ ] Desaturated}. Every
 * context that recedes a backdrop contributes this same control - the filter picker and any view
 * with a backdrop of its own each build it - so one control shape and one toggle-wiring live in one place.
 *
 * <p>The caller supplies the target preferences set - what its context recedes - the caption naming
 * it, and the panel it is being placed on, while the two checkbox labels and their toggle wiring are
 * fixed here, so the recede reads and behaves identically wherever it is placed and only the set it
 * drives differs. Each checkbox reads its lit state live from the passed set when the spec is built
 * (specs are rebuilt each frame), so a flip - from this checkbox or a reload of the save's stored
 * choice - shows at once.
 */
public final class RecedeControl {

    private RecedeControl() {
    }

    /**
     * @param preferences  the recede set this control drives - the filter recede or a view's own
     *                     backdrop recede - read for its lit state and written on a click
     * @param captionLabel the resolved caption naming what this context recedes, shown ahead of
     *                     the two checkboxes
     * @param target       the panel this control was placed on, carried into both checkboxes so a flip
     *                     repaints that sector's backdrop and is stored as that screen's own
     * @return the recede control, top to bottom: the caption, the Mute checkbox (lit when the set's
     *         receded backdrop is dimmed on that screen), and the Desaturate checkbox (lit when it is
     *         recoloured there)
     */
    public static List<ControlSpec> buildControls(
            RecedePreferences preferences,
            String captionLabel,
            BodyControlTarget target) {

        // The caption and both boxes read in the engine's plain text tone - the control block calls
        // nothing out, so every run of it is the same one colour, resolved once here.
        var textColour = StarsectorUiColour.VANILLA_TEXT.resolve();

        return List.of(
            LabelSpec.createLabel(new TextSpan(captionLabel, textColour)),
            CheckboxSpec.lit(
                new TextSpan(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_MUTED), textColour),
                preferences.isMuted(target.memoryScope()),
                cellIndex -> toggleMuted(preferences, target)),
            CheckboxSpec.lit(
                new TextSpan(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_DESATURATED), textColour),
                preferences.isDesaturated(target.memoryScope()),
                cellIndex -> toggleDesaturated(preferences, target)));
    }

    // Flips the set's Mute toggle to the opposite of its current state on the panel this control was
    // placed on, so the checkbox is a plain on/off. This is where the panel's screen stops being carried
    // and becomes the address of the slot written.
    private static void toggleMuted(RecedePreferences preferences, BodyControlTarget target) {

        preferences.setMuted(
            target.memoryScope(),
            !preferences.isMuted(target.memoryScope()),
            target.board());
    }

    // Flips the set's Desaturate toggle to the opposite of its current state, matching the Mute
    // checkbox.
    private static void toggleDesaturated(RecedePreferences preferences, BodyControlTarget target) {

        preferences.setDesaturated(
            target.memoryScope(),
            !preferences.isDesaturated(target.memoryScope()),
            target.board());
    }
}
