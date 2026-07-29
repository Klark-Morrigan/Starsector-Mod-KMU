package kmu.maplayers.base.labels.anchor.specifications;

import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.render.regions.PoliticalBorderTrace;
import kmu.settings.KmuLunaSettings;

/**
 * The modifiers the cluster-anchor search reads, gathered into one value so the search
 * takes its whole tuning surface as data rather than reaching into the settings
 * mid-computation.
 *
 * <p>The tuning splits into the search's own knobs - where it generates candidate lines
 * ({@link AnchorSearch}), how it scores their lean ({@link LeanScoring}), and which debug
 * lines each cluster carries ({@link AnchorDiagnostics}) - and how a chosen anchor's name is
 * sized into the box the fit accepted ({@link NameFitSpecification}). What shade that name
 * then draws in is deliberately absent: colour is a per-group decision, resolved by whoever
 * knows what a group means and handed to the search already resolved, so the tuning of the
 * geometry never carries a notion of who owns anything.
 *
 * @param search      where the search generates and clips its candidate lines
 * @param scoring     how the search scores a candidate by its lean
 * @param diagnostics which debug lines each cluster carries
 * @param nameFit     how a chosen anchor's name is sized into its box
 */
public record LabelAnchorSpecification(
        AnchorSearch search,
        LeanScoring scoring,
        AnchorDiagnostics diagnostics,
        NameFitSpecification nameFit) {

    // Reads the live tuning into its sub-records: the search geometry and scoring knobs
    // from the Dev "Label anchors" section (the end-inset multiple resolved against the
    // fixed border channel here, so the search works in plain distances), the diagnostic
    // toggles that let the search skip the extra candidates while no one is looking, and the
    // name-fit knobs from the visuals "Faction names" section. The border trace comes from
    // the same source the drawn border renders with, so the anchor clips against the rings
    // the player sees.
    public static LabelAnchorSpecification readFromLunaSettings() {
        return new LabelAnchorSpecification(
                new AnchorSearch(
                        PoliticalBorderTrace.readFromLunaSettings(),
                        KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                                * CellShaper.BORDER_INSET_DISTANCE,
                        KmuLunaSettings.getPoliticalMapAnchorIconClearance(),
                        KmuLunaSettings.getPoliticalMapAnchorDirectionCount(),
                        KmuLunaSettings.getPoliticalMapAnchorOffsetCount()),
                new LeanScoring(
                        KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyStrength(),
                        KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyExponent(),
                        KmuLunaSettings.getPoliticalMapAnchorMaxSlantDegrees()),
                new AnchorDiagnostics(
                        KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                        KmuLunaSettings.getPoliticalMapShowUnbiasedAxes()),
                new NameFitSpecification(
                        KmuLunaSettings.getPoliticalMapNameMinFontSize(),
                        KmuLunaSettings.getPoliticalMapNameMaxFontSize(),
                        KmuLunaSettings.getPoliticalMapNameMaxLines(),
                        KmuLunaSettings.getPoliticalMapNameLineSpacing()));
    }
}
