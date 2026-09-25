package kmu.maplayers.politicalmap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.layer.MapLayerStanding;
import kmu.maplayers.ownermap.MapLayerViewRegistry;

/**
 * The political map's pair: what the framework stands up when its tab is on the bar, and takes back
 * when the player takes that tab off.
 *
 * <p>{@link PoliticalMapInstaller} is what both halves are - a save heal, five listeners and a poll,
 * each stated beside the wiring it stands up. This is only what makes them reachable as a pair, so
 * the installer stays the utility it is and the framework never names it.
 *
 * <p>Its own type rather than the layer answering for itself, so what a layer <em>is</em> - a tab,
 * a label, a key, a renderer - stays apart from what it runs on a sector.
 */
public final class PoliticalMapStanding implements MapLayerStanding {

    // The layer's views, whose spotlights the save heal judges.
    private final MapLayerViewRegistry viewRegistry;

    /**
     * @param viewRegistry the standing layer's views, so the heal on standing up judges that layer's
     *                     spotlights under that layer's picks
     */
    public PoliticalMapStanding(MapLayerViewRegistry viewRegistry) {
        this.viewRegistry = viewRegistry;
    }

    @Override
    public void standLayerUpOn(SectorAPI sector) {
        PoliticalMapInstaller.installAll(sector, viewRegistry);
    }

    @Override
    public void standLayerDownFrom(SectorAPI sector) {
        PoliticalMapInstaller.uninstallAll(sector);
    }
}
