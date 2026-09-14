package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.holders.HolderProvider;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Test fixture: the smallest view the shared pipeline can run against. Answers the seam's
 * abstract methods with fixed values and names a bloc from a canned map, so a test of a shared
 * default exercises that default alone rather than whichever concrete view it borrowed to reach
 * it.
 *
 * <p>The holding source and the picker gate are supplied where a case is about what the pipeline
 * hands a provider, or about which blocs survive the gate; each defaults to the seam's own, which is
 * what a case about anything else wants.
 */
public final class PoliticalMapViewFake implements PoliticalMapView {

    private final Map<String, String> nameByBlocId;
    private final HolderProvider holderProvider;
    private final Predicate<String> selectableBlocGate;

    PoliticalMapViewFake(Map<String, String> nameByBlocId) {
        this(nameByBlocId, null, null);
    }

    public PoliticalMapViewFake(Map<String, String> nameByBlocId, HolderProvider holderProvider) {
        this(nameByBlocId, holderProvider, null);
    }

    private PoliticalMapViewFake(
            Map<String, String> nameByBlocId,
            HolderProvider holderProvider,
            Predicate<String> selectableBlocGate) {

        this.nameByBlocId = nameByBlocId;
        this.holderProvider = holderProvider;
        this.selectableBlocGate = selectableBlocGate;
    }

    /**
     * A fake offering only the blocs a stated gate accepts, for a case about the gate rather than
     * about what surrounds it. A named factory rather than a second two-argument constructor, whose
     * lambda a reader could not tell from a holder source at the call site.
     *
     * @param nameByBlocId       the canned labels this fake names its blocs from
     * @param selectableBlocGate which of the walked blocs the fake's picker offers
     * @return the fake, gated
     */
    public static PoliticalMapViewFake createGatedFake(
            Map<String, String> nameByBlocId,
            Predicate<String> selectableBlocGate) {

        return new PoliticalMapViewFake(nameByBlocId, null, selectableBlocGate);
    }

    // The supplied source, or the seam's own default when a case did not name one.
    @Override
    public HolderProvider resolveHolderProvider() {
        return holderProvider == null
            ? PoliticalMapView.super.resolveHolderProvider()
            : holderProvider;
    }

    // The supplied gate, or the seam's own default (offer everything) when a case did not name one.
    @Override
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        return selectableBlocGate == null
            ? PoliticalMapView.super.resolveSelectableBlocGate(grouping)
            : selectableBlocGate;
    }

    @Override
    public String getId() {
        return "fake";
    }

    @Override
    public String getSegmentLabelKey() {
        return "fake_label";
    }

    // Samples nothing live, so the board it is handed contributes nothing and the fingerprint holds
    // constant - the "never forces a rebuild on its own" case the seam allows.
    @Override
    public int getContentRevision(MapLayerRefreshBoard board) {
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
    public ElementStyleAdjustment resolveBlocStyleAdjustment(
            String blocId,
            HolderGrouping grouping,
            ContentInputs contentInputs) {
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
