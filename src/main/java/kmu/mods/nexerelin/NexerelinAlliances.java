package kmu.mods.nexerelin;

import kmlib.mods.nexerelin.NexerelinAllianceSource;
import kmlib.starsector.factions.alliances.FactionAlliances;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.mods.nexerelin.alliances.AllianceFingerprint;
import kmu.mods.nexerelin.alliances.AllianceGroupingFactory;

/**
 * KMU's readings of the live alliance set: a {@link HolderGrouping} for the political map, a
 * {@link FactionAlliances} for the rule that decides who would keep a partner's secret, and a
 * fingerprint for the watcher that notices either changing.
 *
 * <p>Three folds over one source and nothing else. Whether the mod that maintains alliances is
 * installed, and what its alliances are, is the library's to answer - so nothing here names that
 * mod's types, defers a class reference or knows an install without it is possible. Each fold
 * reads the source afresh, alliances forming and dissolving while a campaign runs.
 */
public final class NexerelinAlliances {

    private NexerelinAlliances() {
        // utility class, no instances.
    }

    /**
     * Whether the alliances view can be offered at all.
     *
     * <p>Stable for a session, a mod set not changing in play, so a caller may compute it once.
     *
     * @return true when the mod that maintains alliances is present
     */
    public static boolean isAvailable() {
        return NexerelinAllianceSource.isModEnabled();
    }

    /**
     * A membership token for the live alliance set, which the sector watcher polls to notice
     * alliances forming, dissolving or changing members between passes.
     *
     * <p>No alliances fingerprint to a fixed value of their own, so an install without the mod
     * polls a steady token and never bumps the alliance revision - which needs no case of its own
     * here, the empty fold being that value.
     *
     * @return the fingerprint of the alliances standing
     */
    public static int computeAllianceFingerprint() {
        return AllianceFingerprint.compute(NexerelinAllianceSource.readAllianceRecords());
    }

    /**
     * The memberships the visibility rule reads.
     *
     * @return which factions stand together, or nobody standing with anybody where there are no
     *         alliances
     */
    public static FactionAlliances resolveFactionAlliances() {
        return FactionAlliances.buildFrom(NexerelinAllianceSource.readAllianceRecords());
    }

    /**
     * The grouping the political map paints blocs by.
     *
     * <p>No alliances yield {@link HolderGrouping#identity()} itself rather than a fold that
     * would equal it. The fold allocates three maps and this answer is resolved per rebuild on
     * every install without the mod, where it is the only answer there ever is - so the shared
     * grouping is handed back rather than rebuilt to look like it.
     *
     * @return the alliance grouping, or the faction grouping where there are no alliances
     */
    public static HolderGrouping resolveGrouping() {

        var allianceRecords = NexerelinAllianceSource.readAllianceRecords();

        return allianceRecords.isEmpty()
            ? HolderGrouping.identity()
            : AllianceGroupingFactory.buildFrom(allianceRecords);
    }
}
