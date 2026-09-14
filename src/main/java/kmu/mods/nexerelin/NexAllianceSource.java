package kmu.mods.nexerelin;

import kmu.mods.nexerelin.alliances.AllianceRecord;
import kmu.mods.nexerelin.alliances.AllianceSource;

import java.util.ArrayList;
import java.util.List;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;

/**
 * The only class that imports {@code exerelin.*}: it reads Nexerelin's live alliance
 * manager and flattens each {@link Alliance} to a plain {@link AllianceRecord}. Kept
 * behind the {@link NexerelinAlliances} gate so the classloader never resolves it -
 * and so never seeks a Nexerelin class - on a Nex-free install.
 *
 * <p>The live read and the flattening are two steps, and only the first needs a running game.
 * Nexerelin's manager reads its own configuration in a static initialiser, so naming that class at
 * all is what commits a caller to a game being up; turning alliances already in hand into records
 * commits it to nothing but the alliances.
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
        return flattenAlliances(AllianceManager.getAllianceList());
    }

    /**
     * Turns alliances already in hand into records, reading each one's stable ID, its display name
     * and its members in rank order.
     *
     * <p>Apart from the read above because it is the half that asks nothing of the running game:
     * everything downstream is built on these records rather than on Nexerelin's own type, so what
     * this takes from where is the whole of the coupling.
     *
     * @param alliances the alliances to flatten, in the order the records should carry
     * @return one record per alliance
     */
    static List<AllianceRecord> flattenAlliances(List<Alliance> alliances) {

        var records = new ArrayList<AllianceRecord>(alliances.size());

        for (var alliance : alliances) {
            // uuId is the stable bloc ID; getMembersSorted() ranks members by descending
            // market size so element 0 is the dominant member the bloc colours off.
            records.add(new AllianceRecord(
                alliance.uuId,
                alliance.getName(),
                alliance.getMembersSorted()));
        }
        return records;
    }
}
