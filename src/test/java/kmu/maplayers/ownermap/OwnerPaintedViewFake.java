package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;

import java.util.Map;

/**
 * Test fixture: the smallest view the shared pipeline can run against. Answers the seam's
 * abstract methods with fixed values and names an owner from a canned map, so a test of a shared
 * default exercises that default alone rather than whichever concrete view it borrowed to reach
 * it.
 *
 * <p>The owner source is supplied where a case is about what the pipeline hands a source; it
 * defaults to one owning nothing, which is what a case about anything else wants.
 */
public final class OwnerPaintedViewFake implements OwnerPaintedView {

    private final OwnerReadingFake readingFake;
    private final OwnerSource ownerSource;

    OwnerPaintedViewFake(Map<String, String> nameByOwnerId) {
        this(nameByOwnerId, new OwnerSourceFake());
    }

    public OwnerPaintedViewFake(Map<String, String> nameByOwnerId, OwnerSource ownerSource) {

        this.readingFake = OwnerReadingFake.createNaming(nameByOwnerId);
        this.ownerSource = ownerSource;
    }

    /**
     * @return the reading this fake answers with, naming owners from its canned map
     */
    public OwnerReadingFake readOwnerReading() {
        return readingFake;
    }

    // The reading naming owners from the canned map - null for an unknown owner, the unresolved-name
    // case the seam allows - beside the supplied source, or one owning nothing.
    @Override
    public ViewReading resolveViewReading(SectorAPI sector) {
        return new ViewReading(this, readingFake, ownerSource);
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

    // A fixture layer's categories, so no suite over the tier borrows a real layer's set.
    @Override
    public OwnerCategories resolveCategories() {
        return OwnerCategoriesFake.SHARED_FAKE;
    }
}
