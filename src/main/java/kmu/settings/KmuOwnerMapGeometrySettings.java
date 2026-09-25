package kmu.settings;

import kmlib.math.ranges.Ranges;

/**
 * The shapes the map is built out of: the cell partition each system's cell is cut from, the
 * two passes that smooth a cluster border, and the hatch a shared fill is cut with.
 *
 * <p>These sit on the framework's {@code Map - Dev} tab because the geometry they move is the
 * framework's, and they are filed here because the owner-painted tier is what reads them - the
 * framework takes the values as parameters and never fetches a setting. A knob is filed by who
 * reads it, not by whose geometry it moves.
 *
 * <p>The cell knobs reseed the partition itself, so they feed the geometry rebuild; every border
 * and hatch knob below restyles fixed geometry through the drawables rebuild. Which of the two a
 * knob triggers is the whole of what its cost at runtime depends on.
 */
public final class KmuOwnerMapGeometrySettings {

    private static final String CELL_BOUND_SEGMENTS_FIELD =
        "kmu_map_dev_cellGeometry_boundSegments";
    private static final String CELL_RADIUS_FIELD =
        "kmu_map_dev_cellGeometry_radius";

    // The border shaping in pipeline order after the framework's own tracing, each pass led by the
    // gate that turns it off whole without zeroing the knobs beneath it.
    private static final String SAND_SPIKES_FIELD =
        "kmu_map_dev_spikeSanding_isEnabled";
    private static final String BORDER_SPIKE_HEIGHT_FIELD =
        "kmu_map_dev_spikeSanding_height";
    private static final String BORDER_SPIKE_ANGLE_FIELD =
        "kmu_map_dev_spikeSanding_angle";

    private static final String ROUND_CORNERS_FIELD =
        "kmu_map_dev_cornerRounding_isEnabled";
    private static final String BORDER_CORNER_RADIUS_FIELD =
        "kmu_map_dev_cornerRounding_radius";
    private static final String BORDER_CORNER_SEGMENTS_FIELD =
        "kmu_map_dev_cornerRounding_segments";
    private static final String BORDER_CHAMFER_ANGLE_FIELD =
        "kmu_map_dev_cornerRounding_chamferAngle";
    private static final String BORDER_ROUND_BELOW_ANGLE_FIELD =
        "kmu_map_dev_cornerRounding_angleThreshold";

    private static final String HATCH_SPACING_FIELD =
        "kmu_map_dev_hatchFill_spacing";
    private static final String HATCH_ANGLE_FIELD =
        "kmu_map_dev_hatchFill_angle";
    private static final String HATCH_WIDTH_FIELD =
        "kmu_map_dev_hatchFill_width";
    private static final String HATCH_SMOOTHING_FIELD =
        "kmu_map_dev_hatchFill_isSmoothed";
    private static final String HATCH_JOIN_TOLERANCE_FIELD =
        "kmu_map_dev_hatchJoining_tolerance";

    // Mirrors VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS, the geometric default this setting
    // overrides; restated as a literal like every fallback here so this class stays decoupled from
    // the geometry library.
    private static final int DEFAULT_CELL_BOUND_SEGMENTS = 48;
    private static final double DEFAULT_CELL_RADIUS = 4000.0;
    private static final boolean DEFAULT_SAND_SPIKES = false;
    private static final double DEFAULT_BORDER_SPIKE_HEIGHT = 150.0;
    private static final double DEFAULT_BORDER_SPIKE_ANGLE_DEGREES = 60.0;
    private static final boolean DEFAULT_ROUND_CORNERS = true;
    private static final double DEFAULT_BORDER_CORNER_RADIUS = 300.0;
    private static final int DEFAULT_BORDER_CORNER_SEGMENTS = 3;
    private static final double DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES = 35.0;

    // Under where a cluster border's own arc samples meet, which is about 173 degrees at the
    // shipped cell-bound sampling, with a couple of degrees to spare rather than right at the edge:
    // the exact joint angle is a fact about the sampling, and a default flush against it would flip
    // to rounding every sample the moment that sampling coarsens. Above the joints the pass arcs the
    // samples along one run as well as the joins between runs - about twice the border vertices for
    // a line that moves some ten world units, a tenth of a pixel at the zoom a sector is read at.
    private static final double DEFAULT_BORDER_ROUND_BELOW_ANGLE_DEGREES = 170.0;

