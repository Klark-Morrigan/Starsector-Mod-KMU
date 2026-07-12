package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

/**
 * The modifiers the cluster-anchor search reads, gathered into one value so the
 * search takes its whole tuning surface as data rather than reaching into the
 * settings mid-computation.
 *
 * @param borderTrace            the national-border trace the anchor clips against -
 *                               shared with the territory build, so the anchor sees
 *                               the same rings the player does by construction
 * @param endInsetDistance       how far each end of the clear interval pulls inward,
 *                               in world units - the border-inset multiple already
 *                               resolved to a distance
 * @param iconClearance          the keep-out radius around each system icon, world
 *                               units
 * @param directionCount         the number of directions the candidate fan spans
 *                               over the half-circle
 * @param offsetCount            the number of parallel lines swept per direction
 * @param verticalPenaltyStrength how much font height a line straying from the
 *                               cluster's preferred lean may give up and still win,
 *                               0 (pure longest) to 1 (a perpendicular line scores
 *                               zero)
 * @param verticalPenaltyExponent the exponent on the line's deviation from the lean
 *                               in the score, concentrating the penalty toward the
 *                               perpendicular
 * @param maxSlantDegrees        the ceiling on the cluster-axis lean the score
 *                               prefers, in degrees; 0 forces level labels, 90 lets
 *                               the lean follow a tall cluster's axis to vertical
 * @param showRejectedAxis       whether a cluster whose accepted line collapsed also
 *                               carries the best rejected candidate the search found,
 *                               for the red diagnostic line
 * @param showUnbiasedAxis       whether each cluster also carries the pure-longest
 *                               accepted line (the winner with no vertical penalty),
 *                               for the yellow diagnostic line
 * @param nameMinFontSize        the smallest per-line font height a fit will accept,
 *                               world units - the readability floor; a chord that
 *                               cannot hold even one line this tall collapses to the
 *                               dot
 * @param nameMaxFontSize        the largest per-line font height a fit will grow to,
 *                               world units, so a roomy cluster does not mint an
 *                               oversized label
 * @param nameMaxLines           the most lines a name may wrap into, spending girth
 *                               to shorten the length its widest line needs
 * @param nameLineSpacing        the line-height multiple between stacked lines,
 *                               at least 1
 * @param factionOuterColor      the outer-border palette choice a core faction's
 *                               name inherits its colour from, resolved against the
 *                               owner's palette
 * @param independentOuterColor  the outer-border palette choice independent space's
 *                               name inherits its colour from
 * @param factionNameOpacity     the opacity a faction cluster's name draws at, 0..1,
 *                               fading only the faction group's names
 * @param independentNameOpacity the opacity an independent-held cluster's name draws
 *                               at, 0..1, fading only the independent group's names
 */
record LabelAnchorSpecification(PoliticalBorderTrace borderTrace, double endInsetDistance, double iconClearance,
        int directionCount, int offsetCount, double verticalPenaltyStrength,
        double verticalPenaltyExponent, double maxSlantDegrees, boolean showRejectedAxis,
        boolean showUnbiasedAxis, double nameMinFontSize, double nameMaxFontSize,
        int nameMaxLines, double nameLineSpacing, FactionPaletteChoice factionOuterColor,
        FactionPaletteChoice independentOuterColor, double factionNameOpacity,
        double independentNameOpacity) {

    // Reads the live tuning: the anchor knobs from the Dev "Label anchors" section,
    // the name-fit knobs from the visuals "Faction names" section, the same two
    // outer-border colour choices the national border reads (so the name inherits the
    // border's colour) and the two per-group name opacities beside them, plus the same
    // border trace the national border renders with. The end-inset multiple is resolved
    // against the fixed border channel here, so the search works in plain distances. The
    // two diagnostic-line toggles ride along so the search only builds the extra
    // candidates while someone is looking at them.
    static LabelAnchorSpecification readFromLunaSettings() {
        return new LabelAnchorSpecification(
                PoliticalBorderTrace.readFromLunaSettings(),
                KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                        * PoliticalMapStyle.BORDER_INSET_DISTANCE,
                KmuLunaSettings.getPoliticalMapAnchorIconClearance(),
                KmuLunaSettings.getPoliticalMapAnchorDirectionCount(),
                KmuLunaSettings.getPoliticalMapAnchorOffsetCount(),
                KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyStrength(),
                KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyExponent(),
                KmuLunaSettings.getPoliticalMapAnchorMaxSlantDegrees(),
                KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                KmuLunaSettings.getPoliticalMapShowUnbiasedAxes(),
                KmuLunaSettings.getPoliticalMapNameMinFontSize(),
                KmuLunaSettings.getPoliticalMapNameMaxFontSize(),
                KmuLunaSettings.getPoliticalMapNameMaxLines(),
                KmuLunaSettings.getPoliticalMapNameLineSpacing(),
                KmuLunaSettings.getFactionOuterBorderColor(),
                KmuLunaSettings.getIndependentOuterBorderColor(),
                KmuLunaSettings.getFactionNameOpacity(),
                KmuLunaSettings.getIndependentNameOpacity());
    }
}
