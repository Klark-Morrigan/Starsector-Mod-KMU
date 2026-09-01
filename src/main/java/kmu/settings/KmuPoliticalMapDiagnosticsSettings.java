package kmu.settings;

/**
 * The political map's dev overlays: the per-cluster label anchors, the ring each cell's presence
 * band would run along, and the border-tracing view that layers the smoothing pipeline's stages.
 *
 * <p>Every one draws over or in place of the finished map to show how it was arrived at, which is
 * why they are one class rather than filed with the passes they report on: what they have in
 * common is being answers to "why does it look like that", and all three ship off.
 */
public final class KmuPoliticalMapDiagnosticsSettings {

    private static final String SHOW_CLUSTER_ANCHORS_FIELD =
        "kmu_map_dev_diagnostics_labels_boxes_areShown";
    private static final String SHOW_RIBBON_PATHS_FIELD =
        "kmu_map_dev_diagnostics_cells_ribbons_arePathsShown";
    private static final String DEBUG_BORDER_TRACING_FIELD =
        "kmu_map_dev_diagnostics_cells_borders_isDebugTracingShown";

    private static final boolean DEFAULT_SHOW_CLUSTER_ANCHORS = false;
    private static final boolean DEFAULT_SHOW_RIBBON_PATHS = false;
    private static final boolean DEFAULT_DEBUG_BORDER_TRACING = false;

    private KmuPoliticalMapDiagnosticsSettings() {
    }

    /**
     * @return whether the political map draws the per-cluster label anchors - a dot at each
     *         contiguous cluster's centre and, in green, the accepted label line its fit produced;
     *         off by default. Master switch for the anchor overlay: the framework's rejected- and
     *         unbiased-axis toggles only add lines while this is on
     */
    public static boolean getPoliticalMapShowClusterAnchors() {
        return KmuLunaSettings.readBoolean(
            SHOW_CLUSTER_ANCHORS_FIELD,
            DEFAULT_SHOW_CLUSTER_ANCHORS);
    }

    /**
     * @return whether the political map draws the ring each cell's presence band would run along,
     *         coloured by what that cell's room lets a band do with it - green where the authored
     *         inset held, yellow where the pad had to be given up, red where no band is laid at
     *         all; off by default. Shows nothing while the bands themselves are switched off,
     *         there being no layout to report on
     */
    public static boolean shouldShowPoliticalMapRibbonPaths() {
        return KmuLunaSettings.readBoolean(
            SHOW_RIBBON_PATHS_FIELD,
            DEFAULT_SHOW_RIBBON_PATHS);
    }

    /**
     * @return whether to replace the normal political-map render with the border-tracing diagnostic
     *         that layers the smoothing pipeline's stages (base, despiked, rounded) in distinct
     *         colours; off by default. Respects the two smoothing gates, so a stage draws only when
     *         its pass ran
     */
    public static boolean shouldTraceBordersForDebug() {
        return KmuLunaSettings.readBoolean(
            DEBUG_BORDER_TRACING_FIELD,
            DEFAULT_DEBUG_BORDER_TRACING);
    }
}
