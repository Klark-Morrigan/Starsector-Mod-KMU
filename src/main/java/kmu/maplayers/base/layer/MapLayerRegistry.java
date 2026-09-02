package kmu.maplayers.base.layer;

import kmlib.starsector.ui.intel.IntelScreenView;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;

import java.util.List;

/**
 * The map-layer registry and the holder of each screen's picks. The layer bar composes its tabs from
 * {@link #getLayers()}, a screen's own pair ({@link #getMapPicks()} or {@link #getIntelPicks()}) stores
 * which layer is active there and whether that screen's layers show at all, and each layer's own state
 * reads {@link #isActive} to decide whether it is the one to draw - so all agree on the live selection
 * without sharing state directly. Each screen's picks are its own {@link PersistedActiveLayerSelection}
 * and {@link PersistedMapLayerVisibility} under its own keys, so a switch or a hide on one screen
 * survives reload without moving the other's; the registry holds both pairs so all four save keys and
 * the one-time legacy migration live in a single place. Each screen's hide is handed out through
 * {@link ControlBackedMapLayerVisibility}, so a stored hide is acted on only while that screen has a
 * control able to reverse it - which is the difference between an optional decoration on the game's own
 * chrome and a load-bearing one.
 *
 * <p>Because the picks are per-screen, "whose picks are live" is a per-frame question rather than a
 * fixed answer: the same map widget draws on the sector map and inside the intel screen's visor, so an
 * overlay reading one fixed screen's pick would paint the sector map's choice onto the intel screen
 * and ignore the tab the player is looking at. {@link #isActive} settles it by reading which screen is
 * up, through the intel-screen seam {@link #registerIntelScreen} supplies, and settles it once for the
 * pair - which is what {@link ScreenLayerPicks} is for.
 *
 * <p>This is the feature-agnostic framework half: it knows nothing of any concrete layer.
 * The set of layers and the default pick are supplied once at startup by a composition root
 * through {@link #registerLayers}, so a new view is added by registering it rather than by
 * editing this class. Each pick lives in sector memory under a stable key, so it serialises into
 * the save and survives reload; an untouched save resolves to the registered default, as does one
 * holding an id no longer registered.
 */
public final class MapLayerRegistry {

    // Save-serialised identity of each screen's active pick; frozen once shipped, since renaming one
    // silently resets every existing save under it to the default.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The same for each screen's show-or-hide pick, and frozen for the same reason.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // The end of the hide ramp: none of a screen's layers left on it, which is where the active pick
    // stops being answered at all.
    private static final float FULLY_HIDDEN = 0f;

    // Each screen's show-or-hide pick as the rest of the mod reads it: the stored pick behind the rule
    // that a hide is honoured only while that screen has a control able to reverse it. Held as that
    // reading rather than as the stored pick, so no consumer can be handed the raw choice by accident -
    // the one caller entitled to it is named below.
    private static final ControlBackedMapLayerVisibility MAP_LAYER_VISIBILITY =
        new ControlBackedMapLayerVisibility(new PersistedMapLayerVisibility(MAP_LAYERS_SHOWN_KEY));
    private static final ControlBackedMapLayerVisibility INTEL_LAYER_VISIBILITY =
        new ControlBackedMapLayerVisibility(new PersistedMapLayerVisibility(INTEL_LAYERS_SHOWN_KEY));

    // Each screen's pair of picks, persisted under that screen's own frozen keys. The map host draws
    // through the map pair and the overlay follows it; the intel host draws through the intel pair. Held
    // here so all four keys sit in one place, and paired so a screen is chosen once rather than at each
    // site that wants one of its two picks.
    private static final ScreenLayerPicks MAP_PICKS = new ScreenLayerPicks(
        new PersistedActiveLayerSelection(MAP_ACTIVE_LAYER_KEY),
        MAP_LAYER_VISIBILITY);
    private static final ScreenLayerPicks INTEL_PICKS = new ScreenLayerPicks(
        new PersistedActiveLayerSelection(INTEL_ACTIVE_LAYER_KEY),
        INTEL_LAYER_VISIBILITY);

    // The registered layers, in tab order, and the pick an untouched save resolves to. Empty
    // until a composition root registers them at startup, before any sector map can open.
    private static List<MapLayer> orderedLayers = List.of();
    private static MapLayer defaultLayer;

    // Reads whether the intel screen is the one up, which is what decides whose pick is live. Null
    // until the composition root supplies it, which resolves every read to the map screen's pick - the
    // answer a registry with no screen wired yet should give, since the sector map is the overlay's home.
    private static IntelScreenView intelScreen;

    private MapLayerRegistry() {
    }

    /**
     * Records the layers the bar shows and the pick an untouched save resolves to. Called once
     * by the composition root at startup: it is the only place a concrete layer is named, so
     * the framework here stays agnostic to which views exist.
     *
     * @param layers        the registered layers in tab order, left to right
     * @param defaultLayer  the pick an untouched save (or a stale stored id) resolves to
     */
    public static void registerLayers(List<MapLayer> layers, MapLayer defaultLayer) {
        orderedLayers = List.copyOf(layers);
        MapLayerRegistry.defaultLayer = defaultLayer;
    }

    /**
     * Records the intel-screen seam that tells the registry which screen is up, so {@link #isActive}
     * can answer from the pick belonging to the screen the player is looking at. Called by the
     * composition root, the one place a concrete screen binding is named. Only the intel screen is
     * asked: the two screens are never up together, so "not the intel screen" is the sector map.
     *
     * @param intelScreen reads whether the intel screen is the one showing
     */
    public static void registerIntelScreen(IntelScreenView intelScreen) {
        MapLayerRegistry.intelScreen = intelScreen;
    }

