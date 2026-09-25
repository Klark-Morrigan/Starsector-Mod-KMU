package kmu.maplayers.ownermap.holding;

import kmlib.text.KmlibStrings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

/**
 * The rule that collapses faction IDs into "bloc" IDs for one holder pass, so
 * the holder pipeline resolves a bloc as a single unit while the geometry,
 * clustering, and labels all key off one ID per system unchanged.
 *
 * <p>A bloc is whatever an owner-painted view groups holders by: a single faction under the
 * identity grouping, or a named group for its member factions and a lone faction for everyone
 * else. Because a mechanic's own ranking works over an opaque {@code Map<key, footprint>},
 * feeding it bloc-keyed footprints makes merging, ranking, and clustering group-aware with no
 * change to their own logic - the seam is this one type.
 *
 * <p>Plain data with no game types: whoever supplies the groups builds the three maps, and this
 * class only ever answers lookups over them. Each map is sparse - an entry is present only when a
 * faction is grouped away from itself - so the identity grouping is three empty maps and every
 * lookup falls through to the faction being its own bloc.
 *
 * @param blocIdByFactionId       a grouped faction ID to its group's bloc ID; a faction absent
 *                                from the map is its own bloc
 * @param colourFactionIdByBlocId a group's bloc ID to the member faction whose palette the bloc
 *                                paints in; a faction bloc is absent, its colour faction being
 *                                itself
 * @param groupNameByBlocId       a group's bloc ID to the group's display name; a faction bloc is
 *                                absent, so a present entry is also the test of whether a bloc is
 *                                a group
 */
