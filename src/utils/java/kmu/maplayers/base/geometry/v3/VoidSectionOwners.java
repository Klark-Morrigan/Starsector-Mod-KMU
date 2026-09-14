package kmu.maplayers.base.geometry.v3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Who holds a section of void, decided by the cells around it.
 *
 * <p>The generalisation of the rule that already shapes a whole pocket: there, one owner is
 * asked to ring the void on every side and any dissent - another owner, or a single unowned
 * cell - hands the pocket back to nobody. That answers for a pocket because a pocket either is
 * or is not interior to one area. It cannot answer for a SECTION, which is a piece of map that
 * has to end up somewhere: every section is going to be drawn, and refusing to decide only
 * means deciding it is void.
 *
 * <p>So every cell around a section has a claim on it, and the largest claim takes it. The
 * cells rather than the frontage they offer, because that is what the pocket rule counted and
 * generalising it should not quietly change what is being weighed - and because a section's
 * outline is closed by its cuts as well as its arcs, so a length measured round it would hand
 * whichever cells happen to sit at a pinch a share of the vote that has nothing to do with how
 * much of the section they bound.
 *
 * <p><b>Ownerless is a claimant, not a default.</b> The unowned cells around a section vote
 * together, exactly as an owner's do, and they take the section when there are more of them
 * than of anyone else. That is the difference from the pocket rule, where one unowned
 * neighbour was enough to empty the whole thing - a rule that leaves every pocket on both
 * fixtures ownerless and so decides nothing.
 *
 * <p>A tie is ownerless too, and for the same reason rather than as a fallback: a section no
 * one claimant holds more of than another is between them, which is what void between owners
 * means. Reported as contested so a reader can see it was a tie rather than a walkover.
 */
public final class VoidSectionOwners {

    // The claim to beat when a section has exactly one claimant. Nobody else is standing, so
    // the bar is any claim at all.
    private static final int NO_CLAIM = 0;

    private VoidSectionOwners() {
    }

    /**
     * One owner's claim on a section, as the cells it holds around it.
     *
     * @param owner the claimant, or null for the unowned cells voting together
     * @param cells how many of the section's cells are its
     */
    public record OwnerClaim(
        String owner,
        int cells) {
    }

    /**
     * Who holds a section, and who else had a claim on it.
     *
     * <p>The runners-up travel with the answer because the answer alone cannot be judged. A
     * section taken 5 cells to 1 and one taken 3 cells to 3 report the same owner, and only one
     * of those is a border a reader would recognise.
     *
     * @param owner  the owner that holds it, or null where the unowned cells took it or no
     *               claimant was ahead
     * @param claims every claim on it, largest first, with the unowned cells among them
     */
    public record SectionOwner(
        String owner,
        List<OwnerClaim> claims) {

        /**
         * Whether more than one claimant had cells around it.
         *
         * @return whether it was contested at all
         */
        boolean isContested() {
            return claims.size() > 1;
        }
    }

    /**
     * Decides who holds one section.
     *
     * @param section     the section
     * @param ownerBySite each site's owner, index-aligned with the sites and null where the
     *                    site is unowned
     * @return the owner and the field it beat
     */
    static SectionOwner resolveSectionOwner(
            VoidSection section,
            List<String> ownerBySite) {

        var claims = countClaims(section, ownerBySite);

        // Ahead outright, or nobody is: a claim only level with the next one is a section
        // between two owners rather than inside either.
        var leader = claims.get(0);
        var runnerUp = claims.size() > 1 ? claims.get(1).cells() : NO_CLAIM;

        return new SectionOwner(leader.cells() > runnerUp ? leader.owner() : null, claims);
    }

    // Every claimant around a section with how many of its cells they hold, largest first.
    // The unowned cells are one claimant among the rest rather than a count kept aside, so the
    // comparison below is the same comparison for all of them.
    private static List<OwnerClaim> countClaims(
            VoidSection section,
            List<String> ownerBySite) {

        // Insertion-ordered, so two runs over the same section offer the sort the same list
        // and a tie between equal claims cannot come out either way.
        var cellsByOwner = new LinkedHashMap<String, Integer>();

        for (var cell : section.cells()) {
            cellsByOwner.merge(ownerBySite.get(cell), 1, Integer::sum);
        }

        var claims = new ArrayList<OwnerClaim>(cellsByOwner.size());

        for (var entry : cellsByOwner.entrySet()) {
            claims.add(new OwnerClaim(entry.getKey(), entry.getValue()));
        }

        // Ties settled by name so a section's runners-up read the same way run to run, with
        // the unowned cells last because they are the one claimant with no name to sort by.
        claims.sort(Comparator
            .comparingInt(OwnerClaim::cells).reversed()
            .thenComparing(
                OwnerClaim::owner,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return List.copyOf(claims);
    }
}
