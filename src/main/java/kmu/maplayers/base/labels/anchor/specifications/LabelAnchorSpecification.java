package kmu.maplayers.base.labels.anchor.specifications;

import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.settings.KmuMapLayerSettings;

/**
 * The modifiers the cluster-anchor search reads, gathered into one value so the search
 * takes its whole tuning surface as data rather than reaching into the settings
 * mid-computation.
 *
 * <p>The tuning splits into the search's own knobs - where it generates candidate lines
 * ({@link AnchorSearch}), how it scores their lean ({@link LeanScoring}), and which debug
 * lines each cluster carries ({@link AnchorDiagnostics}) - and the two halves of sizing a
 * name into a chosen line: the room a band is measured within
 * ({@link BandFitSpecification}) and the text sized into it ({@link NameFitSpecification}).
 * The last two are handed to the box fitter as they stand, so this record's shape is the
 * fitter's own surface rather than a repackaging of it. What shade the name then draws in
 * is deliberately absent: colour is a per-group decision, resolved by whoever knows what a
 * group means and handed to the search already resolved, so the tuning of the geometry
 * never carries a notion of who owns anything.
 *
 * @param search      where the search generates its candidate lines
 * @param scoring     how the search scores a candidate by its lean
 * @param diagnostics which debug lines each cluster carries
 * @param bandFit     the clearances and search precision each candidate is measured under
 * @param nameFit     how a chosen anchor's name is sized into its box
 */
public record LabelAnchorSpecification(
    AnchorSearch search,
    LeanScoring scoring,
    AnchorDiagnostics diagnostics,
    BandFitSpecification bandFit,
    NameFitSpecification nameFit) {

    // The floor a stored font-height tolerance is held to, matching the settings table's
    // own slider minimum. The slider binds what the screen can produce, never what the
    // file holds - a value stored before an edit to the table outlives it, and the file is
    // hand-editable - while a tolerance at or below zero names a precision no halving
    // reaches and the box fitter rejects it outright. Held here rather than at the read,
    // because this is where a raw setting becomes something the search is handed.
    private static final double MIN_FONT_HEIGHT_TOLERANCE = 0.05;

    // Reads the live tuning into its sub-records: the candidate-fan and scoring knobs from
    // the Dev "Label anchors" section, the diagnostic toggles that let the search skip the
    // extra candidates while no one is looking, the band-fit clearances and precision from
    // that same Dev section (the end-inset multiple resolved against the fixed border
    // channel here, so the fit works in plain distances), and the name-fit knobs from the
    // visuals "Map labels" section. The border trace comes from the same source the drawn
    // border renders with, so the anchor clips against the rings the player sees.
    public static LabelAnchorSpecification readFromLunaSettings() {
        return new LabelAnchorSpecification(
            new AnchorSearch(
                ClusterBorderTrace.readFromLunaSettings(),
                KmuMapLayerSettings.getMapAnchorDirectionCount(),
                KmuMapLayerSettings.getMapAnchorOffsetCount()),
            new LeanScoring(
                KmuMapLayerSettings.getMapAnchorVerticalPenaltyStrength(),
                KmuMapLayerSettings.getMapAnchorVerticalPenaltyExponent(),
                KmuMapLayerSettings.getMapAnchorMaxSlantDegrees()),
            new AnchorDiagnostics(
                KmuMapLayerSettings.getMapShowRejectedAxes(),
                KmuMapLayerSettings.getMapShowUnbiasedAxes()),
            new BandFitSpecification(
                KmuMapLayerSettings.getMapAnchorIconClearance(),
                KmuMapLayerSettings.getMapAnchorEndInsetMultiple() * CellShaper.BORDER_INSET_DISTANCE,
                holdFontToleranceAboveFloor(KmuMapLayerSettings.getMapAnchorFontHeightTolerance())),
            new NameFitSpecification(
                KmuMapLayerSettings.getMapNameMinFontSize(),
                KmuMapLayerSettings.getMapNameMaxFontSize(),
                KmuMapLayerSettings.getMapNameMaxLines(),
                KmuMapLayerSettings.getMapNameLineSpacing()));
    }

    // Holds a stored tolerance to the floor. Compared rather than clamped with Math.max,
    // which answers NaN for a NaN input: a stored value that is not a number fails this
    // test like any other out-of-range one and takes the floor, so a bad settings file
    // costs a finer search than asked for rather than a map that stops rebuilding.
    private static double holdFontToleranceAboveFloor(double tolerance) {
        return tolerance >= MIN_FONT_HEIGHT_TOLERANCE
            ? tolerance
            : MIN_FONT_HEIGHT_TOLERANCE;
    }
}
