package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.EveryFrameScript;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenLayerTabs;

import java.util.List;
import java.util.function.Supplier;

/**
 * Keeps every screen's active-layer pick on a tab that screen actually offers, and that screen's
 * show-or-hide control saying what the tab it lands on paints.
 *
 * <p>The two ways the bar and the map come to disagree are both a few clicks away in the arranging
 * dialog: a tab taken off the layer a screen is painting leaves that layer painting with no tab lit,
 * and putting it back on a screen whose own control has taken the empty view's tab over leaves the
 * only tab there is standing unlit over an empty map. Both are one question - is the pick among the
 * tabs on offer - and {@link ScreenLayerTabs#healPickOntoOfferedTabs} is the answer.
 *
 * <p>A standing pass rather than a write at the moment a row changes, for three reasons that no one
 * writing site covers: one dialog moves both screens' rows at once, a screen nobody is looking at
 * still has to be right when they next look, and the pass that lays a row out is a layout and may not
 * write. Asked afresh each frame, so whatever moved a row - the dialog, a mod registering a layer, a
 * box standing on a screen's own chrome - needs to know nothing about picks.
 *
 * <p>Every screen rather than the one showing, for the same reason it is a pass at all: which screen
 * is up is not what changed, and a screen healed only once it is next opened would be drawn for its
 * first frames from a pick its bar does not offer.
 *
 * <p>It runs while paused, every screen the bar draws on pausing the campaign - a pass standing down
 * while paused would never run on a frame where a row could have moved under it.
 *
 * <p>Costs a row read and a pick read per screen per frame, and writes only when the two disagree. A
 * heal that moved a pick leaves it on a tab the row offers, so the next frame finds nothing to do.
 */
public final class MapLayerPickUpkeep implements EveryFrameScript {

    // The picks of every screen the mod knows, since one dialog moves both screens' rows at once and
    // neither has to be the one on show for its pick to be left stranded.
    private final Supplier<List<ScreenLayerPicks>> resolveEveryScreenPicks;

    /** Reads the live screens - the pairing a running game gets. */
    public MapLayerPickUpkeep() {
        this(MapLayerScreens::getAllScreenPicks);
    }

    MapLayerPickUpkeep(Supplier<List<ScreenLayerPicks>> resolveEveryScreenPicks) {
        this.resolveEveryScreenPicks = resolveEveryScreenPicks;
    }

    @Override
    public void advance(float amount) {

        for (var screenPicks : resolveEveryScreenPicks.get()) {
            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);
        }
    }

    @Override
    public boolean isDone() {
        // Rows go on moving for as long as the player keeps arranging their bar.
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        // Every screen the bar draws on pauses the campaign, so a pass standing down while paused
        // would never see a row the player just rearranged.
        return true;
    }
}
