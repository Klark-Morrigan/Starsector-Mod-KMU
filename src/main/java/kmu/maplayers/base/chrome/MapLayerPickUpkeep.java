package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.EveryFrameScript;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenLayerTabs;

import java.util.List;
import java.util.function.Supplier;

/**
 * Asks {@link ScreenLayerTabs#healPickOntoOfferedTabs} of every screen, every frame - the rule being
 * that class's, and when it is owed being this one's.
 *
 * <p>A standing pass rather than a write wherever a row moves, for three reasons no one writing site
 * covers: one dialog moves both screens' rows at once, a screen nobody is looking at still has to be
 * right when they next look, and the pass that lays a row out is a layout and may not write. Every
 * screen for the middle one of those, and a frame at a time for the rest - so whatever moved a row,
 * the dialog or a mod registering a layer or a box standing on a screen's own chrome, needs to know
 * nothing about picks.
 *
 * <p>Costs a row read and a pick read per screen per frame, and writes only where the two disagree. A
 * heal leaves the pick on a tab the row offers, so the frame after one finds nothing to do.
 */
public final class MapLayerPickUpkeep implements EveryFrameScript {

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
