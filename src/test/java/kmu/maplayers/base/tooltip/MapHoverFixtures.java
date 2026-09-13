package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;

import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

/**
 * The surroundings a hover box needs before it will draw at all: a system under the cursor, published
 * on the machinery of the sector whose map it is.
 *
 * <p>The box reads its hover and its sector off one machinery, so a hover published anywhere else
 * is a hover of no sector and nothing draws for it. Staging one is therefore the shared setup of
 * every case about what the cursor is over, and it is here rather than copied per suite so the box
 * and the dispatcher that draws it are posed the same way - a suite that staged it differently would
 * pass or fail for a reason of its own making.
 *
 * <p>Public because the dispatcher's own install site is exercised from the composition root's
 * package, while the box is exercised from this one.
 */
public final class MapHoverFixtures {

    private MapHoverFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * Puts the cursor over {@code systemId} on a sector with the map machinery installed on it, which
     * is how a frame with something to draw a box about is posed.
     *
     * @param sector   the sector whose map the cursor is over
     * @param systemId the id of the system under it
     * @return that sector's machinery, which is what the box resolves as it draws
     */
    public static SectorMapMachinery hoverASystemOnAnInstalledSector(
            SectorAPI sector,
            String systemId) {

        var machinery = SectorMapMachineryIndex.installMachineryOn(sector);

        hoverASystemIn(machinery, systemId);

        return machinery;
    }

    /**
     * Puts the cursor over {@code systemId} on machinery a case already holds - which is how a hover
     * belonging to some sector other than the one being drawn is posed.
     *
     * @param machinery the machinery the hover is published on
     * @param systemId     the id of the system under the cursor, alone in its cluster since what a
     *                     hover lights beside it turns nothing about whether a box draws
     */
    public static void hoverASystemIn(SectorMapMachinery machinery, String systemId) {

        // Keyed by the id alone, which is the key a system posed with no centre and no anchor is
        // read off - so the box resolves the posed system back out of the sector.
        var systemKey = buildCellKey(systemId);

        machinery
            .resolveHoverState()
            .publishHover(new MapHover(systemKey, List.of(systemKey)));
    }
}