    /** @return the registered layers in tab order, left to right. */
    public static List<MapLayer> getLayers() {
        return orderedLayers;
    }

    /** @return the pick an untouched save resolves to, the fallback for an absent or stale stored pick. */
    public static MapLayer getDefaultLayer() {
        return defaultLayer;
    }

    /**
     * @return the map screen's picks, for the map host to draw through, switch and flip, so the host and
     *         the overlay read one pair rather than each resolving their own
     */
    public static ScreenLayerPicks getMapPicks() {
        return MAP_PICKS;
    }

    /**
     * @return the intel screen's picks, its own under its own keys, so the intel sidebar's tab and its
     *         show-or-hide state are independent of the map screen's and survive reload on their own
     */
    public static ScreenLayerPicks getIntelPicks() {
        return INTEL_PICKS;
    }

    /**
     * @return whether the layers are picked to show on the screen showing this frame, answered the
     *         moment the pick flips. The crisp reading, for whatever has to stand down at once rather
     *         than ride the dissolve out - a control switched off must stop answering the player
     *         immediately, whatever is still fading off the screen
     */
    public static boolean areLayersShownOnLiveScreen() {
        return resolveLivePicks().layerVisibility().areLayersShown();
    }

    /**
     * @return the show-or-hide state of the screen showing this frame, for whatever stands a control on
     *         that screen's own chrome: the stored pick such a control shows and moves, and the word
     *         that one now stands there. One object for both halves, so a control cannot be bound to
     *         one screen and recorded against the other; which screen it belongs to is settled here, so
     *         the caller never has to ask
     */
    public static ControlBackedMapLayerVisibility resolveLayerControlOfLiveScreen() {
        return isIntelScreenLive()
            ? INTEL_LAYER_VISIBILITY
            : MAP_LAYER_VISIBILITY;
    }

    /**
     * @return how much of the showing screen's layers is on it this frame, 0 with them wholly hidden
     *         and 1 with them wholly shown, for a pass multiplying it into what it paints so the
     *         whole footprint thins together rather than one part snapping out from under another
     */
    public static float resolveShownFadeOnLiveScreen() {
        return resolveLivePicks().layerVisibility().resolveShownFade();
    }

    /**
     * @return the active pick of the screen showing this frame, or null before a composition root has
     *         registered any layers, or once that screen's layers have wholly faded off it. This is
     *         what the map surface dispatches its render pass through, so the paint follows the tab
     *         the player is looking at without the surface naming a layer; it follows the live screen
     *         rather than one fixed screen, so the intel screen's own tab governs what paints there
     *         while the sector map keeps its own pick
     */
    public static MapLayer getActiveLayer() {

        // One resolution for both readings, so the pick answered and the show-or-hide state gating it
        // cannot come off different screens.
        var livePicks = resolveLivePicks();

        // Hiding lands here rather than at each consumer, because every pass driven by the active pick
        // already treats "no pick" as nothing to draw: one read takes the overlay, the labels and the
        // hover box off the screen together.
        if (!isAnythingOfTheLayersOn(livePicks.layerVisibility())) {
            return null;
        }
        return livePicks.layerSelection().getActiveLayer();
    }

    /**
     * What draws for the screen showing this frame, over {@code installation}'s sector, or null when
     * nothing does - because no layer is picked yet (before a composition root has registered any),
     * because that screen's layers have faded off it, or because the active one draws nothing. They
     * are one answer on purpose: every pass driven by the active pick treats them alike, so neither a
     * switch-only tab nor a hidden screen needs a case of its own in any of them.
     *
     * <p>The installation is passed rather than resolved here because which sector is being drawn is
     * the caller's to know: the roster this registry holds is the process's, while the renderer it
     * hands back is one sector's.
     *
     * @param installation the machinery installed on the sector being drawn
     * @return that sector's renderer for the active pick, or null when nothing draws
     */
    public static MapLayerRenderer resolveActiveMapRenderer(MapLayerInstallation installation) {
        var activeLayer = getActiveLayer();
        if (activeLayer == null) {
            return null;
        }
        return activeLayer.resolveRenderer(installation);
    }

    /**
     * @return whether {@code layer} is the active pick of the screen showing this frame - the gate a
     *         layer's own state reads to decide whether it is the one in play
     */
    public static boolean isActive(MapLayer layer) {
        // Layers are singletons, so identity settles it without an id compare.
        return getActiveLayer() == layer;
    }

    // Whether anything of the given screen's layers is on it at all: a screen switched off goes on being
    // drawn until its ramp reaches the end, which is what dissolves it rather than blinking it out.
    //
    // The crisp pick settles a shown screen outright, and the fade is asked for only where the answer
    // could still turn on it. A screen coming back paints from the first frame of its ramp whatever the
    // fade reads, so consulting it there could only ever agree - at the price of a clock read, and of a
    // settings read behind it, on every frame the layers are simply on.
    private static boolean isAnythingOfTheLayersOn(MapLayerVisibility visibility) {
        return visibility.areLayersShown() || visibility.resolveShownFade() > FULLY_HIDDEN;
    }

    // The picks of the screen that is up. One resolution for the pair, so no reading can answer for a
    // screen another reading has already left - which would hide one screen's layers over the other's tab.
    private static ScreenLayerPicks resolveLivePicks() {
        return isIntelScreenLive()
            ? INTEL_PICKS
            : MAP_PICKS;
    }

    // Whether the intel screen is the one up, which is what "live screen" means above.
    private static boolean isIntelScreenLive() {
        return intelScreen != null && intelScreen.isIntelTabOpen();
    }
}
