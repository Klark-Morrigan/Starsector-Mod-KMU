package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.holders.HolderProvider;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

import java.util.Map;

/**
 * Test fixture: the smallest view the shared pipeline can run against. Answers the seam's
 * abstract methods with fixed values and names a bloc from a canned map, so a test of a shared
 * default exercises that default alone rather than whichever concrete view it borrowed to reach
 * it.
 *
 * <p>The holding source is supplied where a case is about what the pipeline hands a provider;
 * the default inherits the seam's own, which is what a case about anything else wants.
 */
public final class PoliticalMapViewFake implements PoliticalMapView {

    private final Map<String, String> nameByBlocId;
    private final HolderProvider holderProvider;

    PoliticalMapViewFake(Map<String, String> nameByBlocId) {
        this(nameByBlocId, null);
    }

    public PoliticalMapViewFake(Map<String, String> nameByBlocId, HolderProvider holderProvider) {
        this.nameByBlocId = nameByBlocId;
        this.holderProvider = holderProvider;
    }

    // The supplied source, or the seam's own default when a case did not name one.
    @Override
    public HolderProvider resolveHolderProvider() {
        return holderProvider == null
            ? PoliticalMapView.super.resolveHolderProvider()
            : holderProvider;
    }

    @Override
    public String getId() {
        return "fake";
    }

    @Override
    public String getSegmentLabelKey() {
        return "fake_label";
    }

    @Override
    public int getContentRevision() {
        return 0;
    }

    @Override
    public HolderGrouping resolveGrouping() {
        return HolderGrouping.identity();
    }

    // No mechanic paints this view, so no system carries a band. Stated rather than defaulted
    // because the seam has no default: naming one mechanic's planner on it would put that
    // mechanic in front of every view.
    @Override
    public SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {
        return system -> RibbonPlan.NONE;
    }

    @Override
    public boolean shouldUseIndependentStyle(
            String blocId,
            HolderGrouping grouping,
            ElementStyleAdjustment adjustment) {
        return false;
    }

    @Override
    public ElementStyleAdjustment resolveBlocStyleAdjustment(String blocId, HolderGrouping grouping) {
        return ElementStyleAdjustment.NONE;
    }

    // The canned label, so a test reads a name it chose rather than one a faction lookup produced.
    // Returns null for an unknown bloc, the unresolved-name case the seam allows.
    @Override
    public String resolveName(
            String blocId,
            HolderGrouping grouping,
            SectorAPI sector,
            FactionNameFormatChoice nameFormat) {
        return nameByBlocId.get(blocId);
    }
}
