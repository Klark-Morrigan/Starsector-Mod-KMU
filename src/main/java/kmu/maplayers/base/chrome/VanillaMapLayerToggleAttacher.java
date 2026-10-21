package kmu.maplayers.base.chrome;

import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterToggle;

import kmu.maplayers.base.compatibility.MapLayerGameReach;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuMapStringKeys;
import kmu.util.KmuStringKeys;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The attachment a running game gets: a tick box appended to the game's own filter row, sized off
 * that row's existing buttons, wearing the words the mod ships for it and answering the key the
 * player has bound to it.
 *
 * <p>Which words and which key are named here rather than passed in, because this is the one part
 * of the attachment that knows the control is the map layers'. Everything under it is a box on a row
 * and would read the same for any control put there. Where each is declared is not this class's
 * business: the words are a string ID and the key a settings accessor, both owned by the classes
 * that own every other one of their kind.
 *
 * <p>Both are asked at each attachment rather than once, so a control built after the player
 * switches language is labelled in the language they switched to, and one built after they rebind
 * the key answers the key they bound - on the next screen they open rather than at the next load.
 *
 * <p>Whether the political map offers an alliances view is the one thing the hover says that this
 * framework cannot know: it turns on which mods are installed. So the composition root, which is the
 * one place that names them, registers the answer here, and everything under this class goes on
 * knowing only that a box has words, a key and a hover.
 */
public final class VanillaMapLayerToggleAttacher
        implements MapLayerToggleAttacher {

    // Whether the political map offers an alliances view, as the composition root registered it at
    // application load. No alliances view until then, which is what an install without the mod
    // that keeps alliances reads.
    private static BooleanSupplier allianceViewAvailability = () -> false;

    // Which key the box answers, asked afresh at each attachment. A supplier rather than the read
    // itself so the settings substrate stays one hop away, the way the alliance check does.
    private final IntSupplier shortcutKeycode;

    // What the box says on hover. One instance rather than one per attachment: it holds no state
    // about any particular box, and reads everything it says at the moment it is asked.
    private final MapLayerToggleTooltip toggleTooltip;

    /** Reads the live install - the pairing a running game gets. */
    public VanillaMapLayerToggleAttacher() {

        this(
            KmuMapKeybindSettings::getMapFilterRowToggleShortcut,
            new MapLayerToggleTooltip(() -> allianceViewAvailability.getAsBoolean()));
    }

    VanillaMapLayerToggleAttacher(
            IntSupplier shortcutKeycode,
            MapLayerToggleTooltip toggleTooltip) {

        this.shortcutKeycode = shortcutKeycode;
        this.toggleTooltip = toggleTooltip;
    }

    /**
     * Records whether the political map offers an alliances view, so the hover lists it. Called once
     * by the composition root at application load, before any map screen can open.
     *
     * <p>Read through on every hover rather than copied, so a box attached before the registration
     * still says what was registered.
     *
     * @param isAllianceViewOffered whether the alliances view is among the ones the political map
     *                              registered
     */
    public static void registerAllianceViewAvailability(BooleanSupplier isAllianceViewOffered) {
        allianceViewAvailability = isAllianceViewOffered;
    }

    @Override
    public MapFilterToggle attachToggleTo(MapFilterRow row, MapLayerVisibility layerVisibility) {

        // The click has to report what the box now shows, and the box is only made by the call that
        // takes the click handler - so the handle is put in a one-slot carrier the handler reads
        // rather than closes over. Nothing can click a button that is not yet on a row, so the slot
        // is filled by the time anything reads it.
        var attachedToggle = new MapFilterToggle[1];
        var toggle = MapFilterToggle.appendToRow(
            row,
            KmuStringKeys.get(KmuMapStringKeys.MAP_LAYER_CTL_FILTER_ROW_TOGGLE),
            () -> layerVisibility.showLayers(attachedToggle[0].isChecked()),
            MapLayerGameReach.FILTER_ROW_TOGGLE.getReporter());

        // No box, for whichever of the row's own reasons - already said where it happened, and the
        // same answer here whichever it was.
        if (toggle == null) {
            return null;
        }

        attachedToggle[0] = toggle;

        // A key nothing had to arrange the scope of: it is live on the screens this box stands on
        // and nowhere else, which is the same shape as the box itself being what a stored hide waits
        // for. An unbound reading leaves it keyless rather than binding the cleared code.
        toggle.bindShortcut(shortcutKeycode.getAsInt());

        // Last, and unchecked: the hover is what the box says rather than part of what it does, so
        // a substrate that declines to hang one costs the words and leaves a working control.
        toggleTooltip.attachTo(toggle);

        return toggle;
    }
}
