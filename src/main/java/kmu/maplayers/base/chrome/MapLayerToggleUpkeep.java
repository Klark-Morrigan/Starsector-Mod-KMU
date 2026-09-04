package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import kmlib.logging.SessionWarning;
import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterRows;
import kmlib.starsector.ui.map.controls.MapFilterToggle;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenLayerTabs;
import kmu.settings.KmuMapLayerSettings;

import org.apache.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps the map layers' tick box standing on whichever filter row the player is looking at, and
 * keeps it showing what that screen holds.
 *
 * <p>A standing pass rather than a one-time install: the map widget builds its row inside its own
 * constructor, so every open of a screen produces a fresh row while the box put on the last one
 * goes on existing, attached to a widget nobody can see. Nothing announces that, so the box held
 * for a screen is asked whether it still stands on the row that is up.
 *
 * <p>Asked by identity, and one box held per screen. Counting the row's children instead would read
 * another mod's button as our own having gone missing, and append a second box every frame; a
 * single held box would have nothing to compare against once the player moved between the two
 * screens and back.
 *
 * <p>What the box shows is a standing job for the same reason its standing is. One seeded as it
 * went up would tell the truth only until something moved the pick under it - the settling below
 * does exactly that, and so does a hatch closed and reopened over a box already up - and a box
 * saying the layers are shown over a map with none on it is worse than no box at all. So it is
 * written from the pick each frame rather than at the moment it goes up, which also leaves whatever
 * moves the pick free to know nothing about boxes.
 *
 * <p>A script rather than a render pass, the sidebar's own pass running only while the sidebar is
 * showing - and this control has to work its way out of exactly the state where nothing of the
 * feature is drawn. It runs while paused, every screen carrying a filter row pausing the campaign.
 *
 * <p>Nothing here can take the feature down with it: every way of failing resolves to no control,
 * one line in the log, and a map that behaves as it did before the box existed. This pass is also
 * the only thing that can say a screen has a control, which is what a stored hide is acted on - so
 * a screen it never writes to shows its layers whatever the save holds, and neither a broken reach
 * nor a closed hatch can leave a player in front of a blank map with nothing to reverse it.
 *
 * <p>Saying so is also what takes a tab off that screen's strip, so this is where the pick standing
 * on that tab is settled - once, on the first box to stand. It is the only place that knows when a
 * box went up, and the pick has to move before the box opens on the state it leaves behind, so both
 * halves belong to the frame that stands it rather than to whatever draws the strip afterwards.
 */
