package kmu.maplayers.ownermap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.KmuMod;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.sidebar.ScreenSelectionSlot;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.settings.KmuLunaSettings;

/**
 * Heals a save's spotlight selections against the view that is active in it: the glue that binds
 * {@link FilterSelection#healStaleSelection}'s pure "clear if not selectable" rule to a concrete
 * source of which blocs are selectable now. {@link FilterSelection} stays ignorant of views (it takes
 * only a predicate); this supplies that predicate from the active view's {@link
 * OwnerPaintedView#resolveBlocPickerRead}, so a bloc that lapsed since the spotlight was set - a faction
 * removed, a group dissolved, a settings knob hiding the last colonies it was listed for - clears
 * the dangling filter instead of spotlighting a bloc no longer on the map.
 *
 * <p>Only heals a screen while that screen has a view selected. A filter may persist while the
 * layer is toggled off, and with no selected view there is no grouping to judge which blocs are
 * selectable - a faction ID would be meaningless under a grouped read and vice versa - so a persisted
 * filter is left intact until a view is up to validate it. It lives in this tier beside the view
 * registry it reads, since resolving "the active view and its selectable blocs" is a view concern the
 * framework's {@link FilterSelection} deliberately does not carry - and it is handed the registry of
 * the layer it heals, so one layer's heal judges only that layer's spotlights.
 *
 * <p>The sector is taken from the caller wherever one holds it. The entries driven by a settings
 * change or a sidebar click are handed none, so those resolve the running game's sector once, at the
 * entry, and pass it down like any other caller's.
 */
public final class FilterSelectionHeal {

    private FilterSelectionHeal() {
    }

    /**
     * Clears every screen's stored spotlight selection in that screen's selected view when the view no
     * longer offers it, a no-op for a screen with no view selected or whose stored bloc is still
     * selectable. Runs on game load, on a view switch, and on a settings change, before the overlay
     * repaints, so it clears without requesting a refresh - each of those already repaints, so there is
     * nothing extra to invalidate. Reads the player's current holding and visibility settings, the same
     * gate the picker lists blocs under, so a bloc is healed away exactly when it would no longer appear
     * in the picker.
     *
     * <p>Every screen rather than the one being looked at, because what lapsed is a fact about the
     * sector: a faction removed or a group dissolved is gone from both panels' pickers, and a
     * screen healed only when it is next up would go on receding the sector behind a bloc the player
     * cannot unpick from the panel they are on. The screens are walked off {@link MapLayerScreens},
     * which is where how many there are is known.
     *
     * <p>Each screen is judged under its own selected view, since the view is that screen's pick as
     * much as the spotlight is: a slot holding a faction ID is stale or sound according to the view
     * that panel is set to, and judging it under the other panel's view would clear a spotlight that
     * is perfectly live where it was set.
     *
     * <p>Costs nothing while no bloc is spotlighted. Judging selectability is a whole grouped
     * holder pass over the sector, so it is left inside the predicate rather than prepared for it:
     * {@link FilterSelection#healStaleSelection} asks only when a stored ID is there to judge, which
     * is the minority of the calls, since every settings change arrives here - and the second
     * screen's call asks nothing at all unless it too has a spotlight of its own.
     *
     * @param sector       the sector whose colonies decide which blocs are still offered; null heals
     *                     nothing, since against no sector every stored bloc would read as lapsed
     * @param viewRegistry the layer whose spotlights are healed, judged under that layer's own views
     */
    public static void healStaleSelectionAgainstActiveView(
            SectorAPI sector,
            MapLayerViewRegistry viewRegistry) {

        if (sector == null) {
            return;
        }
        for (var screenPicks : MapLayerScreens.getAllScreenPicks()) {

            var view = viewRegistry.getSelectedView(screenPicks.memoryScope());

            // No view selected on this panel means no grouping to judge its slot under, so its stored
            // spotlight is left for the next view selected there to rule on - the other panel is still
            // healed.
            if (view == null) {
                continue;
            }
            FilterSelection.healStaleSelection(
                new SelectionSlot(
                    new ScreenSelectionSlot(KmuMod.MAP_STORE_NAMESPACE, screenPicks.memoryScope()),
                    view.getId()),
                spotlitBlocId -> isBlocOfferedBy(view, sector, spotlitBlocId));
        }
    }

    /**
     * {@link #healStaleSelectionAgainstActiveView} for a caller the game hands no sector - a settings
     * change, a sidebar click - judged against the sector running at the moment of the call.
     *
     * @param viewRegistry the layer whose spotlights are healed, judged under that layer's own views
     */
    public static void healStaleSelectionAgainstLiveSector(MapLayerViewRegistry viewRegistry) {
        healStaleSelectionAgainstActiveView(Global.getSector(), viewRegistry);
    }

    /**
     * Registers the heal against this mod's settings, so a knob that takes a bloc off the picker takes
     * its spotlight with it - a visibility override switched off can leave a faction with no visible
     * inhabited market, and the stored spotlight would otherwise go on receding the sector behind a
     * bloc the player can no longer unpick. Each change is judged against the sector running when it
     * fires.
     *
     * <p>Call once per layer at application load: every call adds another listener.
     *
     * @param viewRegistry the layer whose spotlights are healed
     */
    public static void installHealOnSettingsChange(MapLayerViewRegistry viewRegistry) {
        KmuLunaSettings.runOnSettingsChange(
            () -> healStaleSelectionAgainstLiveSector(viewRegistry));
    }

    // Whether the view still lists one bloc. Asked through the picker seam rather than the bloc
    // identity: the match is on the ID alone, so it needs nothing a view's own metrics carry and
    // stays valid for any of them. That is also why the view's sort vocabulary is passed over, and
    // the presence beside the rows with it - a heal ranks nothing and lights nothing.
    private static boolean isBlocOfferedBy(OwnerPaintedView view, SectorAPI sector, String blocId) {

        for (var bloc : view.resolveBlocPickerRead(sector).picker().items()) {
            if (bloc.itemId().equals(blocId)) {
                return true;
            }
        }
        return false;
    }
}
