package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmu.maplayers.base.layer.MapLayerRegistry;

import java.util.EnumSet;

/**
 * The map's render surface: the terrain plugin that owns the sector (M) map's overlay render pass
 * and hands each frame to whichever map layer the player has selected. It names no layer of its own -
 * it asks {@link MapLayerRegistry} what draws for the showing screen and draws through that - so a new
 * layer draws here by registering rather than by being named here.
 *
 * <p>A layer that supplies no renderer, and a frame with no active pick at all, arrive as the same
 * answer: nothing to draw. That is what makes the "show nothing" tab an ordinary layer rather than a
 * special case in this class.
 *
 * <p>Terrain is the surface because the sector map renders terrain through {@code renderOnMap} -
 * the same hook the vanilla nebulae draw with. A custom campaign entity has no map-render hook, so
 * its {@code render} never reaches the map. The below-UI {@code renderOnMap} pass (rather than
 * {@code renderOnMapAbove}) keeps the clusters beneath system and constellation names, matching
 * its role as a quiet background layer.
 *
 * <p>This plugin is serialised into the save with its terrain entity, so it deliberately holds no
 * state: everything a frame needs lives behind the layer's renderer, which is reached through a
 * registered layer and never enters a save.
 */
public class SectorMapLayerTerrainPlugin extends BaseTerrain {

    // Map rendering ignores this (the map calls the map hooks regardless), but BaseTerrain
    // requires the override; large so the terrain is never treated as a tiny point elsewhere.
    private static final float RENDER_RANGE = 1_000_000f;

    // This terrain draws only on the sector map (renderOnMap); it has no world-view rendering, so
    // it claims no engine layers. BaseTerrain's default getActiveLayers() throws to force a
    // deliberate choice, and an empty set is the correct one for a map-only terrain - vanilla's
    // RadioChatterTerrainPlugin does the same. Returning it (rather than leaving the default) is
    // what lets addTerrain succeed on a fresh game; omitting it crashes onGameLoad.
    private static final EnumSet<CampaignEngineLayers> ACTIVE_LAYERS =
        EnumSet.noneOf(CampaignEngineLayers.class);

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return ACTIVE_LAYERS;
    }

    @Override
    public float getRenderRange() {
        return RENDER_RANGE;
    }

    @Override
    public void advance(float amount) {
        // Purely visual: no fleet effect, sound, or music suppression, so the default BaseTerrain
        // effect/sound pass is intentionally skipped.
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        // Nothing in the live world view - this overlay is a map-only layer.
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        // Draws through the pick of whichever screen is showing this frame, so switching a tab
        // switches what paints with no per-layer branch here. Null when nothing draws at all - no
        // registered pick yet, or a pick that paints nothing.
        var layerRenderer = MapLayerRegistry.resolveActiveMapRenderer();
        if (layerRenderer == null) {
            return;
        }
        layerRenderer.renderOnMap(factor, alphaMult);
    }
}
