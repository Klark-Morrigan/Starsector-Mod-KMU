package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.HolderProviderFake;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

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
public final class OwnerPaintedViewFake implements OwnerPaintedView {

    private final Map<String, String> nameByBlocId;
    private final HolderProvider holderProvider;
    private final Predicate<String> selectableBlocGate;

    OwnerPaintedViewFake(Map<String, String> nameByBlocId) {
        this(nameByBlocId, null, null);
    }

    public OwnerPaintedViewFake(Map<String, String> nameByBlocId, HolderProvider holderProvider) {
        this(nameByBlocId, holderProvider, null);
    }

    private OwnerPaintedViewFake(
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
    public static OwnerPaintedViewFake createGatedFake(
            Map<String, String> nameByBlocId,
            Predicate<String> selectableBlocGate) {

        return new OwnerPaintedViewFake(nameByBlocId, null, selectableBlocGate);
    }

    // The supplied source, or one holding nothing when a case did not name one. The seam carries
    // no default of its own, a default there having had to name one mechanic's resolver.
    @Override
    public HolderProvider resolveHolderProvider() {
        return holderProvider == null
            ? HolderProviderFake.createHoldingNothing()
            : holderProvider;
    }

    // The supplied gate, or the seam's own default (offer everything) when a case did not name one.
    @Override
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        return selectableBlocGate == null
            ? OwnerPaintedView.super.resolveSelectableBlocGate(grouping)
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

    // Nobody stands together, which is the answer a layer without allies gives.
    @Override
    public HolderGrouping resolveContestGrouping() {
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
