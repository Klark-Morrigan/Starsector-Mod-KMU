package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import kmlib.logging.SessionWarning;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.OfferedTabsRevision;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenLayerTabs;

import org.apache.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Asks {@link ScreenLayerTabs#healPickOntoOfferedTabs} of every screen whose bar has moved - the rule
 * being that class's, and when it is owed being this one's.
 *
 * <p>A standing pass rather than a write wherever a row moves, for three reasons no one writing site
 * covers: one dialog moves both screens' rows at once, a screen nobody is looking at still has to be
 * right when they next look, and the pass that lays a row out is a layout and may not write. Every
 * screen for the middle one of those, and a frame at a time for the rest - so whatever moved a row,
 * the dialog or a mod registering a layer or a box standing on a screen's own chrome, needs to know
 * nothing about picks.
 *
 * <p>Asked every frame is not the same as answered every frame. Building the offered row costs an
 * index of the roster by ID, three lists and two stream passes, which is not a thing to spend sixty
 * times a second for an answer that changes when the player opens a dialog. So each screen's
 * {@link OfferedTabsRevision} is held from the frame it was last settled at, and a frame whose
 * revision has not moved is two field reads and a comparison of two short lists. The revision is
 * built beside the row it describes, so an ingredient added to one is added to the other.
 *
 * <p>Held on the outcome and never on the attempt. The heal says whether the screen ended up on a tab
 * its row offers, and only that records the revision - a pick with nowhere to write drops the move
 * silently, and a pass that took having tried for having done it would leave that bar lit wrong for
 * as long as nobody touched the dialog. Unsettled means the next frame asks again.
 *
 * <p>What the revision deliberately leaves out is the pick itself, which would cost a save read per
 * screen per frame to include. Nothing moves a pick to a tab that is not offered: the bar selects
 * only tabs it draws, and this pass lands it only on tabs the row carries - so a pick that has
 * stopped being offered is a row that moved. A pick written straight into the save from outside the
 * bar is healed when the row next moves, or on the next load, this pass being installed per sector
 * and so starting each campaign with nothing held.
 *
 * <p>A failure is held the same way a success is, and that is the whole of what stops it repeating.
 * What the heal reads is every registered layer's, which on an install carrying a foreign mod's layer
 * means calling a stranger's {@code getId} - and unlike a control that cannot reach a row the game has
 * not built yet, a roster that throws throws again on the identical row. So the throwing row is
 * recorded as done with, which costs one attempt per row change rather than one per frame, and the
 * next row the player makes puts the screen back in play: whatever the fault was, it is not being
 * asked the same question again. Said once per session however often it happens, and the bar behaves
 * meanwhile as it did before the pass existed.
 */
public final class MapLayerPickUpkeep implements EveryFrameScript {

    // The news a failure carries, wherever it was caught: one message, so one line per session.
    private static final String PICKS_UNCHECKED_WARNING =
        "The map layer bar's picks could not be checked against the tabs on offer; a tab taken off "
            + "the bar may leave the map it painted showing with no tab lit. Arranging the bar again "
            + "is what asks afresh.";

    private static final Logger LOG = Global.getLogger(MapLayerPickUpkeep.class);

    // One holder for every way of failing, all of them the same news: the bar's picks are not being
    // checked. Once per session rather than once per row change, a broken roster otherwise saying so
    // on every arrangement the player makes.
    private final SessionWarning warning = new SessionWarning(LOG);

    private final Supplier<List<ScreenLayerPicks>> resolveEveryScreenPicks;

    // The revision each screen was last finished with, so a frame where nothing has moved does no
    // work. By identity: picks are the choices themselves, not anything describable about them.
    private final Map<ScreenLayerPicks, OfferedTabsRevision> settledRevisionByScreenPicks =
        new IdentityHashMap<>();

    /** Reads the live screens - the pairing a running game gets. */
    public MapLayerPickUpkeep() {
        this(MapLayerScreens::getAllScreenPicks);
    }

    MapLayerPickUpkeep(Supplier<List<ScreenLayerPicks>> resolveEveryScreenPicks) {
        this.resolveEveryScreenPicks = resolveEveryScreenPicks;
    }

    @Override
    public void advance(float amount) {

        // The net rather than the designed path: what this covers is reading the screens and their
        // rows, which fail soft by contract - the store answers the unarranged row for a file it
        // cannot open. A fault here is worth asking again next frame, so nothing is recorded for it.
        try {
            for (var screenPicks : resolveEveryScreenPicks.get()) {
                healScreenIfItsRowMoved(screenPicks);
            }

        } catch (RuntimeException failure) {
            warning.warnOnce(PICKS_UNCHECKED_WARNING, failure);
        }
    }

    @Override
    public boolean isDone() {
        // Rows go on moving for as long as the player keeps arranging their bar, and a fault on one
        // of them is not a reason to stop reading the next.
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        // Every screen the bar draws on pauses the campaign, so a pass standing down while paused
        // would never see a row the player just rearranged.
        return true;
    }

    // One screen, acted on only where its offered row is not the one it was last settled at.
    private void healScreenIfItsRowMoved(ScreenLayerPicks screenPicks) {

        var offeredTabsRevision = ScreenLayerTabs.readOfferedTabsRevision(screenPicks);

        if (offeredTabsRevision.equals(settledRevisionByScreenPicks.get(screenPicks))) {
            return;
        }

        if (isScreenFinishedWith(screenPicks)) {
            settledRevisionByScreenPicks.put(screenPicks, offeredTabsRevision);
        }
    }

    // Whether this screen is worth nothing further at this revision: it settled on an offered tab, or
    // it threw, which it will go on doing until the row it was asked about is a different one.
    private boolean isScreenFinishedWith(ScreenLayerPicks screenPicks) {

        try {
            return ScreenLayerTabs.healPickOntoOfferedTabs(screenPicks);

        } catch (RuntimeException failure) {

            warning.warnOnce(PICKS_UNCHECKED_WARNING, failure);

            return true;
        }
    }
}
