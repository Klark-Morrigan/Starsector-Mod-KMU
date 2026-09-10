package kmu.maplayers.base.render;

import kmlib.profiling.ProfileSection;
import kmlib.profiling.SectionTerms;
import kmlib.profiling.budget.ProfileBudget;
import kmlib.starsector.SectorWalkCounters;
import kmlib.time.Timings;

import kmu.maplayers.base.profiling.MapFrameBudgets;

/**
 * What each part of a frame is called in a profiling report - one section per beat of the sequence,
 * plus the row a layer's own work sits on inside whichever beat is running - and what one call of
 * each is allowed. Which beat covers what, and why the refresh is not a beat of its own, is
 * <a href="README.md#what-a-frame-costs">what a frame costs</a>.
 *
 * <p>Published rather than spelled at each site because a row is only worth having if a reader can
 * find it: a consumer layer names the beat it runs under from here, and two sites spelling one beat
 * alike would be two rows that look like one.
 *
 * <p>Registered sections rather than strings because these are opened every frame, and a registered
 * section is found among the children of the open scope by reference rather than by hashing a name.
 *
 * <p>The bounds are here rather than at the sites that open the sections, because what the framework
 * promises is a property of the beat and not of whoever happens to be running in it: a consumer
 * reading its layer's rows under one of these beats is reading them against the same bound the
 * framework holds itself to.
 */
public final class MapFrameSections {

    // The two bounds, declared above the sections that take them: static fields initialise in the
    // order they are written, and a section cannot be handed a bound that does not exist yet.

    // What a beat may take. Asked as each beat ends, so a player who has decided their machine can
    // afford more moves the knob and the next frame is held to that.
    //
    // Taken off a bound value rather than read from the settings here: the knob deciding how much of
    // the framework is measured lives in that same settings section, and a file here naming the class
    // that holds one would have the other within reach. MapFrameBudgets states the rest.
    private static final SectionTerms FRAME_BEAT_BUDGET_TERMS = SectionTerms.DEFAULT.withBudget(
        ProfileBudget.allowingDurationPerCall(
            () -> Timings.convertMillisToNanos(MapFrameBudgets.resolveFrameBeatBudgetMillis())));

    // One traversal of the sector per refresh. The rule the framework's indexes exist to keep: a
    // rebuild reads what it needs from one walk, so a second is a pass that went looking for the
    // sector on its own rather than asking for what had already been gathered.
    private static final long ONE_SECTOR_WALK = 1L;

    private static final SectionTerms REFRESH_BUDGET_TERMS = SectionTerms.DEFAULT.withBudget(
        ProfileBudget.allowingCountPerCall(SectorWalkCounters.SECTOR_WALKS, ONE_SECTOR_WALK));

    /** The frame's single preparation. Its self time is what it costs outside {@link #REFRESH}. */
    public static final ProfileSection PREPARE =
        ProfileSection.registerSection("mapLayer.prepare", FRAME_BEAT_BUDGET_TERMS);

    /** Bringing a layer's draw lists up to date, inside {@link #PREPARE}. */
    public static final ProfileSection REFRESH =
        ProfileSection.registerSection("mapLayer.refresh", REFRESH_BUDGET_TERMS);

    /** One pass's cursor read, so a frame painted by several surfaces reports each read it made. */
    public static final ProfileSection HOVER_PUBLISH =
        ProfileSection.registerSection("mapLayer.hoverPublish", FRAME_BEAT_BUDGET_TERMS);

    /** The hover box a layer offers for the cell under the cursor, in the later UI pass. */
    public static final ProfileSection TOOLTIP =
        ProfileSection.registerSection("mapLayer.tooltip", FRAME_BEAT_BUDGET_TERMS);

    // The two paint beats, reached through resolveRenderSection so no caller holding a band spells
    // either name.
    private static final ProfileSection RENDER_BENEATH_STARSCAPE_NEBULAE =
        ProfileSection.registerSection("mapLayer.render.beneathNebulae", FRAME_BEAT_BUDGET_TERMS);

    private static final ProfileSection RENDER_ABOVE_STARSCAPE_NEBULAE =
        ProfileSection.registerSection("mapLayer.render.aboveNebulae", FRAME_BEAT_BUDGET_TERMS);

    // Under the framework's own namespace rather than bare, so every layer's row is found under one
    // prefix whoever registered the layer, and two mods picking the same layer id collide where the
    // registry already reports them rather than silently sharing a row.
    private static final String LAYER_SECTION_PREFIX = "mapLayer.layer.";

    private MapFrameSections() {
    }

    /**
     * The section one layer's work is measured under, within whichever beat is running.
     *
     * <p>Keyed by the layer's id, that being what the layer is known by everywhere else it is
     * recorded. Resolve it once and hold it: the concatenation and the registry lookup behind it
     * are not a per-frame cost, while the section it returns is.
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
