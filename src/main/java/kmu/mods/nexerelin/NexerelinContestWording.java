package kmu.mods.nexerelin;

import kmlib.mods.nexerelin.NexerelinPresence;

import kmu.maplayers.politicalmap.tooltip.ContestWording;

/**
 * The soft-dependency gate behind how a political box words presence beside a system's holder.
 * Nexerelin moves systems between factions - invasions take them, and the sector the player is
 * looking at is a snapshot of a war in progress - so on an install carrying it the groups around a
 * holder really are contesting the system. Vanilla transfers nothing: its holdings are the ones the
 * sector was generated with and they stay that way, so the same groups are neighbours rather than
 * rivals.
 *
 * <p>Read live rather than settled once, for the reason
 * {@link kmu.maplayers.politicalmap.tooltip.ContestWordingSource} sets out - the boxes that ask
 * are built before the game has necessarily stood its mod set up.
 *
 * <p>Kept apart from {@link NexerelinAlliances} although both turn on the mod being present: that
 * class folds the live alliance set, which is state of a running game, while this one reads the mod
 * set alone - and binding a wording through an alliance reader would tie the heading to a set it has
 * nothing to do with.
 */
public final class NexerelinContestWording {

    private NexerelinContestWording() { // utility class, no instances.
    }

    /**
     * The wording this install calls for.
     *
     * <p>Asked through the library's own gate rather than through a mod ID and a hop of this class's
     * own: the ID is the mod's, not KMU's, and the gate answers "not present" before the game has
     * stood its settings up instead of throwing there - which here reads as the vanilla wording, the
     * safe way round, since a box drawn that early has no sector to report a contest over anyway.
     *
     * @return the contested wording where Nexerelin is enabled, else the plain presence wording
     */
    public static ContestWording resolveWording() {
        return NexerelinPresence.isModEnabled()
            ? ContestWording.CONTESTED
            : ContestWording.PRESENT;
    }
}
