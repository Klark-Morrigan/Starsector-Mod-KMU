package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

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
 * <p>Plain data with no Starsector types, so a rule taking these stays free of the live economy:
 * what bars a bloc arrives as a predicate and what settles a tie as a comparator. The live rules
 * are built by {@link #createByMarketProximity}, the one place they are put together, so the walk
 * that paints the fills and the poll that watches them cannot settle a tie two different ways.
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

    // Nobody barred: the second look a ranking takes where its bar admitted nobody.
    private static final Predicate<String> EVERY_BLOC = blocId -> true;

    public HolderRankingRules {
        Objects.requireNonNull(tieBreak, "tieBreak");
        Objects.requireNonNull(candidacy, "candidacy");
    }

    /**
     * The rules a live system's holder is settled by: every bloc but the one painting in the
     * neutral placeholder may win, and a dead heat goes to the bloc whose nearest market orbits
     * closest to the system's centre.
     *
     * <p>The tie-break reads no geometry unless a tie forces it, so a system with a clear winner -
     * nearly every system - pays nothing for carrying one.
     *
     * @param system          the system whose ranking these rules settle
     * @param colonies        the system's colonies as the caller's one walk of it reported, so the
     *                        tie is settled among the colonies the weights were folded from
     * @param colonyKnowledge what the player may be shown of a colony, as the weights were folded
     *                        under
     * @param grouping        the active grouping, naming each bloc's colour faction
     * @return the rules a ranking of this system's footprints is taken under
     */
    public static HolderRankingRules createByMarketProximity(
            StarSystemAPI system,
            Colonies colonies,
            ColonyKnowledge colonyKnowledge,
            HolderGrouping grouping) {

        return new HolderRankingRules(
            MarketProximityTieBreak.createForSystem(system, colonies, colonyKnowledge, grouping),
            BlocCandidacy.createForGrouping(grouping));
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
