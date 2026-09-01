package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterToggle;

import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.util.KmuStrings;

/**
 * The attachment a running game gets: a tick box appended to the game's own filter row, sized off
 * that row's existing buttons, wearing the words the mod ships for it.
 *
 * <p>The wording is read here rather than passed in, because this is the one part of the attachment
 * that knows the control is the map layers'. Everything under it is a box on a row and would read
 * the same for any control put there.
 *
 * <p>Read at each attachment rather than once, so a control built after the player switches
 * language is labelled in the language they switched to.
 */
public final class VanillaMapLayerToggleAttacher implements MapLayerToggleAttacher {

    @Override
    public boolean attachToggleTo(MapFilterRow row, MapLayerVisibility layerVisibility) {

        // The click has to report what the box now shows, and the box is only made by the call that
        // takes the click handler - so the handle is put in a one-slot carrier the handler reads
        // rather than closes over. Nothing can click a button that is not yet on a row, so the slot
        // is filled by the time anything reads it.
        var attachedToggle = new MapFilterToggle[1];

        var toggle = MapFilterToggle.appendToRow(
            row,
            KmuStrings.get(KmuStrings.MAP_LAYER_CTL_FILTER_ROW_TOGGLE),
            () -> layerVisibility.showLayers(attachedToggle[0].isChecked()));

        // No box, for whichever of the row's own reasons - already said where it happened, and the
        // same answer here whichever it was.
        if (toggle == null) {
            return false;
        }
        attachedToggle[0] = toggle;

        // Seeded rather than left at whatever a fresh button starts at: a screen reopened with its
        // layers hidden would otherwise show a ticked box over an empty map.
        toggle.setChecked(layerVisibility.areLayersShown());

        return true;
    }
}
