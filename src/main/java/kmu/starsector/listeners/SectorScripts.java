package kmu.starsector.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.function.Supplier;

/**
 * Registering and clearing the sector's own per-frame scripts, so every installer states what it
 * wants rather than writing the null guard and the clear-then-add for itself.
 *
 * <p>Everything here registers transient - a script that entered the save would be restored beside
 * the one each load adds, and two of a pass are two of whatever it does. That is what leaves
 * {@link #removeScript} needed only where a feature can be switched off mid-session: across a load
 * there is nothing left to remove.
 *
 * <p>One install shape rather than two, for {@link SectorListeners}' reason: a presence check that
 * kept the registration already there would be no safer than replacing it - both leave exactly one -
 * and would be worse for a script holding the sector it was built against.
 *
 * <p>The class cleared is the built script's own rather than one the caller names beside it. A
 * caller that could name it could name a different one, and what that buys is a pass silently
 * doubling while a sibling disappears. It is why this differs in shape from {@link SectorListeners},
 * where the two are separate calls on the engine's manager.
 *
 * <p>By class, which makes this the wrong tool for a script whose class a sibling mod may also be
 * running over the same sector - {@link InstalledTransientScript} is that case, and states the
 * difference.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class SectorScripts {

    private SectorScripts() {
        // utility class, no instances.
    }

    /**
     * Adds one script to the sector as a transient one, clearing every registration of its class
     * first.
     *
     * @param sector      the sector to register with; null is a no-op
     * @param buildScript called only once there is a sector to add to, so a load that cannot
     *                    register does not construct a script it would discard
     */
    public static void installScript(
            SectorAPI sector,
            Supplier<? extends EveryFrameScript> buildScript) {

        if (sector == null) {
            return;
        }
        var installedScript = buildScript.get();

        sector.removeTransientScriptsOfClass(installedScript.getClass());
        sector.addTransientScript(installedScript);
    }

    /**
     * Clears every registration of one script class, adding nothing back - what a feature switched
     * off asks for, so a pass does not go on running for a feature the player has turned off.
     *
     * @param sector      the sector to clear on; null is a no-op
     * @param scriptClass the class every registration of is cleared
     */
    public static void removeScript(SectorAPI sector, Class<? extends EveryFrameScript> scriptClass) {

        if (sector == null) {
            return;
        }
        sector.removeTransientScriptsOfClass(scriptClass);
    }
}
