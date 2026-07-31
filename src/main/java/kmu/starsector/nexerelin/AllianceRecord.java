package kmu.starsector.nexerelin;

import java.util.List;

/**
 * One live alliance flattened to plain data, so the grouping logic can be built and
 * tested without touching a Nexerelin type. A {@link NexAllianceSource} reads these
 * off the running game and {@link AllianceGroupingFactory} folds them into an
 * {@code HolderGrouping}; every field here is a snapshot taken at read time, not a
 * live handle back into Nexerelin.
 *
 * @param allianceId              the alliance's stable id (its Nexerelin {@code uuId}),
 *                                used as the bloc id every member faction shares
 * @param name                    the alliance's display name, carried through as the
 *                                bloc's label
 * @param membersSortedDescending the member faction ids ordered by descending market
 *                                size, so element 0 is the de-facto dominant member
 *                                whose palette the bloc paints in
 */
public record AllianceRecord(
        String allianceId,
        String name,
        List<String> membersSortedDescending) {

    /**
     * Defensively snapshots the member list into an immutable copy, so a record handed
     * to the factory cannot change under it after the read.
     */
    public AllianceRecord {
        membersSortedDescending = List.copyOf(membersSortedDescending);
    }
}
