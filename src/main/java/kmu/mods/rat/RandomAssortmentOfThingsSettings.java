package kmu.mods.rat;

import kmlib.mods.rat.RandomAssortmentOfThingsPresence;
import kmlib.settings.LunaSettingsReader;

/**
 * Random Assortment of Things' settings, as far as this mod reads them - which today is only the
 * moment they are saved.
 *
 * <p>The compatibility follows that mod's own minimap switch as well as this mod's toggle, and
 * LunaLib announces every mod's saves to every listener, so the other mod's switch is as observable
 * as ours. Binding to it takes that mod's ID, and an ID written at a call site is an ID that can be
 * written wrong there - so it is bound here, in the package that names the mod already, and a caller
 * names the reaction and nothing else.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class RandomAssortmentOfThingsSettings {

    private RandomAssortmentOfThingsSettings() {
        // utility class, no instances.
    }

    /**
     * Runs {@code onChange} whenever that mod's settings are saved, so this mod answers its switch
     * where the player flips it rather than at the next load.
     *
     * <p>Bound without asking whether the mod is installed: the binding is to its ID, so on an
     * install without it nothing ever announces that ID and the callback simply never runs.
     *
     * <p>Call once at application load: every call adds another listener.
     *
     * @param onChange what to run after a change to that mod's settings lands
     */
    public static void runOnSettingsChange(Runnable onChange) {

        LunaSettingsReader.runOnSettingsChange(
            RandomAssortmentOfThingsPresence.MOD_ID,
            onChange);
    }
}
