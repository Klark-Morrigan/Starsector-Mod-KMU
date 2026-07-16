package kmu.maplayers.politicalmap.base.render.labels.anchor.specifications;

import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.politicalmap.base.geometry.FrontierSettings;
import kmu.maplayers.politicalmap.base.render.PoliticalBorderTrace;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;
import kmu.settings.KmuLunaSettings;

/**
 * The modifiers the cluster-anchor search reads, gathered into one value so the search
 * takes its whole tuning surface as data rather than reaching into the settings
 * mid-computation.
 *
 * <p>The tuning splits into the search's own knobs - where it generates candidate lines
 * ({@link AnchorSearch}), how it scores their lean ({@link LeanScoring}), and which debug
 * lines each cluster carries ({@link AnchorDiagnostics}) - and how a chosen anchor's name
 * is drawn: how it is sized ({@link NameFitSpecification}) and, per owner group, coloured and faded
 * ({@link NameGroupStyle}). The placement search reads the first three and the name fit;
 * the label styling reads only the two group styles.
 *
 * @param search           where the search generates and clips its candidate lines
 * @param scoring          how the search scores a candidate by its lean
 * @param diagnostics      which debug lines each cluster carries
 * @param nameFit          how a chosen anchor's name is sized into its box
 * @param factionNames     how a core faction's names are coloured and faded
 * @param independentNames how independent space's names are coloured and faded
 */
public record LabelAnchorSpecification(
        AnchorSearch search,
        LeanScoring scoring,
        AnchorDiagnostics diagnostics,
        NameFitSpecification nameFit,
        NameGroupStyle factionNames,
        NameGroupStyle independentNames) {

    // Reads the live tuning into its sub-records: the search geometry and scoring knobs
    // from the Dev "Label anchors" section (the end-inset multiple resolved against the
    // fixed border channel here, so the search works in plain distances), the diagnostic
    // toggles that let the search skip the extra candidates while no one is looking, the
    // name-fit knobs from the visuals "Faction names" section, and per owner group the
    // outer-border colour choice the name inherits (the same the national border reads)
    // beside that group's name opacity. The border trace comes from the same source the
    // national border renders with, carrying the pass's frontier snapshot, so the anchor
    // clips against the frontier-pushed rings the player sees rather than a plain-inset trace.
    public static LabelAnchorSpecification readFromLunaSettings(FrontierSettings frontier) {
        return new LabelAnchorSpecification(
                new AnchorSearch(
                        PoliticalBorderTrace.readFromLunaSettings(frontier),
                        KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                                * PoliticalMapStyle.BORDER_INSET_DISTANCE,
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
                        KmuLunaSettings.getPoliticalMapNameLineSpacing()),
                new NameGroupStyle(
                        KmuLunaSettings.getFactionOuterBorderColor(),
                        KmuLunaSettings.getFactionNameOpacity()),
                new NameGroupStyle(
                        KmuLunaSettings.getIndependentOuterBorderColor(),
                        KmuLunaSettings.getIndependentNameOpacity()));
    }
}
