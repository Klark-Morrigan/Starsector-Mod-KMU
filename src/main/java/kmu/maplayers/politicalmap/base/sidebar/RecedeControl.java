package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The reusable recede control: a caption line and the Mute and Desaturate checkboxes that drive a
 * {@link RecedePreferences} set, laid out as {@code <caption>  [ ] Muted  [ ] Desaturated}. Every
 * context that recedes a backdrop contributes this same control - the filter picker and the
 * alliances view each build it - so one control shape and one toggle-wiring live in one place.
 *
 * <p>The caller supplies the target preferences set - what its context recedes - the caption naming
 * it, the board a flip repaints through, and the screen the flip is filed under, while the two checkbox
 * labels and their toggle wiring are fixed here, so the recede reads and behaves identically wherever it
 * is placed and only the set it drives differs. Each
 * checkbox reads its lit state live from the passed set when the spec is built (specs are rebuilt each
 * frame), so a flip - from this checkbox or a reload of the save's stored choice - shows at once.
 */
public final class RecedeControl {

    private RecedeControl() {
    }

    /**
     * @param preferences  the recede set this control drives - the filter recede or the alliances
     *                     view's non-allied recede - read for its lit state and written on a click
     * @param captionLabel the resolved caption naming what this context recedes, shown ahead of
     *                     the two checkboxes
     * @param board        the refresh board of the sector this control was built for, carried into
     *                     both checkboxes so a flip repaints that sector's backdrop
     * @param memoryScope  the scope of the screen this control was placed on, carried into both
     *                     checkboxes so a flip is read back and stored as that panel's own
     * @return the recede control, top to bottom: the caption, the Mute checkbox (lit when the set's
     *         receded backdrop is dimmed on that screen), and the Desaturate checkbox (lit when it is
     *         recoloured there)
     */
    public static List<ControlSpec> buildControls(
            RecedePreferences preferences,
            String captionLabel,
            MapLayerRefreshBoard board,
            ScreenMemoryScope memoryScope) {

        // The caption and both boxes read in the engine's plain text tone - the control block calls
        // nothing out, so every run of it is the same one colour, resolved once here.
        var textColour = StarsectorUiColour.VANILLA_TEXT.resolve();

        return List.of(
            ControlSpec.Label.createLabel(new TextSpan(captionLabel, textColour)),
            ControlSpec.Checkbox.lit(
                new TextSpan(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED), textColour),
                preferences.isMuted(memoryScope),
                cellIndex -> toggleMuted(preferences, board, memoryScope)),
            ControlSpec.Checkbox.lit(
                new TextSpan(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED), textColour),
                preferences.isDesaturated(memoryScope),
                cellIndex -> toggleDesaturated(preferences, board, memoryScope)));
    }

    // Flips the set's Mute toggle to the opposite of its current state on the screen this control was
    // placed on, so the checkbox is a plain on/off. The setter persists the choice under that screen and
    // repaints through the board it is handed.
    private static void toggleMuted(
            RecedePreferences preferences,
            MapLayerRefreshBoard board,
            ScreenMemoryScope memoryScope) {

        preferences.setMuted(memoryScope, !preferences.isMuted(memoryScope), board);
    }

    // Flips the set's Desaturate toggle to the opposite of its current state, matching the Mute
    // checkbox.
    private static void toggleDesaturated(
            RecedePreferences preferences,
            MapLayerRefreshBoard board,
            ScreenMemoryScope memoryScope) {

        preferences.setDesaturated(memoryScope, !preferences.isDesaturated(memoryScope), board);
    }
}
