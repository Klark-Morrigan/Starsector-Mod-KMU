package kmu.maplayers.politicalmap.base.dominance;

import kmlib.text.KmlibStrings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * The rule that collapses faction ids into "bloc" ids for one holder pass, so
 * the dominance pipeline resolves a bloc as a single unit while the geometry,
 * clustering, and labels all key off one id per system unchanged.
 *
 * <p>A bloc is whatever a political-map view groups holders by: in the faction
 * view a bloc is a single faction (the identity grouping); in the alliances view a
 * bloc is an alliance for its member factions and a lone faction for everyone else.
 * Because {@link SystemDominance} already ranks an opaque {@code Map<key, footprint>},
 * feeding it bloc-keyed footprints makes merging, dominance, and territory grouping
 * alliance-aware with no change to their own logic - the seam is this one type.
 *
 * <p>Plain data with no Starsector or Nexerelin types: an adapter builds the three
 * maps from the live alliance set, and this class only ever answers lookups over
 * them, so the grouping can be exercised on hand-built inputs. Each map is sparse -
 * an entry is present only when a faction is grouped away from itself - so the
 * identity grouping is three empty maps and every lookup falls through to the
 * faction being its own bloc.
 *
 * @param blocIdByFactionId       an allied faction id to its alliance's bloc id; a
 *                                faction absent from the map is its own bloc
 * @param colourFactionIdByBlocId an alliance bloc id to the member faction whose
 *                                palette the bloc paints in; a faction bloc is absent,
 *                                its colour faction being itself
 * @param allianceNameByBlocId    an alliance bloc id to the alliance's display name; a
 *                                faction bloc is absent, so a present entry is also the
 *                                test of whether a bloc is an alliance
 */
public record HolderGrouping(
    Map<String, String> blocIdByFactionId,
    Map<String, String> colourFactionIdByBlocId,
    Map<String, String> allianceNameByBlocId) {

    // The faction-mode grouping: every map empty, so each faction is its own bloc,
    // no bloc is an alliance, and each bloc's colour faction is itself. Shared as a
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
        allianceNameByBlocId = Map.copyOf(allianceNameByBlocId);
    }

    /**
     * The identity grouping, in which every faction is its own bloc so the pipeline
     * resolves the faction view. The default for every entry point that takes no
     * explicit grouping.
     *
     * @return the shared faction-mode grouping
     */
    public static HolderGrouping identity() {
        return IDENTITY;
    }

    /**
     * The bloc a faction belongs to: its alliance's bloc id when allied, otherwise
     * the faction itself. The one lookup the regroup step folds each faction's
     * footprint through, so allied factions share a key and everyone else stays
     * separate.
     *
     * @param factionId the faction to place; a faction with no id belongs to no bloc, since a bloc
     *                  is named and there is nothing here to name one after
     * @return the faction's bloc id, the faction id itself when it is in no bloc, or null when the
     *         faction carries no id
     */
    public String resolveBlocId(String factionId) {
        return findGroupedId(blocIdByFactionId, factionId, factionId);
    }

    /**
     * Folds a per-faction map into a per-bloc map under this grouping: each faction's value merges
     * into its bloc's through {@code merge}, so an alliance's members combine into one entry while
     * an outsider stays its own bloc. Under the identity grouping every faction is its own bloc and
     * each value merges into the identity alone, coming out unchanged - the no-op the faction view
     * relies on. The map-level counterpart of {@link #resolveBlocId}.
     *
     * <p>Generic over the folded value so the dominance-only footprint regroup and the picker's
     * fuller footprint-plus-market-size regroup share one fold rather than two copies of the idiom.
     * First-seen bloc order is preserved, so the economy-walk ordering flows straight through.
     *
     * @param valueByFactionId each faction's value to fold, in walk order; a faction with no id
     *                         belongs to no bloc, so its value is left out rather than folded under
     *                         a bloc the map cannot name
     * @param identity         the merge identity a bloc's first value combines with
     * @param merge            combines two same-bloc values into one
     * @param <T>              the folded value type
     * @return each bloc's merged value, keyed by bloc id in first-seen order
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
     * The faction whose authored palette a bloc paints in: an alliance's dominant
     * member, or - for a faction bloc - the faction itself. Lets the render layer
     * colour an alliance cluster in a real faction's shades without the bloc id
     * needing to be a faction id.
     *
     * @param blocId the bloc to colour; a bloc with no id has no palette to name
     * @return the faction id supplying the bloc's palette, or null for a bloc with no id
     */
    public String resolveColourFactionId(String blocId) {
        return findGroupedId(colourFactionIdByBlocId, blocId, blocId);
    }

    /**
     * The display name of an alliance bloc, or null when the bloc is a lone faction
     * rather than an alliance. Null both supplies the alliance label and, via
     * {@link #isAlliance}, tells a lone-faction bloc from an alliance one.
     *
     * @param blocId the bloc to name; a bloc with no id is no alliance, there being nothing to have
     *               named it one
     * @return the alliance's name, or null when the bloc is not an alliance
     */
    public String resolveAllianceName(String blocId) {
        return findGroupedId(allianceNameByBlocId, blocId, null);
    }

    /**
     * Whether a bloc is an alliance rather than a lone faction, which the render
     * rules read to decide alliance styling versus the plain faction style.
     *
     * @param blocId the bloc to test
     * @return true when the bloc is an alliance
     */
    public boolean isAlliance(String blocId) {
        return resolveAllianceName(blocId) != null;
    }

    /**
     * Whether this grouping holds any alliance at all, which distinguishes "grouped, and these blocs
     * are the outsiders" from "nothing is grouped, so every bloc is a lone faction". A render rule
     * that sets alliances against a backdrop needs the difference: with no alliance there is no
     * figure, so receding every bloc would sink the whole sector rather than isolate anything.
     *
     * @return true when at least one bloc is an alliance
     */
    public boolean hasAnyAlliance() {
        return !allianceNameByBlocId.isEmpty();
    }

    // One of this grouping's lookups, answered for an id the sector never named rather than faulting
    // on it.
    //
    // The guard is what the immutable maps oblige: an immutable map asked for a null key throws
    // instead of reporting the key absent, even when the map is empty - which the identity
    // grouping's three are. So a colony whose owning faction carries no id (a mod's, the game
    // itself always naming its factions) would take down whatever walk reached it: the ribbon
    // count, the dominance regroup, or a render rule asking whether its bloc is an alliance.
    //
    // Answered as "no bloc" rather than as some stand-in id, because a nameless bloc pooled under
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
