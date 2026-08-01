package kmu.starsector.nexerelin;

import java.util.ArrayList;
import java.util.List;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;

/**
 * The only class that imports {@code exerelin.*}: it reads Nexerelin's live alliance
 * manager and flattens each {@link Alliance} to a plain {@link AllianceRecord}. Kept
 * behind the {@link NexerelinAlliances} gate so the classloader never resolves it -
 * and so never seeks a Nexerelin class - on a Nex-free install.
 */
final class NexAllianceSource implements AllianceSource {

    /**
     * Snapshots every current alliance. Guards the manager itself because
     * {@link AllianceManager#getManager()} returns null before the manager is created
     * (early in a session) while {@link AllianceManager#getAllianceList()} would
     * dereference it unchecked - so an empty list here means "no alliances yet", never
     * a crash.
     *
     * @return one record per live alliance, or empty when none exist yet
     */
    @Override
    public List<AllianceRecord> readAlliances() {
        if (AllianceManager.getManager() == null) {
            return List.of();
        }
        List<AllianceRecord> records = new ArrayList<>();

        for (Alliance alliance : AllianceManager.getAllianceList()) {
            // uuId is the stable bloc id; getMembersSorted() ranks members by descending
            // market size so element 0 is the dominant member the bloc colours off.
            records.add(new AllianceRecord(
                alliance.uuId,
                alliance.getName(),
                alliance.getMembersSorted()));
        }
        return records;
    }
}
