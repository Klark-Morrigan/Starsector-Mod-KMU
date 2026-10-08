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
 * source of which owners are selectable now. {@link FilterSelection} stays ignorant of views (it
 * takes only a predicate); this supplies that predicate from the active view's
 * {@link OwnerPaintedView#resolveOwnerPickerRead}, so an owner that lapsed since the spotlight was
 * set - an owner removed, a group dissolved, a settings knob hiding the last systems it held -
 * clears the dangling filter instead of spotlighting an owner no longer on the map.
 *
 * <p>Only heals a screen while that screen has a view selected. A filter may persist while the
 * layer is toggled off, and with no selected view there is no view to judge which owners are
 * selectable - one view's owner ID would be meaningless under another view's read - so a persisted
 * filter is left intact until a view is up to validate it. It lives in this tier beside the view
 * registry it reads, since resolving "the active view and its selectable owners" is a view concern
 * the framework's {@link FilterSelection} deliberately does not carry - and it is handed the
 * registry of the layer it heals, so one layer's heal judges only that layer's spotlights.
 *
 * <p>The sector is taken from the caller wherever one holds it. A settings change is handed none, so
 * that entry resolves the running game's sector once, at the entry, and passes it down like any other
 * caller's.
 */
public final class FilterSelectionHeal {

    private FilterSelectionHeal() {
    }

    /**
     * Clears every screen's stored spotlight selection in that screen's selected view when the
     * view no longer offers it, a no-op for a screen with no view selected or whose stored owner is
     * still selectable. Runs on game load, on a view switch, and on a settings change, before the
     * overlay repaints, so it clears without requesting a refresh - each of those already
     * repaints, so there is nothing extra to invalidate. Reads the player's current holding and
     * visibility settings, the same gate the picker lists owners under, so an owner is healed away
     * exactly when it would no longer appear in the picker.
     *
     * <p>Every screen rather than the one being looked at, because what lapsed is a fact about the
     * sector: an owner removed or a group dissolved is gone from both panels' pickers, and a screen
     * healed only when it is next up would go on receding the sector behind an owner the player
     * cannot unpick from the panel they are on. The screens are walked off
     * {@link MapLayerScreens}, which is where how many there are is known.
     *
     * <p>Each screen is judged under its own selected view, since the view is that screen's pick
     * as much as the spotlight is: a slot holding an owner ID is stale or sound according to the
     * view that panel is set to, and judging it under the other panel's view would clear a
     * spotlight that is perfectly live where it was set.
     *
     * <p>Costs nothing while no owner is spotlighted. Judging selectability is a whole walk
     * of the sector, so it is left inside the predicate rather than prepared for it:
     * {@link FilterSelection#healStaleSelection} asks only when a stored ID is there to judge, which
     * is the minority of the calls, since every settings change arrives here - and the second
     * screen's call asks nothing at all unless it too has a spotlight of its own.
     *
     * @param sector       the sector whose owners decide which owners are still offered; null
     *                     heals nothing, since against no sector every stored owner would read as
     *                     lapsed
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

            // No view selected on this panel means no view to judge its slot under, so its stored
            // spotlight is left for the next view selected there to rule on - the other panel is still
            // healed.
            if (view == null) {
                continue;
            }
            FilterSelection.healStaleSelection(
                new SelectionSlot(
                    new ScreenSelectionSlot(KmuMod.MAP_STORE_NAMESPACE, screenPicks.memoryScope()),
                    view.getId()),
                spotlitOwnerId -> isOwnerOfferedBy(view, sector, spotlitOwnerId));
        }
    }

    /**
     * {@link #healStaleSelectionAgainstActiveView} for a caller the game hands no sector - a settings
     * change - judged against the sector running at the moment of the call.
     *
     * @param viewRegistry the layer whose spotlights are healed, judged under that layer's own views
     */
    public static void healStaleSelectionAgainstLiveSector(MapLayerViewRegistry viewRegistry) {
        healStaleSelectionAgainstActiveView(Global.getSector(), viewRegistry);
    }

    /**
     * Registers the heal against this mod's settings, so a knob that takes an owner off the picker
     * takes its spotlight with it - a visibility override switched off can leave an owner holding
     * no visible system, and the stored spotlight would otherwise go on receding the sector behind
     * an owner the player can no longer unpick. Each change is judged against the sector running
     * when it fires.
     *
     * <p>Call once per layer at application load: every call adds another listener.
     *
     * @param viewRegistry the layer whose spotlights are healed
     */
    public static void installHealOnSettingsChange(MapLayerViewRegistry viewRegistry) {
        KmuLunaSettings.runOnSettingsChange(
            () -> healStaleSelectionAgainstLiveSector(viewRegistry));
    }

    // Whether the view still lists one owner. Asked through the picker seam rather than the owner
    // identity: the match is on the ID alone, so it needs nothing a view's own metrics carry and
    // stays valid for any of them. That is also why the view's sort vocabulary is passed over, and
    // the presence beside the rows with it - a heal ranks nothing and lights nothing.
    private static boolean isOwnerOfferedBy(OwnerPaintedView view, SectorAPI sector, String ownerId) {

        for (var owner : view.resolveOwnerPickerRead(sector).picker().items()) {
            if (owner.itemId().equals(ownerId)) {
                return true;
            }
        }
        return false;
    }
}
