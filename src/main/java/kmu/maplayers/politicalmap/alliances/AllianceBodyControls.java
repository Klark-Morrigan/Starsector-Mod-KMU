package kmu.maplayers.politicalmap.alliances;

import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
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
    public static List<SidebarControlSpec> buildControls() {
        return List.of(
                SidebarControlSpec.createLabel(
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION)),
                new SidebarControlSpec(SidebarControlKind.CHECKBOX,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED)), "",
                        mutedCheckboxState(), cellIndex -> toggleNonAlliedMuted()),
                new SidebarControlSpec(SidebarControlKind.CHECKBOX,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED)), "",
                        desaturatedCheckboxState(), cellIndex -> toggleNonAlliedDesaturated()));
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

    // The Mute checkbox is lit (cell 0) when non-allied factions are dimmed, off otherwise - a
    // single-cell control is either its one lit cell or NO_SELECTION.
    private static int mutedCheckboxState() {
        return AllianceStylePreferences.isNonAlliedMuted() ? 0 : SidebarControlSpec.NO_SELECTION;
    }

    // The Desaturate checkbox is lit (cell 0) when non-allied factions are recoloured, off otherwise.
    private static int desaturatedCheckboxState() {
        return AllianceStylePreferences.isNonAlliedDesaturated()
                ? 0
                : SidebarControlSpec.NO_SELECTION;
    }
}