    // About ten lines across a default-reach cell, on a 45-degree diagonal.
    private static final double DEFAULT_HATCH_SPACING = 1000.0;
    private static final double DEFAULT_HATCH_ANGLE_DEGREES = 45.0;

    // Half the gap inked and half left open, the reading furthest from either failure the width
    // can approach: nothing at all to see at the bottom of the range, and a fill indistinguishable
    // from the solid one beside it at the top.
    private static final double DEFAULT_HATCH_WIDTH_PERCENT = 50.0;

    // Floored where a line still reads as a line rather than as the rasteriser's own minimum
    // stroke, which is what anything thinner comes out as however far it is dragged down.
    private static final double MIN_HATCH_WIDTH_PERCENT = 5.0;

    // Held short of the whole gap, the one width that is not a hatch at all: ink meeting ink
    // paints the solid fill this pattern exists to read apart from.
    private static final double MAX_HATCH_WIDTH_PERCENT = 90.0;

    // Hard-edged, which is the state the hatch was tuned and verified at.
    private static final boolean DEFAULT_HATCH_SMOOTHING = false;

    // A tenth of a percent of the spacing, which sits between two measured bounds rather than two
    // guessed ones. On a real sector the crossings the merge has to close disagree by around 1e-14
    // of the spacing - double rounding, not geometry - while the narrowest gap it must leave open,
    // where a territory genuinely stops, is around 0.4 of it. This is far enough above the rounding
    // to survive a coarser tessellation and still some four hundred times below the nearest real
    // break.
    //
    // Stored 0..100 in the CSV as a percentage and exposed as the fraction the merge works in,
    // because the slider rounds a Double to two decimals: a fraction authored directly would
    // collapse to zero the moment it was dragged.
    private static final double DEFAULT_HATCH_JOIN_TOLERANCE_PERCENT = 0.2;

    private KmuOwnerMapGeometrySettings() {
    }

    /**
     * @return the sides of the polygon that rounds each system cell's reach into empty space; 48
     *         by default. Every segment is a vertex on each frontier cell, so this is the lever
     *         for trading map FPS against frontier smoothness
     */
    public static int getOwnerMapCellBoundSegments() {
        return KmuLunaSettings.readInt(CELL_BOUND_SEGMENTS_FIELD, DEFAULT_CELL_BOUND_SEGMENTS);
    }

    /**
     * @return how far each system's territory reaches into empty space before its frontier bound
     *         closes it off, in world units; 4000.0 by default. Higher lets distant systems'
     *         territories meet and merge
     */
    public static double getOwnerMapCellRadius() {
        return KmuLunaSettings.readDouble(CELL_RADIUS_FIELD, DEFAULT_CELL_RADIUS);
    }

    /**
     * @return the corner-rounding radius of the national border, in world units; 300.0 by default
     */
    public static double getOwnerMapBorderCornerRadius() {
        return KmuLunaSettings.readDouble(
            BORDER_CORNER_RADIUS_FIELD,
            DEFAULT_BORDER_CORNER_RADIUS);
    }

    /** @return the arc segments per rounded corner of the national border; 3 by default */
    public static int getOwnerMapBorderCornerSegments() {
        return KmuLunaSettings.readInt(
            BORDER_CORNER_SEGMENTS_FIELD,
            DEFAULT_BORDER_CORNER_SEGMENTS);
    }

