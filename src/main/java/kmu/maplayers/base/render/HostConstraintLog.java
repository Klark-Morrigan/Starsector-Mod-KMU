package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;

import org.apache.log4j.Logger;

/**
 * Says at DEBUG when the render constraint starts standing a sector's passes down, and when it lets a
 * pass through again.
 *
 * <p>With the layers kept to the vanilla maps, a pass from a mod's own map draws nothing, and nothing
 * else says so. A report that the layers are missing on such a map cannot then be told from a layer
 * that broke. One line on each change, never one per pass: a stood-down pass recurs every frame.
 *
 * <p>The first pass admitted says nothing, since admitting is what the layers do on any vanilla map.
 *
 * <p>One per machinery, since the passes are a sector's. Released with the machinery.
 */
final class HostConstraintLog
        implements InstalledMachinery {

    private static final Logger LOG = Global.getLogger(HostConstraintLog.class);

    private static final String STOOD_DOWN_LINE =
        "Map layers stood a pass down: they are kept to the vanilla maps and none is drawn."
            + " A pass from a mod's own map draws nothing while that holds.";

    private static final String ADMITTED_LINE =
        "Map layers admitted a pass again: a vanilla map is drawn.";

    // Whether the last pass asked about was stood down.
    private boolean isStandingPassesDown;

    @Override
    public void disposeMachinery() {
        isStandingPassesDown = false;
    }

    /**
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's log
     */
    static HostConstraintLog resolveLogIn(SectorMapMachinery machinery) {
        return machinery.resolveMachinery(HostConstraintLog.class, HostConstraintLog::new);
    }

    /**
     * Records what the constraint decided for one pass, saying so where that differs from the pass
     * before it.
     *
     * @param isPassAdmitted whether the constraint let the pass draw
     */
    void recordPassVerdict(boolean isPassAdmitted) {

        if (isPassAdmitted != isStandingPassesDown) {
            return;
        }

        isStandingPassesDown = !isPassAdmitted;
        LOG.debug(isStandingPassesDown ? STOOD_DOWN_LINE : ADMITTED_LINE);
    }
}
