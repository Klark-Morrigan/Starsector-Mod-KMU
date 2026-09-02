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
 * <p>A standing pass rather than a one-time install, because the row is not furniture: the map
 * widget builds its own row inside its constructor, so every open of a map screen produces a fresh
 * row while the box put on the last one goes on existing, attached to a widget nobody can see.
 * There is no event for that, and nothing else to notice it by - so the row on screen is compared
 * against the row the box was put on, and they part company exactly when the screen was reopened.
 *
 * <p>Row identity rather than what the row holds. A row another mod has appended to carries a
 * button this mod never put there, and counting children would read that as our own box having gone
 * missing - and then append a second one beside it on every frame.
 *
 * <p>One row remembered per screen rather than one for the pass. The two screens are never up
 * together, so a single slot would be replaced each time the player moved between them and would
 * have nothing to compare against on the way back; whether that is harmless rests on the game
 * building a fresh row every time, which is exactly the assumption a duplicated control would be
 * the punishment for.
 *
 * <p>A script rather than a render pass, for the reason the hover expirer is one: the sidebar's own
 * pass runs only while the sidebar is showing, and a control that has to bring the layers back is
 * needed precisely on the frames where nothing of them is drawn. It runs while paused because every
 * screen it works on pauses the campaign.
 *
 * <p>Nothing here can take the whole feature down with it. The reach it drives is a write into
 * another party's widget, so every way of failing - a row that cannot be resolved, a shape that no
 * longer builds a drivable button, a read that throws outright - resolves to no control, one line
 * in the log, and a map that behaves as it did before the box existed.
 *
 * <p>This pass is also the only thing that can say a screen has a control at all, which is what a
 * stored hide is honoured against. So the half of failing open that no log line covers is here: a
 * screen this never manages to write to goes on showing its layers whatever the save holds, and a
 * reach that stops working cannot leave a player with them switched off and nothing to switch them
 * back on.
 */
public final class MapLayerToggleUpkeep implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(MapLayerToggleUpkeep.class);

    // Whether to reach for the row at all: the hatch a player closes when the reach misbehaves.
    // Read per frame like the rest of the mod's switches, so closing it takes effect on the next
    // screen the player opens rather than on the next load. A box already standing stays where it
    // is - the row offers no way to take one off again - and the row the next open builds is bare.
    private final BooleanSupplier isToggleEnabled;

    // The show-or-hide state of the screen showing this frame: the pick a box on that screen's row
    // drives, and the word that a box now stands there. Asked per frame rather than held, since
    // which screen is up is a per-frame question, and asked as one object rather than two, so a box
    // cannot be bound to one screen while the other is told it has one.
    private final Supplier<ControlBackedMapLayerVisibility> resolveLiveScreenLayerControl;

    // The filter row of the map on screen, or nothing on every screen that shows no map - which is
    // most of them, and is the ordinary answer rather than a failure.
    private final Supplier<MapFilterRow> resolveShownFilterRow;

    // Stands the box on a row and binds it to a pick.
    private final MapLayerToggleAttacher toggleAttacher;

    // Says once per session that the box could not be put up, rather than on every frame the pass
    // reaches a row it cannot write to. One holder for every way of failing, all of them being the
    // same piece of news: there is no control on the row.
    private final SessionWarning warning = new SessionWarning(LOG);

    // The row each screen's box was last put on, held against that screen's own show-or-hide state,
    // since that state is what a box is bound to and what says which screen it belongs to. By
    // identity, because a row is the widget itself rather than anything describable about it.
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

        // The whole pass is inside the boundary, the switch read included: it reaches the settings
        // substrate, which is as able to throw on an install this mod has not met as the widget
        // walk below it.
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
        // Nothing ends this: the row it maintains is rebuilt for as long as the player keeps
        // opening map screens.
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        // Every screen carrying a filter row pauses the campaign, so a pass that stood down while
        // paused would run on none of the frames it exists for.
        return true;
    }

    // The pass itself, inside the failure boundary above. Ordered cheapest-first: a switch read,
    // then one hop into the widget on screen, then a reference compare - so the common frame, where
    // the box is already where it belongs, costs the hop and nothing more.
    private void attachToggleWhereMissing() {

        if (!isToggleEnabled.getAsBoolean()) {
            return;
        }

        var shownRow = resolveShownFilterRow.get();
        if (shownRow == null) {
            return;
        }

        var screenLayerControl = resolveLiveScreenLayerControl.get();

        var attachedRow = attachedRowsByScreenState.get(screenLayerControl);
        if (attachedRow != null && attachedRow.isSameRowAs(shownRow)) {
            return;
        }

        // Both said only where a box actually went up: a refusal is retried on the next frame rather
        // than remembered as an attachment that never happened, and a screen told it has a control it
        // never got would act on a stored hide with nothing on the row to reverse it.
        if (!toggleAttacher.attachToggleTo(shownRow, screenLayerControl.getStoredVisibility())) {
            return;
        }
        attachedRowsByScreenState.put(screenLayerControl, shownRow);
        screenLayerControl.recordControlAttached();
    }
}
