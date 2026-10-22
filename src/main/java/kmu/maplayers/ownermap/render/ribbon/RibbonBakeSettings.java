package kmu.maplayers.ownermap.render.ribbon;

import kmu.maplayers.base.ribbon.RibbonStyle;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;
import kmu.settings.KmuOwnerMapRibbonSettings;
import kmu.settings.RibbonNameClearanceChoice;

/**
 * Every answer the player gives about the bands, read once for one bake: whether they draw, the
 * sizes they are laid at, how their runs are laid, the room they give the names, and whether the
 * diagnostic overlay traces their paths.
 *
 * <p>The single seam those knobs are fetched through. A bake reads them in one go and hands the
 * parts on, so a slider moved mid-pass cannot leave two cells drawn to different designs, and no
 * stage of the bake reaches for a setting on its own.
 *
 * <p>The proportions the whole design rests on are the player's, because they are a matter of
 * taste against a real sector: how thick a band has to be before it reads, how far a run should
 * reach relative to the parting beside it, and how deep inside a border a band sits are all
 * answers that only look right or wrong on the map. What stays authored here is the mitre limit,
 * which is not one of those - it is the angle past which a corner's mitre becomes a spike, a
 * property of stroking a polyline rather than of how the readout looks.
 *
 * @param areRibbonsDrawn          whether any cell draws a band. Off, a bake builds nothing to lay
 *                                 one from, so the counting leaves the rebuild along with the bands
 * @param ribbonStyle              the sizes every band is laid at
 * @param planRules                how far a band's runs reach, contested and uncontested
 * @param isRibbonKeptClearOfNames whether a band breaks around a cluster name drawn over its cell
 * @param nameClearance            how much room a name takes, where the bands keep clear of the
 *                                 names at all
 * @param isRibbonPathShown        whether the diagnostic overlay traces each considered cell's path
 */
public record RibbonBakeSettings(
    boolean areRibbonsDrawn,
    RibbonStyle ribbonStyle,
    RibbonPlanRules planRules,
    boolean isRibbonKeptClearOfNames,
    RibbonNameClearanceChoice nameClearance,
    boolean isRibbonPathShown) {

    // Where a corner stops being a corner and becomes a spike. Two half-widths of overshoot is
    // about a 53-degree turn; anything sharper bevels, which on a cell ring is rare enough to be
    // the exception it is meant to be.
    private static final double MITER_SPIKE_LIMIT = 2.0;

    /**
     * Reads every band knob as one snapshot.
     *
     * <p>Every knob is read whether the bands draw or not. They are ten reads of values LunaLib
     * already holds, and zeroes standing in for a switched-off pass would be a second design a
     * bake could be handed by mistake.
     *
     * @return how this bake lays its bands out, or that it lays none
     */
    public static RibbonBakeSettings readFromLunaSettings() {

        var ribbonStyle = new RibbonStyle(
            KmuOwnerMapRibbonSettings.getOwnerMapRibbonWidth(),
            KmuOwnerMapRibbonSettings.getOwnerMapRibbonInsetPad(),
            MITER_SPIKE_LIMIT,
            KmuOwnerMapRibbonSettings.shouldAlwaysDrawOwnerMapRibbons());

        var planRules = RibbonPlanRules.createFromAuthoredLengths(
            new RibbonSegmentLengths(
                KmuOwnerMapRibbonSettings.getOwnerMapRibbonSegmentLength(),
                KmuOwnerMapRibbonSettings.getOwnerMapRibbonInterjectionLength()),
            KmuOwnerMapRibbonSettings.shouldShortenOwnerMapUncontestedRibbonRuns());

        return new RibbonBakeSettings(
            KmuOwnerMapRibbonSettings.shouldDrawOwnerMapRibbons(),
            ribbonStyle,
            planRules,
            KmuOwnerMapRibbonSettings.shouldKeepOwnerMapRibbonsClearOfNames(),
            KmuOwnerMapRibbonSettings.getOwnerMapRibbonNameClearance(),
            KmuOwnerMapDiagnosticsSettings.shouldShowOwnerMapRibbonPaths());
    }
}
