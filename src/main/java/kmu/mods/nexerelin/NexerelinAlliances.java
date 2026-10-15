package kmu.mods.nexerelin;

import kmlib.mods.nexerelin.NexerelinAllianceSource;
import kmlib.starsector.factions.alliances.FactionAlliances;

import kmu.maplayers.ownermap.owners.holders.alliances.AllianceGroupingFactory;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

/**
 * KMU's readings of the live alliance set: a {@link HolderGrouping} for the political map, and a
 * {@link FactionAlliances} for the rule that decides who would keep a partner's secret.
 *
 * <p>Two folds over one source and nothing else. Whether the mod that maintains alliances is
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
