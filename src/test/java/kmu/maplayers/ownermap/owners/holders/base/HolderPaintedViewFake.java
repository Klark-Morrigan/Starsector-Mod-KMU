package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Test fixture: the smallest view painting holders. Answers every part with a value holding
 * nothing and names a bloc from a canned map, so a case about a shared holder default exercises
 * that default alone rather than whichever concrete view it borrowed to reach it.
 *
 * <p>The picker gate is supplied where a case is about which blocs survive it; it defaults to the
 * seam's own, which offers every bloc.
 */
public final class HolderPaintedViewFake
        implements HolderPaintedView {

    private final OwnerReadingFake readingFake;
    private final Predicate<String> selectableBlocGate;

    public HolderPaintedViewFake(Map<String, String> nameByBlocId) {

        this(nameByBlocId, null);
    }

    private HolderPaintedViewFake(Map<String, String> nameByBlocId, Predicate<String> selectableBlocGate) {

        this.readingFake = OwnerReadingFake.createNaming(nameByBlocId);
        this.selectableBlocGate = selectableBlocGate;
    }

    /**
     * A fake offering only the blocs a stated gate accepts, for a case about the gate rather than
     * about what surrounds it.
     *
     * @param nameByBlocId       the canned labels this fake names its blocs from
     * @param selectableBlocGate which of the walked blocs the fake's picker offers
     * @return the fake, gated
     */
    public static HolderPaintedViewFake createGatedFake(
            Map<String, String> nameByBlocId,
            Predicate<String> selectableBlocGate) {

        return new HolderPaintedViewFake(nameByBlocId, selectableBlocGate);
    }

    /**
     * @return the reading this fake answers with, naming blocs from its canned map
     */
    public OwnerReadingFake readOwnerReading() {
        return readingFake;
    }

    @Override
    public HolderGrouping resolveGrouping() {
        return HolderGrouping.identity();
    }

    @Override
    public OwnerAffiliation resolveContestAffiliation() {
        return OwnerAffiliation.NONE;
    }

    @Override
    public HolderProvider resolveHolderProvider() {
        return HolderProviderFake.createHoldingNothing();
    }

    @Override
    public SystemHolderResolveSource resolveSystemHolderResolveSource() {
        return SystemHolderResolveFake.createSourceHoldingNothing();
    }

    @Override
    public SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {
        return system -> RibbonPlan.NONE;
    }

    @Override
    public OwnerReading resolveOwnerReading(SectorAPI sector, HolderGrouping grouping) {
        return readingFake;
    }

    // The supplied gate, or the seam's own default (offer everything) when a case did not name one.
    @Override
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {

        return selectableBlocGate == null
            ? HolderPaintedView.super.resolveSelectableBlocGate(grouping)
            : selectableBlocGate;
    }

    @Override
    public OwnerCategories resolveCategories() {
        return OwnerCategoriesFake.SHARED_FAKE;
    }

    @Override
    public String getId() {
        return "holder_fake";
    }

    @Override
    public String getSegmentLabelKey() {
        return "holder_fake_label";
    }

    @Override
    public int getContentRevision(MapLayerRefreshBoard board) {
        return 0;
    }
}
