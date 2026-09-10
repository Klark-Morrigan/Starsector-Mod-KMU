package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.layer.MapLayerStanding;

/**
 * The political map's pair: what the framework stands up when its tab is on the bar, and takes back
 * when the player takes that tab off.
 *
 * <p>{@link PoliticalMapInstaller} is what both halves are - a save heal, four listeners and a poll,
 * each stated beside the wiring it stands up. This is only what makes them reachable as a pair, so
 * the installer stays the utility it is and the framework never names it.
 *
 * <p>Its own type rather than the layer answering for itself, so what a layer <em>is</em> - a tab,
 * a label, a key, a renderer - stays apart from what it runs on a sector.
 */
public final class PoliticalMapStanding implements MapLayerStanding {

    @Override
    public void standLayerUpOn(SectorAPI sector) {
        PoliticalMapInstaller.installAll(sector);
    }

    @Override
    public void standLayerDownFrom(SectorAPI sector) {
        PoliticalMapInstaller.uninstallAll(sector);
    }
}
