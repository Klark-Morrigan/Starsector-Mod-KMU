package kmu.starsector.nexerelin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reduces the live alliance set to a single change-detection token, so the sector
 * watcher can tell whether the alliances moved between polls with one int compare rather
 * than re-deriving the whole grouping every frame. A change to the token drives the
 * alliances view's rebuild; an unchanged token drives nothing.
 *
 * <p>Two things count: <em>who is allied</em> and <em>which member leads</em>. Each
 * alliance's canonical form is its id, its members as a sorted set, and its lead member
 * (Nexerelin's dominant-by-market-size, element 0). The sorted set makes a reshuffle
 * <em>below</em> the lead read as no change - it alters neither the membership nor the
 * colour - while the lead is carried explicitly because it sets the bloc's colour, so a
 * colour lead swap repaints even when the membership set is unchanged. A rename is not
 * captured; it repaints on the next full drawables rebuild (any settings or geometry
 * change).
 *
 * <p>The canonical lines are sorted before hashing, so the token is independent of the
 * order Nexerelin reports the alliances in (its list order is not guaranteed stable
 * between polls, and a bare positional hash would false-bump on a pure reorder). Sorting
 * a handful of alliances is trivial, so a plain sorted hash is preferred here over the
 * avalanche-then-sum fingerprint {@code PoliticalMapVisibility} needs for its hundreds of
 * systems. Pure over plain {@link AllianceRecord}s - no Nexerelin or Starsector type - so
 * it is exercised directly on hand-built records.
 */
public final class AllianceFingerprint {

    private AllianceFingerprint() {
    }

    /**
     * Computes the change token for a set of alliances.
     *
     * @param alliances the live alliance records, in any order
     * @return an int that is stable for a fixed membership and colour lead, and differs
     *         when an alliance forms, dissolves, gains or loses a member, or changes which
     *         member leads it
     */
    public static int compute(List<AllianceRecord> alliances) {
        List<String> canonicalLines = new ArrayList<>(alliances.size());
        for (AllianceRecord alliance : alliances) {
            List<String> members = alliance.membersSortedDescending();
            if (members.isEmpty()) {
                // A memberless alliance leaves no bloc and no colour in the grouping, so it
                // leaves no trace here either - matching what the map actually renders.
                continue;
            }
            List<String> sortedMembers = new ArrayList<>(members);
            Collections.sort(sortedMembers);
            // Canonical form: id, sorted member set, and lead member. The lead (element 0,
            // Nexerelin's dominant by market size) is carried apart from the set because it
            // sets the bloc's colour, so a lead swap moves the token even when the set is
            // unchanged while a reshuffle below the lead does not.
            canonicalLines.add(alliance.allianceId() + "|" + members.get(0) + "|" + sortedMembers);
        }
        // Sort so the token depends on membership and leads alone, not on Nexerelin's
        // reporting order; List.hashCode is then the standard positional 31-multiply fold.
        Collections.sort(canonicalLines);
        return canonicalLines.hashCode();
    }
}
