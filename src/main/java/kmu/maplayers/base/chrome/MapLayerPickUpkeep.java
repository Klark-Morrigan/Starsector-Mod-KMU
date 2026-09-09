package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import kmlib.logging.SessionWarning;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenLayerTabs;

import org.apache.log4j.Logger;

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
 *
 * <p>Nothing here can take the game down with it. The row it reads is every registered layer's, which
 * on an install with a foreign mod's layer on the bar means calling a stranger's {@code getId} sixty
 * times a second - and this pass runs on every frame rather than only where the bar is drawn, so a
 * throw would reach the player nowhere near the map they could connect it to. A failed frame resolves
 * to no heal, one line in the log, and a bar behaving as it did before the pass existed.
 */
public final class MapLayerPickUpkeep implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(MapLayerPickUpkeep.class);

    private final Supplier<List<ScreenLayerPicks>> resolveEveryScreenPicks;

    // Says once per session that the heal could not be asked, rather than on every frame - a roster
    // that throws throws again on the next frame, and sixty lines a second would bury the one that
    // said what happened.
    private final SessionWarning warning = new SessionWarning(LOG);

    /** Reads the live screens - the pairing a running game gets. */
    public MapLayerPickUpkeep() {
        this(MapLayerScreens::getAllScreenPicks);
    }

    MapLayerPickUpkeep(Supplier<List<ScreenLayerPicks>> resolveEveryScreenPicks) {
        this.resolveEveryScreenPicks = resolveEveryScreenPicks;
    }

    @Override
    public void advance(float amount) {

        try {
            healEveryScreensPick();

        } catch (RuntimeException failure) {

            warning.warnOnce(
                "The map layer bar's picks could not be checked against the tabs on offer; a tab "
                    + "taken off the bar may leave the map it painted showing with no tab lit.",
                failure);
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

    private void healEveryScreensPick() {

        for (var screenPicks : resolveEveryScreenPicks.get()) {
            ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);
        }
    }
}
