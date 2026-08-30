package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.BlocFriendliness;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The four rules a box's blocks are placed by: who is in the running for the system at all, who
 * stands with the holder by alliance, who stands with it in disposition, and what a bloc is made of.
 *
 * <p>Bundled rather than threaded loose because all four have to come off the one pass. A routing
 * handed a friendliness read over one grouping and an affiliation off another would place blocs
 * under an alliance set no fill was resolved with, and one handed a membership from a third would
 * answer "who is in this bloc" about blocs the ranking never named. Passed as four arguments that is
 * a mistake nobody would notice at the call site; passed as one value it cannot be written.
 *
 * <p>Plain data with no Starsector types, so a rule taking these is still exercised on hand-built
 * inputs: what bars a bloc and what a bloc is made of arrive as functions over ids, and where either
 * comes from is the caller's business.
 *
 * @param candidacy      which blocs take part in the contest at all; the rest are set aside before a
 *                       holder is picked
 * @param affiliation    the alliance set the holder's own allies are lifted out by, which is
 *                       {@link BlocAffiliation#NONE} wherever nothing groups factions
 * @param friendliness   whether two blocs are on good terms, read over both memberships whole
 * @param blocMembership what a bloc is made of, keyed by bloc id. A reader rather than the grouping
 *                       itself, because a grouping sat beside a {@link BlocAffiliation} is the very
 *                       confusion that wrapper exists to make impossible
 */
public record StandingBlockRules(
    Predicate<String> candidacy,
    BlocAffiliation affiliation,
    BlocFriendliness friendliness,
    Function<String, Set<String>> blocMembership) {

    public StandingBlockRules {
        Objects.requireNonNull(candidacy, "candidacy");
        Objects.requireNonNull(affiliation, "affiliation");
        Objects.requireNonNull(friendliness, "friendliness");
        Objects.requireNonNull(blocMembership, "blocMembership");
    }

    /**
     * The factions one bloc is made of - its whole membership, and not who happens to stand in the
     * system being hovered.
     *
     * @param blocId the bloc to read; a bloc with no id is made of nobody
     * @return its member factions
     */
    public Set<String> readMemberFactionIds(String blocId) {
        return blocMembership.apply(blocId);
    }
}
