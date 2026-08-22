package kmu;

/**
 * The mod's own identity: the id its settings and scoped loggers are keyed on, and the name it
 * presents under.
 *
 * <p>Held apart from {@link KMU_ModPlugin} so a class deep in the mod can say who it belongs to
 * without naming the entry point. Referring to the plugin for a constant loads the plugin - a
 * {@code BaseModPlugin} subclass with its own logger and its own imports - into anything that
 * touches the referring class, which is a large amount of machinery to drag in for
 * a string. This holds constants and nothing else, so importing it costs what importing a constant
 * should.
 */
public final class KmuMod {

    /** The mod id: its LunaLib settings key, and the owner its scoped library loggers name. */
    public static final String MOD_ID = "kmu";

    /** The mod's display name. */
    public static final String MOD_NAME = "Klark Morrigan's Utilities";

    private KmuMod() {
    }
}
