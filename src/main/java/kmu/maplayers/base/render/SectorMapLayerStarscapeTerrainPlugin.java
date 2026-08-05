package kmu.maplayers.base.render;

import kmlib.starsector.ui.map.presence.MapPresence;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Draws the lower band of the active map layer while a map is in Starscape mode - the mode the base
 * map-layer terrain is suppressed in. It is the second of the three surfaces: the
 * same draw, from the same renderer, reached through a second terrain entity whose reported type the
 * map widget lets through.
 *
 * <p>The widget wraps every terrain entity in a render wrapper whose visibility test and
 * {@code renderOnMap} call share one condition - the map is not in Starscape mode, or the entity's
 * terrain type is the literal {@code "slipstream"}. That is a raw string equality against one
 * literal, with no set, tag or alias behind it, so a terrain that wants to draw in this mode has to
 * report exactly that type. Reporting it is {@link SectorMapLayerStarscapeTerrain}'s job;
 * this plugin is what that entity's spec resolves to.
 *
 * <p>Passing the widget's check in <em>both</em> modes is why this half stands itself down. The base
 * half is suppressed by the engine whenever its host is in Starscape and so needs no Starscape
 * awareness at all; this one is called in either mode, so it returns early unless a Starscape map is
 * actually on screen. The base half and the Starscape ones never paint in the same frame, which is
 * what keeps the picture identical between the two looks - two surfaces painting the same band would
 * double every translucent fill.
 *
 * <p>It paints the lower band alone. Starscape is the mode that draws the map's nebula icons as a
 * large sprite laid over the sector, and they fall between this surface's pass and
 * {@link SectorMapLayerAboveStarscapeNebulaeTerrainPlugin}'s, so the parts of the overlay that read
 * through fog are emitted here and the parts that do not are emitted there. Painting the lower band
 * also makes this the surface that prepares the frame, the inherited pass tying the two together.
 *
 * <p>Subclassing is what keeps the surfaces one renderer: the dispatch to the active layer's
 * renderer is inherited, so a change to what any layer draws reaches all of them without any surface
 * being told the others exist. It costs nothing beyond the extra entity, since the renderer it
 * dispatches to is reached through the registered layer rather than held per plugin - so every
 * surface drives one layer renderer, over one set of draw lists.
 */
public class SectorMapLayerStarscapeTerrainPlugin extends SectorMapLayerTerrainPlugin {

    // Everything that reads as an area, which is what survives having fog drawn over it.
    private static final List<MapOverlayBand> BENEATH_BAND =
        List.of(MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

    // Whether a Starscape map is on screen, held rather than re-resolved per frame. Transient and
    // non-final because this plugin is serialised into the save with the terrain entity holding it,
    // and the binding behind this field is not something XStream can carry. A save-restored plugin
    // therefore comes back with it null - XStream skips transient fields and runs no field
    // initialisers - so it is created lazily in renderOnMap rather than in a field initialiser.
    private transient BooleanSupplier isStarscapeMapShowing;

    /**
     * The pairing the terrain spec instantiates, reading whichever host is showing a Starscape map.
     * The read is left unset here and resolved on the first render, since a save-restored plugin
     * arrives with it unset regardless and one lazy path then covers both arrivals.
     */
    public SectorMapLayerStarscapeTerrainPlugin() {
    }

    /** Binds the Starscape read explicitly instead of letting the first render resolve it. */
    SectorMapLayerStarscapeTerrainPlugin(BooleanSupplier isStarscapeMapShowing) {
        this.isStarscapeMapShowing = isStarscapeMapShowing;
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        // Recreated lazily: a save-restored plugin comes back with the field null (transient), so
        // the first render this session rebuilds it before the guard below reads it.
        if (isStarscapeMapShowing == null) {
            isStarscapeMapShowing = new MapPresence()::isStarscapeMapShowing;
        }
        // Stand aside outside Starscape. The engine lets this half through in both modes, and
        // outside Starscape the base half is the one it is already calling, so drawing here as well
        // would lay the same overlay down twice and double the alpha of every fill.
        if (!isStarscapeMapShowing.getAsBoolean()) {
            return;
        }
        super.renderOnMap(factor, alphaMult);
    }

    @Override
    protected List<MapOverlayBand> resolvePaintedBands() {
        return BENEATH_BAND;
    }
}
