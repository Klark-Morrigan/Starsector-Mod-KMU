package kmu.starsector.rat;

import kmlib.starsector.rat.RandomAssortmentOfThingsMinimap;
import kmlib.starsector.ui.map.presence.CampaignMinimap;

import kmu.settings.KmuMapLayerSettings;

import java.util.function.BooleanSupplier;

/**
 * Whether the map layers should treat the minimap Random Assortment of Things draws in the
 * campaign radar's place as a map worth answering, rather than as one more surface to stand clear
 * of.
 *
 * <p>Two halves, ANDed, and the mode is nothing without both. The player's half is that mod's
 * switch on the {@code Map - Compatibility} tab. The other is whether its minimap is on screen at
 * all, so an install without one pays a boolean and sees no behaviour it did not ask for, whatever
 * the switch says. Asking the switch first keeps that true in the other direction as well: a
 * player who turned the mode off is never the reason a mod-presence read runs.
 *
 * <p>Named for the mod because the switch is: a compatibility mode is something a player reaches
 * for having installed one particular thing, and a class binding a per-mod switch under a
 * mod-neutral name would leave which mod unsaid at the one place a reader needs it. What it
 * *asks* is mod-neutral, though, which is why the collaborator is KMLib's {@link CampaignMinimap}
 * role - so a second mod replacing the radar the same way arrives as its own switch and its own
 * mode rather than being folded into this one silently, which is what a player toggling this
 * mod's name would have every right not to expect.
 *
 * <p>The policy is the half that lives here. Whether a minimap is drawn at all is a fact about
 * another mod rather than about this one's map layers, so it sits with the rest of what KMLib
 * knows about that mod, and only the switch and the ANDing are this mod's to own.
 *
 * <p>This narrows what the general switches on the same tab allow; it does not widen it. Which
 * frames the layers may answer the cursor on at all is settled by those - the render constraint and
 * the two hover permissions beside it - and this only confines, within a frame they already allow,
 * to the surface the minimap actually occupies. They exist for the mods nobody here has met, and a
 * per-mod mode able to override them would make them unreliable as general switches: the reading a
 * player takes from "constrain layers to their own map" has to hold whatever else is installed.
 *
 * <p>Both halves are read live, like every other hover switch, so a player toggling the mode sees
 * the map change on the next frame rather than on the next load.
 */
public final class RandomAssortmentOfThingsCompatibilityMode {

    private final BooleanSupplier isModeSwitchedOn;

    private final CampaignMinimap minimap;

    /**
     * @param isModeSwitchedOn whether the player has left the compatibility mode on
     * @param minimap          whether a minimap stands in for the campaign radar
     */
    public RandomAssortmentOfThingsCompatibilityMode(
            BooleanSupplier isModeSwitchedOn,
            CampaignMinimap minimap) {

        this.isModeSwitchedOn = isModeSwitchedOn;
        this.minimap = minimap;
    }

    /**
     * @return the mode over the switch a running game actually reads and the minimap it actually
     *         has
     */
    public static RandomAssortmentOfThingsCompatibilityMode createForLiveGame() {

        return new RandomAssortmentOfThingsCompatibilityMode(
            KmuMapLayerSettings::getRandomAssortmentOfThingsCompatibilityModeEnabled,
            new RandomAssortmentOfThingsMinimap());
    }

    /**
     * @return whether the layers should adapt to the minimap this frame - the player's switch and
     *         a minimap to adapt to, both
     */
    public boolean isEngaged() {

        return isModeSwitchedOn.getAsBoolean()
            && minimap.isReplacingRadar();
    }
}
