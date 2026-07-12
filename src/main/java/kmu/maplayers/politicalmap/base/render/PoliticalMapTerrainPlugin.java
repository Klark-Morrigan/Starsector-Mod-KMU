package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;

import java.util.EnumSet;

/**
 * Terrain plugin that paints the political map on the sector (M) map as merged HOI4-style
 * clusters, where adjacent same-grouping systems fuse into one solid national region. It is the
 * shared political-map terrain surface, living in {@code base.render} so any political-map view
 * reuses one grouping-agnostic pipeline; it draws whichever view
 * {@link PoliticalMapViewRegistry#getActiveView()} reports, or stays dark when none is active.
 *
 * <p>This class is only the terrain adapter: it satisfies Starsector's terrain contract and, each
 * frame the map is open, asks its {@link PoliticalMapCache} to bring the cached draw lists up to
 * date and hands them to its {@link PoliticalMapOverlayRenderer} to paint. All the real work -
 * keeping the draw lists fresh with the least work per frame, and composing the overlay layers -
 * lives in those two collaborators.
 *
 * <p>Terrain is the surface because the sector map renders terrain through {@code renderOnMap} -
 * the same hook the vanilla nebulae draw with. A custom campaign entity has no map-render hook, so
 * its {@code render} never reaches the map. The below-UI {@code renderOnMap} pass (rather than
 * {@code renderOnMapAbove}) keeps the territory beneath system and constellation names, matching
 * its role as a quiet background layer.
 *
 * <p>The two collaborators are {@code transient}: they hold record types XStream cannot serialise
 * and are rebuilt each session, so they must never enter the save. A save-restored plugin comes
 * back with them null - XStream skips transient fields and runs no field initialisers - so they
 * are created lazily in {@link #renderOnMap} rather than in a field initialiser.
 */
public class PoliticalMapTerrainPlugin extends BaseTerrain {
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

    // The freshness cache and the overlay compositor the plugin delegates to. Transient: they hold
    // record types XStream cannot serialise and are derived each session, so they stay out of the
    // save. Null until the first render this session (and after a save restore), then created lazily.
    private transient PoliticalMapCache cache;
    private transient PoliticalMapOverlayRenderer overlayRenderer;

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
        // Paints whichever political-map view is active, or nothing when none is - the map is off
        // (No Layer tab) or dark (political-map tab open, view deselected). Gating the whole draw
        // (and its refresh) on one view read keeps a hidden overlay near-free per frame, and reading
        // the view - not a named faction gate - is what lets any registered view paint here.
        var view = PoliticalMapViewRegistry.getActiveView();
        if (view == null) {
            return;
        }
        // Recreated lazily: a save-restored plugin comes back with both null (transient), so the
        // first render this session rebuilds them before any draw.
        if (cache == null) {
            cache = new PoliticalMapCache();
        }
        if (overlayRenderer == null) {
            overlayRenderer = new PoliticalMapOverlayRenderer();
        }
        cache.refresh(view);
        overlayRenderer.renderOnMap(cache, factor, alphaMult);
    }
}
