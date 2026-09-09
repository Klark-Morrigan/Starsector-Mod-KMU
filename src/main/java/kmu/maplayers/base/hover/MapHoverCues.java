package kmu.maplayers.base.hover;

import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;

import kmu.settings.KmuMapSoundSettings;

/**
 * What the map answers the cursor with audibly: the tick as a new cell arrives under it, at the level
 * the player set for it.
 *
 * <p>Beside {@link MapHoverGates} and read the same way - live, per moment - so a slider moved on the
 * settings screen is heard on the next cell the cursor reaches rather than after a restart. Composing
 * the pair here rather than at the moment's detector is what keeps the detector from naming a sound or
 * a level of its own, both being the look's to state.
 *
 * <p>The map's own cue rather than one of the sidebar's, for all that both play through the same seam.
 * A cell is not a control: it is crossed rather than aimed at, it has no chrome the pointer lights, and
 * it answers with a different sample - so what the two share is the moment being answered at all and
 * not the balance the panel's arrivals are tuned against.
 */
public final class MapHoverCues {

    private MapHoverCues() {
    }

    /**
     * @return what the cursor reaching a new cell sounds like, or null once the player has pulled its
     *         level to the bottom - the library's own rule for a level composed from a slider, so the
     *         map states silence the way the sidebar does
     */
    public static UiSoundCue composeCellArrivalCue() {
        return UiSoundCue.createIfAudible(
            StarsectorUiSound.TEXT_TYPED,
            KmuMapSoundSettings.getMapCellArrivalVolume());
    }
}
