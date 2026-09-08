package kmu.maplayers.base.render;

import kmlib.profiling.ProfileSection;

/**
 * The sections a frame's beats are measured under, and the section one layer's own work is measured
 * beneath whichever beat is running.
 *
 * <p>Published rather than spelled at each site because a row is only worth having if a reader can
 * find it: a consumer layer names the beat it runs under from here, and a reader matching a name in
 * a report back to a point in the sequence reads the same names. Two sites spelling one beat alike
 * would be two rows that look like one.
 *
 * <p>Held as registered sections rather than as strings because these are opened every frame, and a
 * registered section is found among the children of the open scope by reference rather than by
 * hashing a name.
 *
 * <p>The beats are the frame sequence's, not any layer's: the frame's single preparation, the cache
 * refresh inside it, the cursor read each pass makes, one band of paint, and the hover box. A
 * layer's own row sits under whichever beat is running, so what a layer costs is read against the
 * beat it cost it in - and a layer that opens no section of its own still has a row, the row being
 * opened around its callback rather than by it.
 */
public final class MapFrameSections {

    /**
     * The frame's single preparation - what it settles once for every pass, and the cache refresh
     * it drives. Its self time is what the preparation costs outside that refresh.
     */
    public static final ProfileSection PREPARE =
        ProfileSection.registerSection("mapLayer.prepare");

    /**
     * Bringing a layer's draw lists up to date, inside {@link #PREPARE}. Inside rather than beside
     * it because that is where the sequence puts it: the preparation is the beat the map hook
     * actually calls, and a refresh hung off its own root would leave the preparation's total
     * excluding the dearest thing it does.
     */
    public static final ProfileSection REFRESH =
        ProfileSection.registerSection("mapLayer.refresh");

    /**
     * One pass's cursor read. Per pass rather than per frame, so a frame painted by several
     * surfaces reports the reads it actually made rather than one of them.
     */
    public static final ProfileSection HOVER_PUBLISH =
        ProfileSection.registerSection("mapLayer.hoverPublish");

    /** The hover box a layer offers for the cell under the cursor, resolved in the later UI pass. */
    public static final ProfileSection TOOLTIP =
        ProfileSection.registerSection("mapLayer.tooltip");

    // The two paint beats, one per band, reached through resolveRenderSection so no caller holding
    // a band spells either name. Separate sections rather than one row: the bands are separate
    // passes carrying different contents, and one row averaging them would describe neither.
    private static final ProfileSection RENDER_BENEATH_STARSCAPE_NEBULAE =
        ProfileSection.registerSection("mapLayer.render.beneathNebulae");

    private static final ProfileSection RENDER_ABOVE_STARSCAPE_NEBULAE =
        ProfileSection.registerSection("mapLayer.render.aboveNebulae");

    // Under the framework's own namespace rather than bare, so every layer's row is found under one
    // prefix whoever registered the layer, and two mods picking the same layer id collide where the
    // registry already reports them rather than silently sharing a row.
    private static final String LAYER_SECTION_PREFIX = "mapLayer.layer.";

    private MapFrameSections() {
    }

    /**
     * The section one layer's work is measured under, within whichever beat is running.
     *
     * <p>Keyed by the layer's id rather than by its label, the id being what the layer is known by
     * everywhere else it is recorded. Resolve it once and hold it: the concatenation and the
     * registry lookup behind it are not a per-frame cost, while the section it returns is.
     *
     * @param layerId the id of the layer whose work is being measured
     * @return the one section that layer's rows sit on
     */
    public static ProfileSection resolveLayerSection(String layerId) {
        return ProfileSection.registerSection(LAYER_SECTION_PREFIX + layerId);
    }

    /**
     * The beat one band of paint is measured under.
     *
     * <p>A switch over the two rather than a name composed per call, so a paint pass resolves its
     * section without allocating a string or reaching the registry.
     *
     * @param band which side of the map's nebula icons this pass is painting
     * @return the section that band's paint sits on
     */
    public static ProfileSection resolveRenderSection(MapOverlayBand band) {
        return switch (band) {
            case BENEATH_STARSCAPE_NEBULAE -> RENDER_BENEATH_STARSCAPE_NEBULAE;
            case ABOVE_STARSCAPE_NEBULAE -> RENDER_ABOVE_STARSCAPE_NEBULAE;
        };
    }
}
