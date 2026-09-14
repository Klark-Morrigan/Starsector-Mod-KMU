package kmu.maplayers.politicalmap.base.dominance;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The two rules a system's ranking is settled by once its weights are in: who may win it at all,
 * and who wins a dead heat.
 *
 * <p>Bundled rather than threaded loose because they always travel together and always come off one
 * pass. Both answer "how is this system's holder settled" rather than "what is being ranked", and a
 * reader handed one from the pass that painted the map and the other from anywhere else would rank a
 * system under a rule no fill was resolved with. Passed apart that is a mistake nobody would notice;
 * passed as one value it cannot be written.
 *
 * <p>Plain data with no Starsector types, so a rule taking these is still exercised on hand-built
 * inputs: what bars a bloc arrives as a predicate and what settles a tie as a comparator, and where
 * either comes from is the caller's business.
 *
 * @param tieBreak  consulted only where two blocs tie on every weight level; the ID it orders first
 *                  wins. It must impose a total order over the IDs, so a winner never depends on map
 *                  iteration order
 * @param candidacy which IDs may win the system at all; the rest are ranked only where it admits
 *                  nobody
 */
public record HolderRankingRules(
    Comparator<String> tieBreak,
    Predicate<String> candidacy) {

    // Nobody barred. Private, because a caller wanting an open contest says so through the factory
    // below rather than by handing in a bar that bars nothing.
    private static final Predicate<String> EVERY_BLOC = blocId -> true;

    public HolderRankingRules {
        Objects.requireNonNull(tieBreak, "tieBreak");
        Objects.requireNonNull(candidacy, "candidacy");
    }

    /**
     * Rules breaking a dead heat by lowest ID, under the given bar - what a reader with no geometry
     * to settle a tie by takes, map iteration order never deciding a winner.
     *
     * @param candidacy which IDs may win the system
     * @return those rules
     */
    public static HolderRankingRules createByLowestId(Predicate<String> candidacy) {
        return new HolderRankingRules(Comparator.naturalOrder(), candidacy);
    }

    /**
     * Rules breaking a dead heat by lowest ID with nobody barred - the ranking of a contest that is
     * about weights rather than about who is allowed to win one.
     *
     * @return those rules
     */
    public static HolderRankingRules createOpenContestByLowestId() {
        return createByLowestId(EVERY_BLOC);
    }

    /**
     * The same tie-break with the bar lifted, for the second look a ranking takes where its bar
     * admitted nobody: a bloc kept out of the contest still holds what nobody contests.
     *
     * @return these rules with every bloc a candidate
     */
    public HolderRankingRules reopenToEveryBloc() {
        return new HolderRankingRules(tieBreak, EVERY_BLOC);
    }
}
