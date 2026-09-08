package kmu.maplayers.base.sidebar;

/**
 * Store namespaces for the suites that have to address a sidebar store without being about which mod
 * is addressing it.
 *
 * <p>A stand-in rather than {@code KmuMod.MAP_STORE_NAMESPACE}, for the reason
 * {@code ScreenMemoryScopes}' screens are stand-ins: a case handed this mod's own namespace reads as
 * being about this mod's frozen keys, which the three stores' own suites pin against literals. A case
 * elsewhere would pin them twice, and a case whose subject is that two mods store apart needs only that
 * they are two.
 */
public final class MapLayerStoreNamespaces {

    private MapLayerStoreNamespaces() {
    }

    /** A mod of no particular identity, for slots whose subject is anything but whose store they are. */
    public static MapLayerStoreNamespace createStandInNamespace() {
        return new MapLayerStoreNamespace("$test_map_");
    }

    /**
     * A second mod of no particular identity, for the cases whose subject is that two mods keep a store
     * apart. Distinct from the first and nothing else.
     *
     * @return a stand-in namespace that is not {@link #createStandInNamespace()}'s
     */
    public static MapLayerStoreNamespace createOtherStandInNamespace() {
        return new MapLayerStoreNamespace("$other_map_");
    }
}
