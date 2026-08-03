package kmu.maplayers.base.render;

import kmlib.starsector.ui.map.presence.StarscapeMapPresence;

import java.util.function.BooleanSupplier;

/**
 * Draws the active map layer while a map is showing the Starscape starfield - the mode the base
 * map-layer terrain is suppressed in. It is the second half of a pair: the same draw, from the same
 * renderer, reached through a second terrain entity whose reported type the map widget lets through.
 *
 * <p>The widget wraps every terrain entity in a render wrapper whose visibility test and
 * {@code renderOnMap} call share one condition - the map is not in starscape mode, or the entity's
 * terrain type is the literal {@code "slipstream"}. That is a raw string equality against one
 * literal, with no set, tag or alias behind it, so a terrain that wants to draw over the starfield
 * has to report exactly that type. Reporting it is {@link SectorMapLayerStarscapeTerrain}'s job;
 * this plugin is what that entity's spec resolves to.
 *
 * <p>Passing the widget's check in <em>both</em> modes is why this half stands itself down. The base
 * half is suppressed by the engine whenever its host is in starscape and so needs no starscape
 * awareness at all; this one is called in either mode, so it returns early unless a starscape map is
 * actually on screen. Exactly one of the pair paints in any frame, which is what keeps the picture
 * identical between the two looks - two halves painting would double every translucent fill.
 *
 * <p>Subclassing is what keeps the pair one renderer: the dispatch to the active layer's renderer is
 * inherited, so a change to what any layer draws reaches both surfaces without either half being
 * told the other exists. The pair costs nothing beyond the second entity, since the renderer it
 * dispatches to is reached through the registered layer rather than held per plugin - so both halves
 * drive one layer renderer, over one set of draw lists.
 */
public class SectorMapLayerStarscapeTerrainPlugin extends SectorMapLayerTerrainPlugin {

    // Whether a starscape map is on screen, held rather than re-resolved per frame. Transient and
    // non-final because this plugin is serialised into the save with the terrain entity holding it,
    // and the binding behind this field is not something XStream can carry. A save-restored plugin
    // therefore comes back with it null - XStream skips transient fields and runs no field
    // initialisers - so it is created lazily in renderOnMap rather than in a field initialiser.
    private transient BooleanSupplier isStarscapeMapShowing;

    /**
     * The pairing the terrain spec instantiates, reading whichever host is showing a starscape map.
     * The read is left unset here and resolved on the first render, since a save-restored plugin
     * arrives with it unset regardless and one lazy path then covers both arrivals.
     */
    public SectorMapLayerStarscapeTerrainPlugin() {
    }

    /** Binds the starscape read explicitly instead of letting the first render resolve it. */
    SectorMapLayerStarscapeTerrainPlugin(BooleanSupplier isStarscapeMapShowing) {
        this.isStarscapeMapShowing = isStarscapeMapShowing;
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        // Recreated lazily: a save-restored plugin comes back with the field null (transient), so
        // the first render this session rebuilds it before the guard below reads it.
        if (isStarscapeMapShowing == null) {
            isStarscapeMapShowing = new StarscapeMapPresence()::isStarscapeMapShowing;
        }
        // Stand aside outside starscape. The engine lets this half through in both modes, and
        // outside starscape the base half is the one it is already calling, so drawing here as well
        // would lay the same overlay down twice and double the alpha of every fill.
        if (!isStarscapeMapShowing.getAsBoolean()) {
            return;
        }
        super.renderOnMap(factor, alphaMult);
    }
}
