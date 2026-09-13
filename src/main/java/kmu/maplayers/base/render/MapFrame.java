package kmu.maplayers.base.render;

/**
 * The two map-wide values every emission of one frame scales and fades by.
 *
 * <p>Neither varies within a frame and every pass that puts anything on the map needs both, so
 * carrying them as one value leaves each pass's own signature to say what that pass actually
 * decides - the primitive it emits, the run it selects, the stroke it strokes with - rather than
 * restating the ambient pair in front of it.
 *
 * <p>It stops at the engine's edge. The plugin vanilla calls, and the layer seam that mirrors that
 * call, both keep the two as loose floats so the trail back to
 * {@code CampaignTerrainPlugin.renderOnMap} survives in the signature; a layer builds one of these
 * at the top of its pass and everything below it takes the pair whole.
 *
 * @param factor    the world-to-map scale every coordinate is multiplied by, named as vanilla names
 *                  it
 * @param alphaMult the map's own fade for this frame, multiplied into every element's opacity
 */
public record MapFrame(
    float factor,
    float alphaMult) {

    /** @return whether the map has faded out entirely, so a pass would emit every run for nothing */
    public boolean isFadedOut() {
        return alphaMult <= 0f;
    }
}
