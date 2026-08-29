package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.function.Predicate;

/**
 * Which blocs may win a system: everyone but the sector's placeholder owner.
 *
 * <p>Vanilla hands every abandoned station, every derelict and every collapsed colony to the
 * {@code neutral} faction, which is where nobody is rather than a faction with interests. It takes
 * a footprint like anyone else and would otherwise enter the ranking that decides who holds a
 * system, where an unopposed nought wins the system outright. Barring the candidate rather than
 * zeroing the weight is what leaves every number the weighting produced honest: the score is
 * computed and shown as it always was, and all it loses is the power to buy a system.
 *
 * <p>The same answer is wanted at two shapes - a ranking asks it of a bloc id, a per-faction read
 * asks it of a faction id - and a rule deciding who holds a system must not be stated twice, so
 * both come from here. The bloc form reads through the grouping's colour faction, so an alliance is
 * judged by the faction it stands as rather than by a bloc id that is not a faction id at all.
 */
public final class BlocCandidacy {

    /**
     * The candidacy barring nobody, for a ranking that is about weights rather than about who is
     * allowed to win one. Named rather than written out at each site so a ranking that opens the
     * contest to everyone says so.
     */
    public static final Predicate<String> NONE_BARRED = blocId -> true;

    private BlocCandidacy() {
    }

    /**
     * Whether a faction may hold a system - true for every faction but the neutral placeholder.
     *
     * @param factionId the faction's id; a null or unknown id is a candidate, there being nothing
     *                  about it that says otherwise
     * @return true where the faction may win a system
     */
    public static boolean isCandidateFaction(String factionId) {
        return !Factions.NEUTRAL.equals(factionId);
    }

    /**
     * The candidacy over bloc ids under one grouping: a bloc may hold a system unless the faction
     * it paints in is the neutral one.
     *
     * @param grouping the grouping naming each bloc's colour faction; under identity a bloc id is
     *                 its own faction id, so the two forms agree
     * @return the test a ranking over that grouping's bloc ids is taken under
     */
    public static Predicate<String> createForGrouping(HolderGrouping grouping) {
        return blocId -> isCandidateFaction(grouping.resolveColourFactionId(blocId));
    }
}
