package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import java.util.Map;

/**
 * Test fixture: the smallest view the shared option assembly can run against. Answers the seam's
 * abstract methods with fixed values and names a bloc from a canned map, so a test of {@link
 * PoliticalMapView#buildSelectableBlocs} exercises the shared default alone rather than whichever
 * concrete view it borrowed to reach it.
 */
final class PoliticalMapViewFake implements PoliticalMapView {

    private final Map<String, String> nameByBlocId;

    PoliticalMapViewFake(Map<String, String> nameByBlocId) {
        this.nameByBlocId = nameByBlocId;
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
