package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.render.style.HolderCategories;
import kmu.maplayers.ownermap.render.style.OwnerCategories;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Test fixture: the smallest view the shared pipeline can run against. Answers the seam's
 * abstract methods with fixed values and names a bloc from a canned map, so a test of a shared
 * default exercises that default alone rather than whichever concrete view it borrowed to reach
 * it.
 *
 * <p>The owner source and the picker gate are supplied where a case is about what the pipeline
 * hands a source, or about which blocs survive the gate; each defaults to an answer naming nothing,
 * which is what a case about anything else wants.
 */
public final class OwnerPaintedViewFake implements OwnerPaintedView {

    private final OwnerReadingFake readingFake;
    private final OwnerSource ownerSource;
    private final Predicate<String> selectableBlocGate;

    OwnerPaintedViewFake(Map<String, String> nameByBlocId) {
        this(nameByBlocId, new OwnerSourceFake(), null);
    }

    public OwnerPaintedViewFake(Map<String, String> nameByBlocId, OwnerSource ownerSource) {
        this(nameByBlocId, ownerSource, null);
    }

    private OwnerPaintedViewFake(
            Map<String, String> nameByBlocId,
            OwnerSource ownerSource,
            Predicate<String> selectableBlocGate) {

        this.readingFake = OwnerReadingFake.createNaming(nameByBlocId);
        this.ownerSource = ownerSource;
        this.selectableBlocGate = selectableBlocGate;
    }

    /**
     * A fake offering only the blocs a stated gate accepts, for a case about the gate rather than
     * about what surrounds it. A named factory rather than a second two-argument constructor, whose
     * lambda a reader could not tell from an owner source at the call site.
     *
     * @param nameByBlocId       the canned labels this fake names its blocs from
     * @param selectableBlocGate which of the walked blocs the fake's picker offers
     * @return the fake, gated
     */
    public static OwnerPaintedViewFake createGatedFake(
            Map<String, String> nameByBlocId,
            Predicate<String> selectableBlocGate) {

        return new OwnerPaintedViewFake(nameByBlocId, new OwnerSourceFake(), selectableBlocGate);
    }

    /**
     * @return the reading this fake answers with, naming blocs from its canned map
     */
    public OwnerReadingFake readOwnerReading() {
        return readingFake;
    }

    // The reading naming blocs from the canned map - null for an unknown bloc, the unresolved-name
    // case the seam allows - beside the supplied source, or one owning nothing.
    @Override
    public ViewReading resolveViewReading(SectorAPI sector) {
        return new ViewReading(this, readingFake, ownerSource);
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

    // The holder layers' categories, which is what every view in the mod declares.
    @Override
    public OwnerCategories resolveCategories() {
        return HolderCategories.INSTANCE;
    }
}
