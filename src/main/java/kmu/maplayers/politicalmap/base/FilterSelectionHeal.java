package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.settings.KmuLunaSettings;

/**
 * Heals a save's spotlight selections against the view that is active in it: the glue that binds
 * {@link FilterSelection#healStaleSelection}'s pure "clear if not selectable" rule to a concrete
 * source of which blocs are selectable now. {@link FilterSelection} stays ignorant of views (it takes
 * only a predicate); this supplies that predicate from the active view's {@link
 * PoliticalMapView#resolveBlocPickerRead}, so a bloc that lapsed since the spotlight was set - a faction
 * removed, an alliance dissolved, a settings knob hiding the last colonies it was listed for - clears
 * the dangling filter instead of spotlighting a bloc no longer on the map.
 *
 * <p>Only heals while a view is selected. A filter may persist while the political map is toggled off,
 * and with no active view there is no grouping to judge which blocs are selectable - a faction id
 * would be meaningless under an alliance read and vice versa - so a persisted filter is left intact
 * until a view is up to validate it. It lives in {@code base} beside the view registry it reads,
 * since resolving "the active view and its selectable blocs" is a view concern the framework's
 * {@link FilterSelection} deliberately does not carry.
 */
public final class FilterSelectionHeal {

    private FilterSelectionHeal() {
    }

    /**
     * Clears every screen's stored spotlight selection in the active view when that view no longer
     * offers it, a no-op when no view is selected or a screen's stored bloc is still selectable. Runs
     * on game load, on a view switch, and on a settings change, before the overlay repaints, so it
     * clears without requesting a refresh - each of those already repaints, so there is nothing extra
     * to invalidate. Reads the live sector and the player's current dominance and visibility settings,
     * the same gate the picker lists blocs under, so a bloc is healed away exactly when it would no
     * longer appear in the picker.
     *
     * <p>Every screen rather than the one being looked at, because what lapsed is a fact about the
     * sector: a faction removed or an alliance dissolved is gone from both panels' pickers, and a
     * screen healed only when it is next up would go on receding the sector behind a bloc the player
     * cannot unpick from the panel they are on. The screens are walked off {@link MapLayerScreens},
     * which is where how many there are is known.
     *
     * <p>Costs nothing while no bloc is spotlighted. Judging selectability is a whole grouped
     * dominance pass over the sector, so it is left inside the predicate rather than prepared for it:
     * {@link FilterSelection#healStaleSelection} asks only when a stored id is there to judge, which
     * is the minority of the calls now that every settings change arrives here - and the second
     * screen's call asks nothing at all unless it too has a spotlight of its own.
     */
    public static void healStaleSelectionAgainstActiveView() {
        var view = PoliticalMapViewRegistry.getSelectedView();
        if (view == null) {
            return;
        }
        for (var screenPicks : MapLayerScreens.getAllScreenPicks()) {
            FilterSelection.healStaleSelection(
                new SelectionSlot(screenPicks.memoryScope(), view.getId()),
                spotlitBlocId -> isBlocOfferedBy(view, spotlitBlocId));
        }
    }

    /**
     * Registers the heal against this mod's settings, so a knob that takes a bloc off the picker takes
     * its spotlight with it - a visibility override switched off can leave a faction with no visible
     * inhabited market, and the stored spotlight would otherwise go on receding the sector behind a
     * bloc the player can no longer unpick.
     *
     * <p>Call once at application load: every call adds another listener.
     */
    public static void installHealOnSettingsChange() {
        KmuLunaSettings.runOnSettingsChange(
            FilterSelectionHeal::healStaleSelectionAgainstActiveView);
    }

    // Whether the view still lists one bloc. Asked through the picker seam rather than the bloc
    // identity: the match is on the id alone, so it needs nothing a view's own metrics carry and
    // stays valid for any of them. That is also why the view's sort vocabulary is passed over, and
    // the presence beside the rows with it - a heal ranks nothing and lights nothing.
    private static boolean isBlocOfferedBy(PoliticalMapView view, String blocId) {

        for (var bloc : view.resolveBlocPickerRead(Global.getSector()).picker().items()) {
            if (bloc.itemId().equals(blocId)) {
                return true;
            }
        }
        return false;
    }
}