public final class MapLayerToggleUpkeep implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(MapLayerToggleUpkeep.class);

    // Whether to reach for the row at all. Read per frame, so closing the hatch takes effect on the
    // next screen opened rather than at the next load.
    private final BooleanSupplier isToggleEnabled;

    // The picks of the screen showing this frame: the show-or-hide state a box drives, the word that
    // a box stands there, and the tab beside them a standing box takes the job of. One object for all
    // of it, so a box cannot be bound to one screen while the other is told it has one.
    private final Supplier<ScreenLayerPicks> resolveLiveScreenPicks;

    // The filter row of the map on screen, or nothing on the many screens that show no map.
    private final Supplier<MapFilterRow> resolveShownFilterRow;

    // Stands the box on a row and binds it to a pick.
    private final MapLayerToggleAttacher toggleAttacher;

    // Says once per session that the box could not be put up, rather than on every frame. One
    // holder for every way of failing, all of them the same news: there is no control on the row.
    private final SessionWarning warning = new SessionWarning(LOG);

    // Each screen's own box, keyed by the picks it is bound to. Held rather than only recorded as
    // having been put up, because it goes on being driven for as long as it stands. By identity:
    // picks are the pair of choices themselves, not anything describable about them.
    private final Map<ScreenLayerPicks, MapFilterToggle> attachedTogglesByScreenPicks =
        new IdentityHashMap<>();

    /** Reads the live settings, screens and widget tree - the pairing a running game gets. */
    public MapLayerToggleUpkeep() {
        this(
            KmuMapLayerSettings::getMapFilterRowToggleEnabled,
            MapLayerScreens::resolveLivePicks,
            MapFilterRows::resolveShownMapFilterRow,
            new VanillaMapLayerToggleAttacher());
    }

    MapLayerToggleUpkeep(
            BooleanSupplier isToggleEnabled,
            Supplier<ScreenLayerPicks> resolveLiveScreenPicks,
            Supplier<MapFilterRow> resolveShownFilterRow,
            MapLayerToggleAttacher toggleAttacher) {

        this.isToggleEnabled = isToggleEnabled;
        this.resolveLiveScreenPicks = resolveLiveScreenPicks;
        this.resolveShownFilterRow = resolveShownFilterRow;
        this.toggleAttacher = toggleAttacher;
    }

    @Override
    public void advance(float amount) {

        // The switch read is inside the boundary too: it reaches the settings substrate, as able to
        // throw on an unfamiliar install as the widget walk below it.
        try {
            keepTheBoxStandingAndTruthful();

        } catch (RuntimeException failure) {

            warning.warnOnce(
                "The control that shows and hides the map layers could not be put on the game's "
                    + "map filter row; that row is left as the game built it, and the layers stay "
                    + "shown.",
                failure);
        }
    }

    @Override
    public boolean isDone() {
        // The row it maintains is rebuilt for as long as the player keeps opening map screens.
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        // Every screen carrying a filter row pauses the campaign, so a pass standing down while
        // paused would never run on a frame with a row to write to.
        return true;
    }

    // Ordered cheapest-first: a switch read, one hop into the widget on screen, then a reference
    // compare - so the common frame, the box already where it belongs, costs the hop, the word
    // re-asserted under it, and the one save read that keeps the box honest.
    private void keepTheBoxStandingAndTruthful() {

        if (!isToggleEnabled.getAsBoolean()) {
            forgetEveryControlAttached();
            return;
        }

        var shownRow = resolveShownFilterRow.get();
        if (shownRow == null) {
            return;
        }

        var screenPicks = resolveLiveScreenPicks.get();

        var standingToggle = resolveToggleStandingOn(shownRow, screenPicks);
        if (standingToggle == null) {
            return;
        }

        standControlOn(screenPicks);

        // Written after the stand rather than before it, since standing can move the pick: the box
        // shows where the screen ended up, on the very frame it goes up and on every frame after.
        standingToggle.setChecked(readStoredVisibilityOf(screenPicks).areLayersShown());
    }

    // The box on the row that is up, appending one where the screen has none - which is every screen
    // reopened, its row being rebuilt with nothing of ours on it.
    //
    // Nothing is remembered for a row that would take no box: a refusal is retried next frame, which
    // is what lets a row the layout had not placed yet take one later.
    private MapFilterToggle resolveToggleStandingOn(
            MapFilterRow shownRow,
            ScreenLayerPicks screenPicks) {

        var attachedToggle = attachedTogglesByScreenPicks.get(screenPicks);
        if (attachedToggle != null && attachedToggle.isStillAttachedTo(shownRow)) {
            return attachedToggle;
        }

        var appendedToggle = toggleAttacher.attachToggleTo(
            shownRow, readStoredVisibilityOf(screenPicks));

        if (appendedToggle == null) {
            return null;
        }
        attachedTogglesByScreenPicks.put(screenPicks, appendedToggle);

        return appendedToggle;
    }

    // What a box now standing on a screen is worth to that screen: its stored hide is acted on from
    // here, and a pick the strip stops offering a screen with a box is moved off before the two can
    // disagree - a blank map held by a tab, under a box saying the layers are shown.
    //
    // The move is owed on the first box to stand and on no later one, so the word is read before it
    // is said. Re-asserted rather than assumed on every other frame, a closed hatch having taken the
    // word back while leaving the box itself standing.
    private static void standControlOn(ScreenLayerPicks screenPicks) {

        var layerControl = screenPicks.layerVisibility();
        var isFirstControlOnThisScreen = !layerControl.hasControlBeenAttached();

        layerControl.recordControlAttached();

        if (isFirstControlOnThisScreen) {
            ScreenLayerTabs.migratePickOffWithheldTab(screenPicks);
        }
    }

    // What closing the hatch means beyond attempting nothing further. The word is a latch, so one
    // set earlier in the session would outlive the control it describes and leave a player who hid
    // the layers holding a blank map with no box to reverse it.
    //
    // Every screen attached to rather than the live one, since which screen is up is not what
    // changed. The rows are kept, so reopening the hatch over a standing row restores the word
    // instead of appending a second box beside the first.
    private void forgetEveryControlAttached() {

        for (var screenPicks : attachedTogglesByScreenPicks.keySet()) {
            screenPicks.layerVisibility().forgetControlAttached();
        }
    }

    // The pick a box shows and moves: the stored choice itself rather than the reading the
    // no-control-no-hiding rule gives everything else. A box is what lifts that rule, so one bound
    // to the reading would report the layers shown over a save that holds them hidden.
    private static MapLayerVisibility readStoredVisibilityOf(ScreenLayerPicks screenPicks) {
        return screenPicks.layerVisibility().getStoredVisibility();
    }
}