    /**
     * @return the interior angle below which a national-border corner is chamfered flat rather
     *         than rounded, in radians; 35 degrees by default. Authored in degrees and converted
     *         here, the rounding math working in radians
     */
    public static double getOwnerMapBorderChamferAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_CHAMFER_ANGLE_FIELD,
            DEFAULT_BORDER_CHAMFER_ANGLE_DEGREES));
    }

    /**
     * @return the interior angle above which a national-border corner keeps its vertex rather than
     *         being rounded, in radians; 170 degrees by default. Authored in degrees and converted
     *         here. A border is arcs sampled at fixed angles joined by straight runs, so a setting
     *         past where those samples meet rounds them too - twice the vertices for a line in all
     *         but the same place
     */
    public static double getOwnerMapBorderRoundBelowAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_ROUND_BELOW_ANGLE_FIELD,
            DEFAULT_BORDER_ROUND_BELOW_ANGLE_DEGREES));
    }

    /**
     * @return the depth up to which a sharp corner counts as a spike to sand off the border before
     *         rounding, in world units; 150.0 by default. A sharp corner jutting farther than this
     *         is real shape and is kept; zero disables the pass
     */
    public static double getOwnerMapBorderSpikeHeight() {
        return KmuLunaSettings.readDouble(BORDER_SPIKE_HEIGHT_FIELD, DEFAULT_BORDER_SPIKE_HEIGHT);
    }

    /**
     * @return the interior angle below which a shallow corner counts as a spike to sand off the
     *         border before rounding, in radians; 60 degrees by default. Authored in degrees and
     *         converted here; zero disables the pass
     */
    public static double getOwnerMapBorderSpikeAngleRadians() {
        return Math.toRadians(KmuLunaSettings.readDouble(
            BORDER_SPIKE_ANGLE_FIELD,
            DEFAULT_BORDER_SPIKE_ANGLE_DEGREES));
    }

    /**
     * @return whether the national-border corner rounding runs; on by default. Off leaves the
     *         radius, segments, and chamfer knobs unread, and also covers the factionless
     *         (decivilised and uninhabited) cell outlines, which reuse this same pass, so the whole
     *         map's corners round or not together
     */
    public static boolean shouldRoundBorderCorners() {
        return KmuLunaSettings.readBoolean(ROUND_CORNERS_FIELD, DEFAULT_ROUND_CORNERS);
    }

    /**
     * @return whether the spike-sanding pass runs before corner rounding; off by default. Off
     *         leaves the spike height and angle knobs unread and leaves needle or cusp protrusions
     *         in the border for the rounding to meet
     */
    public static boolean shouldSandBorderSpikes() {
        return KmuLunaSettings.readBoolean(SAND_SPIKES_FIELD, DEFAULT_SAND_SPIKES);
    }

    /**
     * @return the perpendicular gap between the diagonal hatch lines filling the shared cluster,
     *         in world units; 1000.0 by default
     */
    public static double getOwnerMapHatchSpacing() {
        return KmuLunaSettings.readDouble(HATCH_SPACING_FIELD, DEFAULT_HATCH_SPACING);
    }

    /**
     * @return the direction the shared-cluster hatch lines run, in radians; 45 degrees off
     *         horizontal by default. Authored in degrees and converted here, the hatch math working
     *         in radians
     */
    public static double getOwnerMapHatchAngleRadians() {
        return Math.toRadians(
            KmuLunaSettings.readDouble(HATCH_ANGLE_FIELD, DEFAULT_HATCH_ANGLE_DEGREES));
    }

    /**
     * @return the width the shared-cluster hatch strokes at, as a fraction of the hatch
     *         spacing; 0.5 by default, held between 0.05 and 0.9. Authored as a percentage of the
     *         spacing and converted here, the stroke scaling its pixel width off the spacing per
     *         frame so the pattern holds its proportions at every zoom. Clamped here as well as
     *         bounded on the slider because LunaLib prunes nothing: this row was once read as a
     *         pixel count over a wider range, and a value stored under that reading is handed
     *         straight to this one
     */
    public static double getOwnerMapHatchWidthFraction() {

        var widthPercent =
            KmuLunaSettings.readDouble(HATCH_WIDTH_FIELD, DEFAULT_HATCH_WIDTH_PERCENT);

        return Ranges.clampInto(widthPercent, MIN_HATCH_WIDTH_PERCENT, MAX_HATCH_WIDTH_PERCENT)
            / KmuLunaSettings.PERCENT_PER_UNIT;
    }

    /**
     * @return whether the shared-cluster hatch is antialiased rather than stroked hard-edged;
     *         off by default. Covers the hatch alone - the fills and borders around it pick their
     *         own line quality - and a GL bridge that ignores the smoothing hint leaves it inert
     */
    public static boolean shouldSmoothHatchLines() {
        return KmuLunaSettings.readBoolean(HATCH_SMOOTHING_FIELD, DEFAULT_HATCH_SMOOTHING);
    }

    /**
     * @return how far apart two of one hatch line's crossings may sit and still merge into one
     *         segment, as a fraction of the hatch spacing; 0.002 by default. Authored as a
     *         percentage of the spacing and converted here
     */
    public static double getOwnerMapHatchJoinToleranceFraction() {
        return KmuLunaSettings.readDouble(
            HATCH_JOIN_TOLERANCE_FIELD,
            DEFAULT_HATCH_JOIN_TOLERANCE_PERCENT)
            / KmuLunaSettings.PERCENT_PER_UNIT;
    }
}
