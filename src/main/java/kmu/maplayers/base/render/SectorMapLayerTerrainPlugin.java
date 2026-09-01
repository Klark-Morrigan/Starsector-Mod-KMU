package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.starsector.ui.map.presence.MapPresence;
import kmlib.starsector.ui.map.probes.EmbeddedMapHostTrace;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuMapLayerSettings;

import java.util.EnumSet;
import java.util.List;

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
 * registered layer and never enters a save. That constraint is also what decides how it finds the
 * sector it draws, which {@link #resolveInstallationBeingDrawn} sets out.
 *
 * <p><b>Renaming or moving this class breaks every existing save.</b> XStream writes the concrete
 * class name into the file, and a save naming a class that no longer exists fails to load outright -
 * the player loses the game, not the overlay. A rename therefore has to ship with an XStream alias
 * from the old name, registered in a {@code configureXStream} override on the mod plugin. The same
 * holds for every other class a terrain entity carries into a save. See
 * {@code base/render/README.md}.
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

    // Reports a pass that drew these layers without a map being on screen. Shared by all three
    // surfaces rather than one each: they would report a single foreign pass three times over, and
    // the stack in the line is what tells one caller from another anyway.
    //
    // Static, and wired here rather than injected, because the engine constructs these plugins from
    // the save and there is no seam to hand one in through. Static also keeps it out of the save,
    // which an instance field on a serialised plugin would not.
    // Whether a vanilla map host is on screen. One binding shared by the render constraint and the
    // warning below, so the two cannot disagree about what counts as a map being up.
    private static final MapPresence MAP_PRESENCE = new MapPresence();

    private static final ForeignMapPassWarning FOREIGN_PASS_WARNING = new ForeignMapPassWarning(
        MAP_PRESENCE::isAnyMapShowing,
        EmbeddedMapHostTrace::describeEmbeddedMapHosts,
        Global.getLogger(SectorMapLayerTerrainPlugin.class));

    // Both bands in one pass, bottom first. This surface owns one terrain icon, so it has no way to
    // put anything above the nebulae the map appends after it - they are drawn on the schematic map
    // too, just as ordinary nebula terrain rather than as the Starscape sprite. Splitting the pass
    // here would move nothing; it would only cost a second entity to paint the same order.
    private static final List<MapOverlayBand> BOTH_BANDS = List.of(
        MapOverlayBand.BENEATH_STARSCAPE_NEBULAE,
        MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);

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

        // First thing in the pass, so the stack it reports is the one that actually reached here,
        // and so a foreign pass is named even when the active pick draws nothing.
        FOREIGN_PASS_WARNING.warnOnceIfNoMapIsShowing();

        // Stands the whole pass down when the player has asked the layers to keep to the vanilla
        // maps and none is showing. Before the preparation rather than only the draw, so a foreign
        // pass cannot take the frame's single preparation from the map that is entitled to it.
        if (KmuMapLayerSettings.getMapLayersOnlyOnTheirHosts()
                && !MAP_PRESENCE.isAnyMapShowing()) {
            return;
        }
        // Null while this surface's terrain sits somewhere nothing is installed in, which is a
        // surface belonging to a sector nothing draws - so it stands down rather than painting
        // through a holder shared with every other sector-less caller.
        var installation = resolveInstallationBeingDrawn();

        if (installation == null) {
            return;
        }
        // Draws through the pick of whichever screen is showing this frame, over the machinery
        // installed on the sector being drawn, so switching a tab switches what paints with no
        // per-layer branch here. Null when nothing draws at all - no registered pick yet, or a pick
        // that paints nothing.
        var layerRenderer = MapLayerRegistry.resolveActiveMapRenderer(installation);

        if (layerRenderer == null) {
            return;
        }

        var paintedBands = resolvePaintedBands();

        // Preparation belongs to whichever surface paints the lower band, and then only to the first
        // of them to reach the frame. The band is what makes preparation land before anything is
        // drawn - it is painted in every mode and reached first - while the claim is what makes it
        // happen once, which the band alone cannot: whether a surface draws at all is settled by
        // that surface for itself, so two can paint the lower band in one frame and would otherwise
        // each prepare it.
        if (paintedBands.contains(MapOverlayBand.BENEATH_STARSCAPE_NEBULAE)
                && MapFramePreparationClaim.resolveClaimIn(installation).claimPreparation()) {
            layerRenderer.prepareFrame(factor);
        }

        // The cursor read, on every admitted pass rather than under the claim above. The claim is
        // first-come and this hook names no caller, so a map another mod composited - drawn from the
        // campaign HUD, ahead of the map screen - takes it every frame and performs the read with
        // its own zoom and pan. Reading per pass and letting the last write win puts the frame's
        // answer on the surface that drew last, which is the map the player is pointing at. It costs
        // one matrix read per extra pass, deferred rather than stalling under Fast Rendering.
        layerRenderer.publishHoverForPass(factor);

        // The showing screen's dissolve, folded into the alpha the map pass already fades the overlay
        // with, so hiding thins the picture rather than cutting it - and thins it on the same curve the
        // rest of the layers' footprint rides. A screen settled hidden never reaches here at all, the
        // active pick resolving to nothing once the dissolve is over.
        var shownAlpha = alphaMult * MapLayerRegistry.resolveShownFadeOnLiveScreen();

        for (var band : paintedBands) {
            layerRenderer.renderOnMap(factor, shownAlpha, band);
        }
    }

    /**
     * The bands this surface paints, in the order it paints them. Overridden by the surfaces that
     * paint only one of them, which is the whole of what a split surface is: the entity it rides on
     * decides where in the map's draw order the pass lands, and this decides what goes in it.
     *
     * @return this surface's bands, bottom first
     */
    protected List<MapOverlayBand> resolvePaintedBands() {
        return BOTH_BANDS;
    }

    /**
     * The machinery of the sector this surface is drawing.
     *
     * <p>The render hook names no sector and there is no seam to hand one in through, the engine
     * rebuilding this plugin from the save - so the terrain entity it rides on is the handle it has,
     * and that entity's containing location is its sector's hyperspace. Resolved per frame and never
     * held: an installation is a live object, and a field on this plugin is a field in the save.
     *
     * @return that sector's installation, or null where the engine has not yet seated this plugin's
     *         entity or where nothing is installed in the location it sits in - both of which mean
     *         nothing to draw
     */
    private MapLayerInstallation resolveInstallationBeingDrawn() {

        var terrainEntity = getEntity();

        return terrainEntity == null
            ? null
            : MapLayerInstallations.resolveInstallationIn(terrainEntity.getContainingLocation());
    }
}
