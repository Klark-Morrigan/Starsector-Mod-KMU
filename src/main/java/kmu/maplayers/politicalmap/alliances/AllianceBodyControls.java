package kmu.maplayers.politicalmap.alliances;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.util.KmuStrings;

import java.util.List;

/**
 * The alliances view's own body controls: a caption line and the two checkboxes that decide how the
 * view recedes every faction outside an alliance - {@code Non-allied factions are  [ ] Muted  [ ]
 * Desaturated}. They live in the alliances package, beside the view that owns them, since they are
 * meaningful only under it; {@link PoliticalMapBodyControls} stays the view-agnostic shared
 * sub-options every view honours.
 *
 * <p>Each checkbox reads its lit state live from {@link AllianceStylePreferences} when the spec is
 * built (specs are rebuilt each frame), so a flip - whether from this checkbox or a reload of the
 * save's stored choice - shows at once; its action flips the matching per-save toggle, which
 * persists the choice and repaints the overlay.
 */
public final class AllianceBodyControls {

    private AllianceBodyControls() {
    }

    /**
     * @return the alliances view's controls, top to bottom: the {@code Non-allied factions are}
     *         caption, the Mute checkbox (lit when non-allied factions are dimmed), and the
     *         Desaturate checkbox (lit when they are recoloured to the desaturation profile)
     */
    public static List<ControlSpec> buildControls() {
        return List.of(
                ControlSpec.createLabel(
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION)),
                ControlSpec.createCheckbox(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED),
                        AllianceStylePreferences.isNonAlliedMuted(),
                        cellIndex -> toggleNonAlliedMuted()),
                ControlSpec.createCheckbox(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED),
                        AllianceStylePreferences.isNonAlliedDesaturated(),
                        cellIndex -> toggleNonAlliedDesaturated()));
    }

    // Flips the Mute toggle to the opposite of its current state, so the checkbox is a plain on/off.
    // The setter persists the choice and requests the overlay repaint.
    private static void toggleNonAlliedMuted() {
        AllianceStylePreferences.setNonAlliedMuted(!AllianceStylePreferences.isNonAlliedMuted());
    }

    // Flips the Desaturate toggle to the opposite of its current state, matching the Mute checkbox.
    private static void toggleNonAlliedDesaturated() {
        AllianceStylePreferences.setNonAlliedDesaturated(
                !AllianceStylePreferences.isNonAlliedDesaturated());
    }
}
