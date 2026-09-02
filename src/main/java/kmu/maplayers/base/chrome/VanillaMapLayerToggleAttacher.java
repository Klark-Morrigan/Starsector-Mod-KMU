package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterToggle;

import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.settings.KmuMapLayerSettings;
import kmu.starsector.nexerelin.NexerelinAlliances;
import kmu.util.KmuStrings;

import java.util.function.IntSupplier;

/**
 * The attachment a running game gets: a tick box appended to the game's own filter row, sized off
 * that row's existing buttons, wearing the words the mod ships for it and answering the key the
 * player has bound to it.
 *
 * <p>Which words and which key are named here rather than passed in, because this is the one part
 * of the attachment that knows the control is the map layers'. Everything under it is a box on a row
 * and would read the same for any control put there. Where each is declared is not this class's
 * business: the words are a string id and the key a settings accessor, both owned by the classes
 * that own every other one of their kind.
 *
 * <p>Both are asked at each attachment rather than once, so a control built after the player
 * switches language is labelled in the language they switched to, and one built after they rebind
 * the key answers the key they bound - on the next screen they open rather than at the next load.
 *
 * <p>The same reasoning puts the presence check here rather than deeper: this is the concrete end
 * of the port, so it is where naming what is installed belongs, and everything under it goes on
 * knowing only that a box has words, a key and a hover.
 */
public final class VanillaMapLayerToggleAttacher implements MapLayerToggleAttacher {

    // Which key the box answers, asked afresh at each attachment. A supplier rather than the read
    // itself so the settings substrate stays one hop away, the way the alliance check does.
    private final IntSupplier shortcutKeycode;

    // What the box says on hover. One instance rather than one per attachment: it holds no state
    // about any particular box, and reads everything it says at the moment it is asked.
    private final MapLayerToggleTooltip toggleTooltip;

    /** Reads the live install - the pairing a running game gets. */
    public VanillaMapLayerToggleAttacher() {
        this(
            KmuMapLayerSettings::getMapFilterRowToggleShortcut,
            new MapLayerToggleTooltip(NexerelinAlliances::isAvailable));
    }

    VanillaMapLayerToggleAttacher(
            IntSupplier shortcutKeycode,
            MapLayerToggleTooltip toggleTooltip) {
        this.shortcutKeycode = shortcutKeycode;
        this.toggleTooltip = toggleTooltip;
    }

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

        // A key nothing had to arrange the scope of: it is live on the screens this box stands on
        // and nowhere else, which is the same shape as the box itself being what a stored hide waits
        // for. An unbound reading leaves it keyless rather than binding the cleared code.
        toggle.bindShortcut(shortcutKeycode.getAsInt());

        // Last, and unchecked: the hover is what the box says rather than part of what it does, so
        // a substrate that declines to hang one costs the words and leaves a working control.
        toggleTooltip.attachTo(toggle);

        return true;
    }
}
