package kmu.starsector.consolecommands;

import kmlib.starsector.settings.ModPresence;

/**
 * Whether Console Commands is enabled this run - the gate every read of that mod stands behind,
 * and the one place its mod id is written.
 *
 * <p>The counterpart of KMLib's {@code RandomAssortmentOfThingsPresence}, kept in this mod because
 * the console integration is: which mods KMU adapts to is KMU's own roster. Two readers answer it
 * for different reasons - the composition that decides whether the console's cover joins the map's
 * covers at all, and {@link ConsoleCommandsOverlay}'s own settled gate, which additionally has to
 * survive the read itself breaking - and each writing the hop would leave the id in two places.
 *
 * <p>How the mod set is asked, and what a read taken before the game has stood one up answers, is
 * {@link ModPresence}'s - false, the answer that reads as an install without the mod.
 */
public final class ConsoleCommandsPresence {

    static final String MOD_ID = "lw_console";

    private ConsoleCommandsPresence() {
    }

    /**
     * @return whether Console Commands is enabled this run; false before the game has stood its
     *         mod set up, which is {@link ModPresence}'s answer rather than this one's
     */
    public static boolean isModEnabled() {
        return ModPresence.isModEnabled(MOD_ID);
    }
}
