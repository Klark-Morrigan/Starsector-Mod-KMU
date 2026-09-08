package kmu;

import kmu.maplayers.base.sidebar.MapLayerStoreNamespace;

/**
 * The mod's own identity: the id its settings and scoped loggers are keyed on, the name it presents
 * under, and the namespace its map-layer sidebar stores save beneath.
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

    /**
     * The prefix this mod's sidebar spotlight, sort and column stores save their keys under, so a
     * layer another mod registers keeps its picker's answers apart from these. Here rather than
     * beside those stores because it says which mod is asking rather than what is stored: every KMU
     * layer shares it, and the stores hold no mod's name at all. Frozen in this spelling - it leads
     * every key those three have ever written, so a rename silently resets every existing save's
     * spotlight, sort and column picks.
     */
    public static final MapLayerStoreNamespace MAP_STORE_NAMESPACE =
        new MapLayerStoreNamespace("$kmu_map_");

    private KmuMod() {
    }
}
