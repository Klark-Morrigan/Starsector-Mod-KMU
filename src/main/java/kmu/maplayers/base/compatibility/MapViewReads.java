package kmu.maplayers.base.compatibility;

import kmlib.starsector.ui.intel.VanillaIntelScreenView;
import kmlib.starsector.ui.map.presence.MapPresence;

/**
 * The live reads behind {@link MapLayerGameReach#MAP_VIEW}: the intel screen and whether a vanilla map
 * is up, one binding each for every part of the map layers that asks.
 *
 * <p>One binding rather than one per caller, because each intel view keeps its own once-a-session
 * warnings. Built per caller, a broken intel reach writes the same warning once per binding.
 *
 * <p>The Starscape reseat keeps a presence read of its own: it files under
 * {@link MapLayerGameReach#STARSCAPE_RESEAT}, a different loss.
 */
public final class MapViewReads {

    /** The intel screen, filing under {@link MapLayerGameReach#MAP_VIEW}. */
    public static final VanillaIntelScreenView INTEL_SCREEN =
        new VanillaIntelScreenView(MapLayerGameReach.MAP_VIEW.getReporter());

    /** Whether a vanilla map is up, reading the intel screen through {@link #INTEL_SCREEN}. */
    public static final MapPresence MAP_PRESENCE =
        new MapPresence(MapLayerGameReach.MAP_VIEW.getReporter(), INTEL_SCREEN);

    private MapViewReads() {
    }
}
