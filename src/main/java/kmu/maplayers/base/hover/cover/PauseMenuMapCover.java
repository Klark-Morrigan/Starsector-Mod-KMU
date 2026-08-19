package kmu.maplayers.base.hover.cover;

import com.fs.starfarer.api.Global;

import java.util.function.BooleanSupplier;

/**
 * The cover the campaign's pause menu lays over the map: it takes the whole screen, so while one is
 * up nothing the cursor rests on is the map.
 *
 * <p>Needed because the menu is raised over whatever was already on screen without taking that
 * screen down. The map goes on being drawn behind it and the terrain pass goes on running, so a
 * hover left ungated resolves cells the player cannot see and ticks at each one as the cursor
 * crosses them - audible with no map open anywhere, which is how it was first noticed.
 *
 * <p>Reads {@link com.fs.starfarer.api.campaign.CampaignUIAPI#isShowingMenu()}, published API and a
 * single call, which makes this the cheapest cover in the set: no reflection, no geometry, and no
 * dependence on an optional mod. That is what puts it first in the order.
 *
 * <p>No cursor test, for the same reason the console needs none: a menu over the screen covers the
 * cursor wherever it is.
 */
public final class PauseMenuMapCover implements MapCover {

    private final BooleanSupplier isPauseMenuShowing;

    /** Reads the live campaign UI - the pairing outside a test. */
    public PauseMenuMapCover() {
        this(PauseMenuMapCover::readLiveMenuState);
    }

    PauseMenuMapCover(BooleanSupplier isPauseMenuShowing) {
        this.isPauseMenuShowing = isPauseMenuShowing;
    }

    @Override
    public boolean isCoveringCursor() {
        return isPauseMenuShowing.getAsBoolean();
    }

    // Fails open on a campaign that is not stood up yet, per the role's rule: what cannot be
    // established is not covering. There is no map to hover in that state either, so the open
    // answer costs nothing.
    private static boolean readLiveMenuState() {

        var sector = Global.getSector();

        if (sector == null || sector.getCampaignUI() == null) {
            return false;
        }
        return sector.getCampaignUI().isShowingMenu();
    }
}
