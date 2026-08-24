package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;

/**
 * Writes the player's own half of the sighting register as they travel: whatever stands where they
 * arrive, and whatever stands where they leave.
 *
 * <p>One of the two routes an observation is made by, the other being a place's own inhabitants -
 * which no journey announces and which is therefore swept for rather than listened for.
 *
 * <p>Both ends of the move are taken because a stay is not an instant. A colony that appears while
 * the player is in a system was not there to be recorded on arrival, and is caught on the way out;
 * one that leaves during the stay is gone by then and is not recorded again, so the register keeps
 * saying it was last seen where it actually was.
 *
 * <p>Driven by the location change rather than by a daily script for the reason the register
 * itself carries no clock: what the player can see only changes when the player moves. The cost is
 * two systems' colonies per journey and nothing at all between journeys.
 *
 * <p>Holds the sector it writes to, so it is a per-save object: installed on load and replaced on
 * the next one, rather than carried into a save and restored beside a fresh one.
 */
public final class ColonySightingRecorder implements CurrentLocationChangedListener {

    private final SectorAPI sector;

    /**
     * @param sector the sector whose memory holds the register; a null one leaves the recorder
     *               inert rather than refusing to install, a missing register costing sightings
     *               and nothing else
     */
    public ColonySightingRecorder(SectorAPI sector) {
        this.sector = sector;
    }

    /**
     * Records what stands in each end of the move as seen there.
     *
     * <p>The place being left is recorded first, since it is the stay that has just ended and the
     * one whose contents may have changed since it began. Either end that is not a star system
     * records nothing.
     *
     * @param previousLocation where the player was; null records nothing for that end
     * @param currentLocation  where the player now is; null records nothing for that end
     */
    @Override
    public void reportCurrentLocationChanged(
            LocationAPI previousLocation,
            LocationAPI currentLocation) {

        SectorColonySightings.recordSightingsIn(sector, previousLocation);
        SectorColonySightings.recordSightingsIn(sector, currentLocation);
    }
}
