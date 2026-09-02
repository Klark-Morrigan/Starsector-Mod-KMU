package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import kmlib.logging.SessionWarning;
import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterRows;

import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.settings.KmuMapLayerSettings;

import org.apache.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps the map layers' tick box standing on whichever filter row the player is looking at.
 *
 * <p>A standing pass rather than a one-time install: the map widget builds its row inside its own
 * constructor, so every open of a screen produces a fresh row while the box put on the last one
 * goes on existing, attached to a widget nobody can see. Nothing announces that, so the row on
 * screen is compared against the row the box was put on.
 *
 * <p>Compared by identity, and one row remembered per screen. Counting the row's children instead
 * would read another mod's button as our own having gone missing, and append a second box every
 * frame; a single remembered row would have nothing to compare against once the player moved
 * between the two screens and back.
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
 */
public final class MapLayerToggleUpkeep implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(MapLayerToggleUpkeep.class);

    // Whether to reach for the row at all. Read per frame, so closing the hatch takes effect on the
    // next screen opened rather than at the next load.
    private final BooleanSupplier isToggleEnabled;

    // The show-or-hide state of the screen showing this frame: the pick a box drives, and the word
    // that a box stands there. One object for both, so a box cannot be bound to one screen while
    // the other is told it has one.
    private final Supplier<ControlBackedMapLayerVisibility> resolveLiveScreenLayerControl;

    // The filter row of the map on screen, or nothing on the many screens that show no map.
    private final Supplier<MapFilterRow> resolveShownFilterRow;

    // Stands the box on a row and binds it to a pick.
    private final MapLayerToggleAttacher toggleAttacher;

    // Says once per session that the box could not be put up, rather than on every frame. One
    // holder for every way of failing, all of them the same news: there is no control on the row.
    private final SessionWarning warning = new SessionWarning(LOG);

    // The row each screen's box was last put on, keyed by that screen's own state - which is what a
    // box is bound to. By identity: a row is the widget itself, not anything describable about it.
    private final Map<ControlBackedMapLayerVisibility, MapFilterRow> attachedRowsByScreenState =
        new IdentityHashMap<>();

    /** Reads the live settings, screens and widget tree - the pairing a running game gets. */
    public MapLayerToggleUpkeep() {
        this(
            KmuMapLayerSettings::getMapFilterRowToggleEnabled,
            MapLayerScreens::resolveLayerControlOfLiveScreen,
            MapFilterRows::resolveShownMapFilterRow,
            new VanillaMapLayerToggleAttacher());
    }

    MapLayerToggleUpkeep(
            BooleanSupplier isToggleEnabled,
            Supplier<ControlBackedMapLayerVisibility> resolveLiveScreenLayerControl,
            Supplier<MapFilterRow> resolveShownFilterRow,
            MapLayerToggleAttacher toggleAttacher) {

        this.isToggleEnabled = isToggleEnabled;
        this.resolveLiveScreenLayerControl = resolveLiveScreenLayerControl;
        this.resolveShownFilterRow = resolveShownFilterRow;
        this.toggleAttacher = toggleAttacher;
    }

    @Override
    public void advance(float amount) {

        // The switch read is inside the boundary too: it reaches the settings substrate, as able to
        // throw on an unfamiliar install as the widget walk below it.
        try {
            attachToggleWhereMissing();

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
    // compare - so the common frame, the box already where it belongs, costs the hop alone.
    private void attachToggleWhereMissing() {

        if (!isToggleEnabled.getAsBoolean()) {
            forgetEveryControlAttached();
            return;
        }

        var shownRow = resolveShownFilterRow.get();
        if (shownRow == null) {
            return;
        }

        var screenLayerControl = resolveLiveScreenLayerControl.get();

        var attachedRow = attachedRowsByScreenState.get(screenLayerControl);
        if (attachedRow != null && attachedRow.isSameRowAs(shownRow)) {

            // Re-asserted rather than assumed: a closed hatch takes the word back, and this is the
            // only frame that sees a standing row again, the append below being skipped.
            screenLayerControl.recordControlAttached();
            return;
        }

        // Recorded only where a box actually went up: a refusal is retried next frame, and a screen
        // told it has a control it never got would act on a stored hide it cannot reverse.
        if (!toggleAttacher.attachToggleTo(shownRow, screenLayerControl.getStoredVisibility())) {
            return;
        }
        attachedRowsByScreenState.put(screenLayerControl, shownRow);
        screenLayerControl.recordControlAttached();
    }

    // What closing the hatch means beyond attempting nothing further. The word is a latch, so one
    // set earlier in the session would outlive the control it describes and leave a player who hid
    // the layers holding a blank map with no box to reverse it.
    //
    // Every screen attached to rather than the live one, since which screen is up is not what
    // changed. The rows are kept, so reopening the hatch over a standing row restores the word
    // instead of appending a second box beside the first.
    private void forgetEveryControlAttached() {

        for (var screenLayerControl : attachedRowsByScreenState.keySet()) {
            screenLayerControl.forgetControlAttached();
        }
    }
}
