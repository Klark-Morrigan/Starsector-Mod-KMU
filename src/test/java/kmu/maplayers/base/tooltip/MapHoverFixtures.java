package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.installation.MapLayerInstallations;

import java.util.List;

/**
 * The surroundings a hover box needs before it will draw at all: a system under the cursor, published
 * on the machinery of the sector whose map it is.
 *
 * <p>The box reads its hover and its sector off one installation, so a hover published anywhere else
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
     * @return that sector's installation, which is what the box resolves as it draws
     */
    public static MapLayerInstallation hoverASystemOnAnInstalledSector(
            SectorAPI sector,
            String systemId) {

        var installation = MapLayerInstallations.installMachineryOn(sector);

        hoverASystemIn(installation, systemId);

        return installation;
    }

    /**
     * Puts the cursor over {@code systemId} on machinery a case already holds - which is how a hover
     * belonging to some sector other than the one being drawn is posed.
     *
     * @param installation the machinery the hover is published on
     * @param systemId     the id of the system under the cursor, alone in its cluster since what a
     *                     hover lights beside it turns nothing about whether a box draws
     */
    public static void hoverASystemIn(MapLayerInstallation installation, String systemId) {

        installation
            .resolveHoverState()
            .publishHover(new MapHover(systemId, List.of(systemId)));
    }
}
