package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.visibility.systems.MapSectorFixture;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A one-system sector whose system reports a position the caller rewrites between polls, the way a
 * mobile system rewrites its own {@code getLocation()} every frame. The position is one mutable
 * vector handed back on every read rather than a queue of stubbed returns, so a caller moves the
 * system per poll rather than per {@code getLocation()} call - the walk's call count is the motion
 * tracker's business, not something a suite should encode.
 *
 * <p>The sector is unrouted - no star anchor, no jump point - so nothing here is drawn on the
 * normal gates; a caller decides admission through the visibility rules it opens its pass under.
 *
 * <p>Each instance holds its own system, so two of them stage the same system ID at two positions -
 * which is what a claim about two sectors not reading each other's observations needs.
 */
public final class MovableSystemSectorFake {

    /**
     * The rules under which this fake's system is drawn: the force override on, so the drawn-set
     * rule admits a system nothing else would put on the map. The shortest route to a tracked
     * system without also staging an economy that owns it.
     */
    public static final MapVisibilityRules FORCED_ONTO_MAP =
        new MapVisibilityRules(BASE_FOG, true);

    // Comfortably past the tracker's one-unit noise floor, so each move is unambiguous motion
    // rather than something that could read as float jitter.
    private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

    private final Vector2f livePosition = new Vector2f(0f, 0f);
    private final SectorAPI sectorMock;

    /**
     * @param systemId the ID the one staged system reports; it states no centre and no anchor, so
     *                 the key a motion observation is made under is this ID alone
     */
    public MovableSystemSectorFake(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);
        when(systemMock.getJumpPoints())
            .thenReturn(List.of());
        when(systemMock.getLocation())
            .thenReturn(livePosition);

        sectorMock = MapSectorFixture.buildUnroutedSectorOf(systemMock);
    }

    /**
     * @return the staged sector, for opening a reading over
     */
    public SectorAPI getSector() {
        return sectorMock;
    }

    /**
     * One poll's observation of this sector, opening the reading a poll opens: a pass built fresh
     * and discarded with the call, since a kept one would answer the next poll off the positions
     * this one saw.
     *
     * @param movingSystems the tracker being observed into
     * @param visibilityRules what the pass admits to the drawn set, which is what the motion walk
     *                        is scoped to
     * @return whether the moving set gained or lost a member on this observation
     */
    public boolean observePositionsInto(
            MovingSystems movingSystems,
            MapVisibilityRules visibilityRules) {

        return movingSystems.updateMovingSystems(
            MapVisibilityPass.over(sectorMock, visibilityRules));
    }

    /**
     * Drifts the staged system far enough that the next observation reads it as moving rather than
     * as sitting still.
     */
    public void moveSystemClearOfItsLastPosition() {
        livePosition.x += CLEAR_OF_THE_NOISE_FLOOR;
    }
}