public record HolderGrouping(
    Map<String, String> blocIdByFactionId,
    Map<String, String> colourFactionIdByBlocId,
    Map<String, String> groupNameByBlocId) {

    // The faction-mode grouping: every map empty, so each faction is its own bloc,
    // no bloc is a group, and each bloc's colour faction is itself. Shared as a
    // singleton because it is immutable and the default every no-grouping pass uses.
    private static final HolderGrouping IDENTITY =
        new HolderGrouping(Map.of(), Map.of(), Map.of());

    /**
     * Defensively copies each map into an immutable snapshot, so a grouping handed
     * around the pipeline cannot be mutated after it is built and its lookups stay
     * stable for the whole pass.
     */
    public HolderGrouping {

        blocIdByFactionId = Map.copyOf(blocIdByFactionId);
        colourFactionIdByBlocId = Map.copyOf(colourFactionIdByBlocId);
        groupNameByBlocId = Map.copyOf(groupNameByBlocId);
    }

    /**
     * The identity grouping, in which every faction is its own bloc. The default for every entry
     * point that takes no explicit grouping.
     *
     * @return the shared faction-mode grouping
     */
    public static HolderGrouping identity() {
        return IDENTITY;
    }

    /**
     * The bloc a faction belongs to: its group's bloc ID when grouped, otherwise
     * the faction itself. The one lookup the regroup step folds each faction's
     * footprint through, so grouped factions share a key and everyone else stays
     * separate.
     *
     * @param factionId the faction to place; a faction with no ID belongs to no bloc, since a bloc
     *                  is named and there is nothing here to name one after
     * @return the faction's bloc ID, the faction ID itself when it is in no bloc, or null when the
     *         faction carries no ID
     */
    public String resolveBlocId(String factionId) {
        return findGroupedId(blocIdByFactionId, factionId, factionId);
    }

    /**
     * Folds a per-faction map into a per-bloc map under this grouping: each faction's value merges
     * into its bloc's through {@code merge}, so a group's members combine into one entry while
     * an outsider stays its own bloc. Under the identity grouping every faction is its own bloc and
     * each value merges into the identity alone, coming out unchanged. The map-level counterpart of
     * {@link #resolveBlocId}.
     *
     * <p>Generic over the folded value so a weighted footprint regroup and the picker's
     * fuller footprint-plus-market-size regroup share one fold rather than two copies of the idiom.
     * First-seen bloc order is preserved, so the economy-walk ordering flows straight through.
     *
     * @param valueByFactionId each faction's value to fold, in walk order; a faction with no ID
     *                         belongs to no bloc, so its value is left out rather than folded under
     *                         a bloc the map cannot name
     * @param identity         the merge identity a bloc's first value combines with
     * @param merge            combines two same-bloc values into one
     * @param <T>              the folded value type
     * @return each bloc's merged value, keyed by bloc ID in first-seen order
     */
    public <T> Map<String, T> regroupByBloc(
            Map<String, T> valueByFactionId,
            T identity,
            BinaryOperator<T> merge) {

        var valueByBlocId = new LinkedHashMap<String, T>();

        for (var entry : valueByFactionId.entrySet()) {

            var blocId = resolveBlocId(entry.getKey());

            // A faction this grouping can name no bloc for is left out, which is the one thing the
            // fold decides that the lookup cannot: a nameless key here would travel on as a bloc,
            // and every reader downstream - the ranking, the palette, the picker row - would then
            // be asked about a bloc the map has no name to show for.
            if (blocId != null) {
                valueByBlocId.put(
                    blocId,
                    merge.apply(
                        valueByBlocId.getOrDefault(blocId, identity),
                        entry.getValue()));
            }
        }
        return valueByBlocId;
    }

    /**
     * The blocs a set of factions folds into under this grouping, each named once, so two grouped
     * members arrive as the one bloc they paint as. The id-level counterpart of
     * {@link #regroupByBloc}, for a caller holding the factions themselves rather than values keyed
     * by them.
     *
     * <p>Here rather than at each caller so the one rule the fold decides is decided once: a
     * faction this grouping can name no bloc for is left out, on the same reasoning
     * {@link #regroupByBloc} applies it. First-seen order is preserved, so a walk's own ordering
     * flows through.
     *
     * @param factionIds the factions to fold, in the order they were read
     * @return their blocs, in first-seen order
     */
    public Set<String> collectBlocIds(Collection<String> factionIds) {

        var blocIds = new LinkedHashSet<String>();

        for (var factionId : factionIds) {

            var blocId = resolveBlocId(factionId);

            if (blocId != null) {
                blocIds.add(blocId);
            }
        }
        return blocIds;
    }

    /**
     * The factions a bloc is made of: every member folded into a group, or the bloc itself
     * where it is a lone faction. The reverse of {@link #resolveBlocId}, off the same fold, so a
     * rule stated over a bloc's whole membership needs no second source of who is in what - a
     * second one would be free to disagree with the fold every fill, run and row was built from.
     *
     * <p>Membership is the bloc's own and not what is present anywhere: a member holding no colony
     * in the system a caller is asking about is still a member here. A rule wanting only who stands
     * in one place narrows this itself, which keeps "who is in this bloc" and "who is present" two
     * questions rather than one answer serving badly as both.
     *
     * @param blocId the bloc to read; a bloc with no ID is made of nobody, there being nothing to
     *               have named it
     * @return the bloc's member factions, immutable and in no meaningful order; empty for a bloc
     *         with no ID
     */
    public Set<String> resolveMemberFactionIds(String blocId) {

        if (!KmlibStrings.hasText(blocId)) {
            return Set.of();
        }
        var memberFactionIds = new LinkedHashSet<String>();

        for (var entry : blocIdByFactionId.entrySet()) {

            if (blocId.equals(entry.getValue())) {
                memberFactionIds.add(entry.getKey());
            }
        }

        // Nothing folds into a bloc that is a lone faction - the sparse map holds an entry only
        // where a faction is grouped away from itself - and there the bloc ID is that faction's own
        // ID, which is the membership of one every identity-grouping read comes back with.
        //
        // Copied rather than handed out as it stands, so both answers are immutable: a caller that
        // may add to a membership scanned out of the fold and not to one of a single faction would
        // be free to do so or to fault on it depending on which bloc it happened to ask about.
        return memberFactionIds.isEmpty()
            ? Set.of(blocId)
            : Set.copyOf(memberFactionIds);
    }

    /**
     * The faction whose authored palette a bloc paints in: a group's leading
     * member, or - for a faction bloc - the faction itself. Lets the render layer
     * colour a group's cluster in a real faction's shades without the bloc ID
     * needing to be a faction id.
     *
     * @param blocId the bloc to colour; a bloc with no ID has no palette to name
     * @return the faction ID supplying the bloc's palette, or null for a bloc with no ID
     */
    public String resolveColourFactionId(String blocId) {
        return findGroupedId(colourFactionIdByBlocId, blocId, blocId);
    }

    /**
     * The display name of a group's bloc, or null when the bloc is a lone faction rather than a
     * group. Null both supplies the group's label and, via {@link #isGroupedBloc}, tells a
     * lone-faction bloc from a group.
     *
     * @param blocId the bloc to name; a bloc with no ID is no group, there being nothing to have
     *               named it one
     * @return the group's name, or null when the bloc is not a group
     */
    public String resolveGroupName(String blocId) {
        return findGroupedId(groupNameByBlocId, blocId, null);
    }

    /**
     * Whether a bloc is a group rather than a lone faction, which a view's style rules read to
     * set groups apart from lone factions.
     *
     * @param blocId the bloc to test
     * @return true when the bloc is a group
     */
    public boolean isGroupedBloc(String blocId) {
        return resolveGroupName(blocId) != null;
    }

    /**
     * Whether this grouping holds any group at all, which distinguishes "grouped, and these blocs
     * are the outsiders" from "nothing is grouped, so every bloc is a lone faction". A render rule
     * that sets groups against a backdrop needs the difference: with no group there is no figure,
     * so receding every bloc would sink the whole sector rather than isolate anything.
     *
     * @return true when at least one bloc is a group
     */
    public boolean hasAnyGroupedBloc() {
        return !groupNameByBlocId.isEmpty();
    }

    // One of this grouping's lookups, answered for an ID the sector never named rather than faulting
    // on it.
    //
    // The guard is what the immutable maps oblige: an immutable map asked for a null key throws
    // instead of reporting the key absent, even when the map is empty - which the identity
    // grouping's three are. So a colony whose owning faction carries no ID (a mod's, the game
    // itself always naming its factions) would take down whatever walk reached it: the ribbon
    // count, a footprint regroup, or a render rule asking whether its bloc is a group.
    //
    // Answered as "no bloc" rather than as some stand-in ID, because a nameless bloc pooled under
    // one is worse than one left out: two such owners would merge into a single run, a single
    // fill, and a single row naming neither of them.
    private static String findGroupedId(
            Map<String, String> groupedIdById,
            String id,
            String fallbackId) {

        if (!KmlibStrings.hasText(id)) {
            return null;
        }
        return groupedIdById.getOrDefault(id, fallbackId);
    }
}
