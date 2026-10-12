package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;

/**
 * Test fixture: the smallest view the shared pipeline can run against. Answers the seam's
 * abstract methods with fixed values and a reading answering nothing, so a test of a shared default
 * exercises that default alone rather than whichever concrete view it borrowed to reach it.
 *
 * <p>The owner source is supplied where a case is about what the pipeline hands a source; it
 * defaults to one owning nothing, which is what a case about anything else wants.
 */
public final class OwnerPaintedViewFake implements OwnerPaintedView {

    private final OwnerReadingFake readingFake;
    private final OwnerSource ownerSource;

    private int contentRevision;

    OwnerPaintedViewFake() {
        this(new OwnerSourceFake());
    }

    public OwnerPaintedViewFake(OwnerSource ownerSource) {

        this.readingFake = OwnerReadingFake.createAnsweringNothing();
        this.ownerSource = ownerSource;
    }

    // The reading answering nothing beside the supplied source, or one owning nothing.
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
    // constant - the "never forces a rebuild on its own" case the seam allows - until a case moves it.
    @Override
    public int getContentRevision(MapLayerRefreshBoard board) {
        return contentRevision;
    }

    /**
     * Moves the fingerprint, as a view whose live inputs changed would.
     *
     * @param contentRevision what {@link #getContentRevision} answers from here on
     */
    public void setContentRevision(int contentRevision) {
        this.contentRevision = contentRevision;
    }

    // A fixture layer's categories, so no suite over the tier borrows a real layer's set.
    @Override
    public OwnerCategories resolveCategories() {
        return OwnerCategoriesFake.SHARED_FAKE;
    }
}
