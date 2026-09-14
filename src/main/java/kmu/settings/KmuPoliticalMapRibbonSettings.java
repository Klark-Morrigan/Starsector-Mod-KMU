package kmu.settings;

/**
 * The presence band a cell strokes inside its own outline to name the blocs holding colonies in
 * that system: whether it is drawn at all, how it gives way to a cluster name lying over it, and
 * the lengths its runs and partings are measured in.
 *
 * <p>One class because a band is laid out from all of them at once - a width, an inset, a run and
 * a parting are one layout decision expressed as four numbers, and reading any of them apart from
 * the rest says nothing about what a cell will draw.
 */
public final class KmuPoliticalMapRibbonSettings {

    // The band's on/off is a real switch rather than a zeroed opacity: a band paints in the palette
    // colours of whoever is present and so has no shade of its own to take away.
    private static final String RIBBON_ENABLED_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_areEnabled";
    private static final String RIBBON_UNCONTESTED_SHORT_RUNS_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_shouldShortenUncontestedRuns";
    private static final String RIBBON_KEEP_CLEAR_OF_NAMES_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_shouldKeepClearOfNames";
    private static final String RIBBON_NAME_CLEARANCE_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_nameClearance";
    private static final String RIBBON_ALWAYS_DRAWN_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_areAlwaysDrawn";
    private static final String RIBBON_WIDTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_width";
    private static final String RIBBON_INSET_PAD_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_insetPad";
    private static final String RIBBON_SEGMENT_LENGTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_segmentLength";
    private static final String RIBBON_INTERJECTION_LENGTH_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_interjectionLength";

    private static final boolean DEFAULT_RIBBON_ENABLED = true;
    private static final boolean DEFAULT_RIBBON_UNCONTESTED_SHORT_RUNS = true;
    private static final boolean DEFAULT_RIBBON_KEEP_CLEAR_OF_NAMES = true;

    // A cell that says nothing and a cell refused the room to speak look identical on the map, so
    // the default takes the reading that shows the sector: the room a band wants is this design's
    // own business, and no player asked for a system's holdings to go unreported because its name
    // landed across the ring.
    private static final boolean DEFAULT_RIBBON_ALWAYS_DRAWN = true;

    // The words rather than the box they were fitted into: a fitted box is the chord the search
    // accepted, and a chord is accepted as soon as it beats the widest line, so it reserves room at
    // both ends that nothing is ever drawn in.
    private static final RibbonNameClearanceChoice DEFAULT_RIBBON_NAME_CLEARANCE =
        RibbonNameClearanceChoice.WORDS;

    // Settled by eye on the shipped sector rather than derived: a band as thick as its own clearance
    // reads as a stripe laid inside the border at the zooms the map is used at, where a finer one
    // disappeared into the border it follows.
    private static final double DEFAULT_RIBBON_WIDTH = 200.0;
    private static final double DEFAULT_RIBBON_INSET_PAD = 200.0;

    // Whole widths rather than fractions: a run is a count of holdings expressed as a length, and
    // how large a width is in the world is the width knob's business.
    private static final int DEFAULT_RIBBON_SEGMENT_LENGTH = 3;
    private static final int DEFAULT_RIBBON_INTERJECTION_LENGTH = 1;

    private KmuPoliticalMapRibbonSettings() {
    }

    /**
     * @return whether a cell draws the banded stroke naming the blocs that hold colonies in that
     *         system; on by default. Off skips the counting as well as the drawing, so a rebuild
     *         pays nothing for the bands at all
     */
    public static boolean shouldDrawPoliticalMapRibbons() {
        return KmuLunaSettings.readBoolean(RIBBON_ENABLED_FIELD, DEFAULT_RIBBON_ENABLED);
    }

    /**
     * @return whether the band on an uncontested cell draws each colony at a single width rather
     *         than at the authored run length; on by default. A cell some rival holds something in
     *         draws at the authored length whatever this says
     */
    public static boolean shouldShortenPoliticalMapUncontestedRibbonRuns() {
        return KmuLunaSettings.readBoolean(
            RIBBON_UNCONTESTED_SHORT_RUNS_FIELD,
            DEFAULT_RIBBON_UNCONTESTED_SHORT_RUNS);
    }

    /**
     * @return whether a band breaks around each drawn cluster name lying over its cell; on by
     *         default. Off lays every band along its whole ring and lets a name draw across it
     */
    public static boolean shouldKeepPoliticalMapRibbonsClearOfNames() {
        return KmuLunaSettings.readBoolean(
            RIBBON_KEEP_CLEAR_OF_NAMES_FIELD,
            DEFAULT_RIBBON_KEEP_CLEAR_OF_NAMES);
    }

    /**
     * @return how much of its ring a band gives up to a cluster name lying over it: FITTED_BOX the
     *         whole box the name's placement reserved, WORDS each drawn line's own measured extent
     *         (the default). Unread while the bands do not keep clear of the names at all
     */
    public static RibbonNameClearanceChoice getPoliticalMapRibbonNameClearance() {
        return KmuLunaSettings.readChoice(
            RIBBON_NAME_CLEARANCE_FIELD,
            DEFAULT_RIBBON_NAME_CLEARANCE);
    }

    /**
     * @return whether a cell with a band to draw draws one even where its ring leaves no room; on
     *         by default, giving up the covering name and then the inset rather than the band. Off,
     *         such a cell draws nothing, which makes a bare cell ambiguous between having nothing
     *         to say and having been refused the room to say it
     */
    public static boolean shouldAlwaysDrawPoliticalMapRibbons() {
        return KmuLunaSettings.readBoolean(
            RIBBON_ALWAYS_DRAWN_FIELD,
            DEFAULT_RIBBON_ALWAYS_DRAWN);
    }

    /**
     * @return how thick the presence band is drawn, in world units; 200.0 by default. The size
     *         every other size in the band is a multiple of, and a world quantity so a band holds
     *         the same share of its cell's outline at every zoom
     */
    public static double getPoliticalMapRibbonWidth() {
        return KmuLunaSettings.readDouble(RIBBON_WIDTH_FIELD, DEFAULT_RIBBON_WIDTH);
    }

    /**
     * @return how far clear of the cell's own border the band's near edge runs, in world units;
     *         200.0 by default. A cell with room for neither this gap nor the band's width draws
     *         no band
     */
    public static double getPoliticalMapRibbonInsetPad() {
        return KmuLunaSettings.readDouble(RIBBON_INSET_PAD_FIELD, DEFAULT_RIBBON_INSET_PAD);
    }

    /** @return how far the run one colony draws reaches, in band widths; 3 by default */
    public static int getPoliticalMapRibbonSegmentLength() {
        return KmuLunaSettings.readInt(
            RIBBON_SEGMENT_LENGTH_FIELD,
            DEFAULT_RIBBON_SEGMENT_LENGTH);
    }

    /**
     * @return how far a parting in the band reaches, in band widths; 1 by default. One length
     *         serves both the parting between two colonies of one bloc and the one closing that
     *         bloc's run, since two parting lengths in one band would read as a claim about the
     *         blocs they part. The setting screen calls it a separator, the counting rule an
     *         interjection
     */
    public static int getPoliticalMapRibbonInterjectionLength() {
        return KmuLunaSettings.readInt(
            RIBBON_INTERJECTION_LENGTH_FIELD,
            DEFAULT_RIBBON_INTERJECTION_LENGTH);
    }
}
