package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.BlocStatsRead;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.base.politics.holders.HolderProvider;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The three per-view decisions the shared political-map pipeline reads, gathered into
 * one rules object so each political-map view (faction, alliance, later mixed) supplies
 * its own grouping, cell styling, and labelling while the pipeline stays written once.
 *
 * <p>A view differs from every other only in these three things - how it collapses
 * factions into blocs, which blocs recede to the muted "independent" style, and what a
 * bloc's label reads - so lifting them behind this seam makes a new view an added rules
 * object rather than a fork of the render pipeline. The grouping is resolved once per
 * pass and handed back into the two per-bloc decisions, so the classifier and the name
 * resolver stay pure lookups over that one snapshot rather than re-reading a live set
 * (the alliances view samples Nexerelin) per cell.
 */
public interface PoliticalMapView {

    /**
     * This view's stable id - the string the active-view selection serialises into the save and
     * the view registry resolves a stored pick back to. Frozen once shipped, since renaming it
     * silently resets a save that selected this view to the default.
     *
     * @return the view's save-stable id
     */
    String getId();

    /**
     * The localisation key for this view's label on the view-selector radio - the segment the
     * player clicks to activate it. A key rather than the resolved string so the segment follows
     * the player's language and the resolution stays with the view radio that draws it.
     *
     * @return the {@code KmuStrings} key for this view's radio-segment label
     */
    String getSegmentLabelKey();

    /**
     * A revision fingerprint folding every live input this view samples, folded into the
     * drawables' content token so a change to any of them forces a rebuild even when no
     * setting moved. A view composes it from its own sources ({@link
     * kmlib.math.hashing.Fingerprints#compute}), so its number of live inputs can grow
     * without widening this contract: the faction view folds the live alliance-set revision alone,
     * since that is all its per-faction painting reads; the alliances view folds that same revision
     * plus its non-allied recede toggles, so either a membership change or a toggle flip repaints
     * it. A view sampling nothing live folds no sources and returns a constant, never triggering a
     * rebuild on its own. This is what lets the shared plugin invalidate on a view's live data
     * without naming any concrete view - each view declares its own fingerprint.
     *
     * @return this view's content fingerprint; a constant for a view with no live inputs
     */
    int getContentRevision();

    /**
     * The holder grouping this view resolves its pass under: identity for the
     * faction view (every faction its own bloc), alliance blocs for the alliances
     * view. Resolved once per rebuild and threaded through the pipeline, so a live set
     * is sampled a single time per pass and every stage keys off the same snapshot.
     *
     * @return the grouping that collapses factions into blocs for this pass
     */
    HolderGrouping resolveGrouping();

    /**
     * The source this view resolves its per-system holder from: for the faction and alliance
     * views (the default), each inhabited system's dominant holder, extended with each system a
     * bloc claims but does not hold, drawn as an unfilled part of that bloc's territory; a view
     * painting holders it derives some other way supplies its own source. Supplied per view so
     * the pipeline reads holders through one seam without naming a concrete resolver, exactly as
     * it reads {@link #resolveGrouping}.
     *
     * @return the provider that resolves this view's per-system holder
     */
    default HolderProvider resolveHolderProvider() {
        return ClaimAugmentedHolderProvider.INSTANCE;
    }

    /**
     * The mechanic this view's cells are counted by for their presence bands.
     *
     * <p>Answered by every view rather than defaulted here, for the reason
     * {@link DominancePaintedView} exists: a default would have to name one mechanic's planner,
     * and naming it on this seam would put it in front of every view including the ones that
     * mechanic does not paint. The views the contest paints answer it once between them, and a
     * view painted by another mechanic answers with its own.
     *
     * <p>Per view at all for the same reason the hover box is: a band explains the fill it sits
     * inside, so counting it by a mechanic other than the one the cell was painted by would lead
     * the band on a bloc the cell is not painted for, or count a set of colonies the fill's own
     * score never saw.
     *
     * @param inputs everything one bake's bands are settled from, sampled once by the bake - the
     *               grouping among it, so a band folds factions into blocs exactly as the fill did
     * @return the planner this view's bands are counted through
     */
    SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs);

    /**
     * Whether a bloc paints in the muted independent cell style rather than the full
     * faction style. The faction view styles only independent space this way; the
     * alliances view styles every non-alliance bloc this way, so the unaligned recede
     * while alliances stand out in full colour.
     *
     * <p>The bloc's already-resolved {@code adjustment} is supplied so a view that keys the
     * style bundle off desaturation reads the one adjustment the pass actually paints under -
     * every reason to recede already folded in - rather than re-deriving it from a source of
     * its own. A view answering from a narrower source than the palette resolves from would
     * paint a bloc in the desaturation palette while leaving it in the faction bundle.
     *
     * @param blocId     the winning bloc for a system, as resolved under {@code grouping}
     * @param grouping   the grouping this pass resolved, supplied so the test is a pure
     *                   lookup over the once-sampled snapshot rather than a fresh read
     * @param adjustment the dimming and recolouring this bloc draws under, resolved ahead of
     *                   this test so both read one decision
     * @return true when the bloc takes the independent style
     */
    boolean shouldUseIndependentStyle(
        String blocId,
        HolderGrouping grouping,
        ElementStyleAdjustment adjustment);

    /**
     * How a bloc's fills, borders, and name are dimmed or recoloured before the pipeline
     * paints it, applied uniformly wherever the style classification is read. The faction
     * view never adjusts a bloc ({@link ElementStyleAdjustment#NONE}); the alliances view dims
     * and/or desaturates every non-alliance bloc when the player has asked it to, leaving
     * alliances untouched. Resolving it here lets the pipeline apply the two knobs without
     * knowing why a view wanted them, mirroring the {@link #shouldUseIndependentStyle} seam.
     *
     * @param blocId   the winning bloc for a system, as resolved under {@code grouping}
     * @param grouping the grouping this pass resolved, so the decision is a pure lookup over
     *                 the once-sampled snapshot rather than a fresh read
     * @return the per-bloc styling adjustment; {@link ElementStyleAdjustment#NONE} to draw the
     *         bloc exactly as classified
     */
    ElementStyleAdjustment resolveBlocStyleAdjustment(String blocId, HolderGrouping grouping);

    /**
     * The label a bloc reads under this view: a faction's display name for a faction
     * bloc, an alliance's name for an alliance bloc. Null when no name resolves, which
     * the label fit treats as an unresolved name and sizes a stand-in band for instead.
     *
     * @param blocId     the winning bloc to name, as resolved under {@code grouping}
     * @param grouping   the grouping this pass resolved
     * @param sector     the sector, from which a faction bloc's display name is read
     * @param nameFormat whether a faction name reads in its short or full form; one of the
     *                   drawn forms, since the whole label build is skipped when the player's
     *                   choice draws no name at all
     * @return the bloc's display name, or null when none resolves
     */
    String resolveName(
        String blocId,
        HolderGrouping grouping,
        SectorAPI sector,
        FactionNameFormatChoice nameFormat);

    /**
     * The spotlight picker this view offers: the blocs it lists - factions with a visible weighted
     * market under the factions view, current alliances under the alliances view - together with the
     * sort vocabulary that ranks them. Each bloc carries the id the filter stores, its picker label,
     * and (for a faction) its crest. The list is what the picker draws and what
     * {@link kmu.maplayers.base.sidebar.FilterSelection} heals a stale saved selection against, so a
     * bloc that is no longer here is no longer spotlightable.
     *
     * <p>Answered as a {@link BlocPickerRead}, so the same walk hands back where each bloc was found
     * beside the rows it was found for. A view resolves that index from the aggregation it already
     * runs for the numbers, which is what lets a surface light a bloc's systems without a second read
     * of the sector - and without the two readings being able to disagree.
     *
     * <p>The list and the vocabulary are answered together because a view owns its picker end to
     * end: which blocs it offers, what numbers those blocs carry, and which metrics rank them are
     * one decision, and a view painted by one mechanic must never be handed a vocabulary reading
     * numbers its blocs do not carry. That is why the return type is wildcarded - the metrics a
     * view's blocs carry are its own, so the layer above passes the picker on without naming them.
     *
     * <p>There is no new per-bloc seam behind the list: a view says which of its blocs are targets
     * (every faction, or only the alliance blocs) through {@link #resolveSelectableBlocGate}, and
     * {@link #buildBlocPickerRead} assembles the read the same way for every view. The convenience
     * overload reads the player's live visibility settings so a caller with no pass of its own need
     * not thread them.
     *
     * <p>What it does <em>not</em> take is the rule any one mechanic weighs by. Who a picker lists
     * is settled by the sector's colonies, and how a listed bloc's numbers are arrived at is the
     * painting layer's own business - so a seam naming a weighting rule would hand every view a
     * knob only some of them spend, and read the settings behind it for the ones that do not. A
     * layer that weighs takes its rule beside this, the way
     * {@link kmu.maplayers.politicalmap.base.politics.holders.HolderProvider} leaves the same rule
     * off the holder seam.
     *
     * <p>The spotlight is optional: the default offers an empty read, so a view with no list to
     * spotlight inherits one rather than overriding with two arguments it would ignore. A view
     * opts into the spotlight by overriding this, the same way it opts into its own body controls.
     *
     * @param sector           the sector whose colonies decide who is listed; null yields an empty
     *                         read
     * @param colonyVisibility what the player may be shown of a colony, so a bloc is offered on
     *                         the strength of the very colonies the map paints it for
     * @return this view's picker - its blocs in the order the source walk surfaces them, and the
     *         vocabulary ranking them - beside where that walk found each bloc; empty when no bloc
     *         qualifies, and empty by default for a view with no spotlight
     */
    default BlocPickerRead<?> resolveBlocPickerRead(
            SectorAPI sector,
            ColonyVisibility colonyVisibility) {
        return BlocPickerRead.empty();
    }

    /**
     * This view's picker under the player's current visibility settings - the live entry the
     * sidebar and the stale-selection heal call, so neither has to read the toggles a running pass
     * would already hold.
     *
     * @param sector the sector whose colonies decide who is listed; null yields an empty read
     * @return this view's picker and presence under the player's live settings; empty when no bloc
     *         qualifies
     */
    default BlocPickerRead<?> resolveBlocPickerRead(SectorAPI sector) {
        return resolveBlocPickerRead(
            sector,
            MapVisibilityRules.readFromLunaSettings().colonyVisibility());
    }

    /**
     * Turns one bloc walk's read into the picker read a view offers, so an overriding view declares
     * only what distinguishes it - which of the present blocs are targets, and which vocabulary
     * ranks them - rather than repeating the crest, name, and option assembly every view resolves
     * identically.
     *
     * <p>It takes the walk's read whole rather than its totals and its presence apart. The two are
     * one reading of the sector ({@link BlocStatsRead}), so passing them separately would let a view
     * hand over a map from one walk and an index from another, which is precisely the disagreement
     * the paired read exists to make impossible.
     *
     * <p>The crest comes from the bloc's colour faction, which is an alliance's lead member and, for
     * a faction bloc, the faction itself, so one lookup serves a grouped and an ungrouped view alike.
     * A bloc with no authored crest keeps its option and simply draws its name alone. The label is
     * this view's own {@link #resolveName}, always in the short form: the picker labels a bloc by its
     * short name regardless of the map's name-format setting, so a long-form map label never widens
     * the sidebar's option rows.
     *
     * <p>It is parameterised on the metrics rather than fixed to {@link DominanceStats} because a
     * view ranks by whatever its own layer is painted from: the identity half of an option is
     * assembled the same way for every view, while the payload half is the calling view's alone. That
     * also keeps this a default method rather than a static - the label is <em>this</em> view's
     * {@link #resolveName}, so no view has to reach into a sibling for a name.
     *
     * @param <S>       the calling view's own metrics type, ranked by that view's vocabulary;
     *                  bounded only by what every option must answer of its metrics, never by one
     *                  layer's numbers
     * @param sector    the sector a bloc's colour faction is read from
     * @param grouping  the grouping the walk folded under, so the colour faction, the name, and the
     *                  gate all resolve against the same snapshot the numbers came from
     * @param statsRead the walk's totals and the systems behind them, in the order it surfaced them,
     *                  which the returned rows preserve
     * @param sortModes the vocabulary ranking this view's rows, bundled with them so a row can only
     *                  ever be sorted by numbers it carries
     * @return the rows this view offers paired with that vocabulary, beside the whole walk's presence
     */
    default <S extends BlocMetrics> BlocPickerRead<RankedBloc<S>> buildBlocPickerRead(
            SectorAPI sector,
            HolderGrouping grouping,
            BlocStatsRead<S> statsRead,
            ListSortModes<RankedBloc<S>> sortModes) {

        return new BlocPickerRead<>(
            new ListPicker<>(
                buildSelectableBlocs(sector, grouping, statsRead.statsByBlocId()),
                sortModes),
            statsRead.presenceIndex());
    }

    /**
     * Which of the present blocs this view offers as spotlight targets. A bloc reaches the test only
     * when the walk already found it, so this decides what is offered among those, never who is
     * present.
     *
     * <p>Defaults to offering every one of them, which is the answer for a view whose blocs are all
     * of a kind. A view whose walk surfaces blocs it does not paint - the alliances view, where a
     * lone faction is present but is not an alliance - narrows it here.
     *
     * @param grouping the grouping the blocs were folded under, so a gate that asks what a bloc is
     *                 (an alliance, a lone faction) reads the same snapshot the numbers came from
     * @return the test a present bloc's id passes to be listed
     */
    default Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        return blocId -> true;
    }

    /**
     * The body controls this view contributes to the political-map tab, appended beneath the shared
     * sub-options and the view selector while this view is the selected one. A view that adds no
     * controls of its own returns an empty list (the default), so the body carries only the shared
     * rows; the alliances view returns its Mute/Desaturate checkboxes so those show solely under it.
     * The sidebar body grows downward to fit whatever rows a view adds, so a view opts into its own
     * controls without any layout knowing which view asked.
     *
     * @return this view's own body controls, top to bottom; empty when the view adds none
     */
    default List<ControlSpec> getViewBodyControls() {
        return List.of();
    }

    /**
     * The hover tooltip this view shows for the star system under the cursor, or empty when it shows
     * none. The layer renderer answers the framework's tooltip seam with whatever the active view
     * supplies here, and the shared dispatcher draws it - nothing when it supplies nothing - so a view
     * opts into a tooltip by injecting one rather than flipping a flag:
     * the faction and alliance views inject the domination breakdown, while the claims view - whose
     * holders that breakdown does not describe - injects the claim breakdown instead, so each view's
     * box explains the same mechanic its fills were painted by.
     * Defaulting to empty makes "no tooltip" the base case, the same shape as
     * {@link #resolveBlocPickerRead} defaulting to no spotlight, so a new view opts in only when it
     * has a tooltip to show.
     *
     * @return this view's hover tooltip, or empty for a view that shows none
     */
    default Optional<MapHoverTooltip> resolveHoverTooltip() {
        return Optional.empty();
    }

    // The row half of the assembly: each gated bloc paired with its metrics, in walk order. Private
    // because the pairing is only ever half an answer - a list of rows with no vocabulary cannot be
    // ranked and no presence beside it cannot be lit - so the whole read is the only thing worth
    // offering a view.
    private <S extends BlocMetrics> List<RankedBloc<S>> buildSelectableBlocs(
            SectorAPI sector,
            HolderGrouping grouping,
            Map<String, S> statsByBlocId) {

        var isSelectable = resolveSelectableBlocGate(grouping);
        var selectableBlocs = new ArrayList<RankedBloc<S>>();

        for (var entry : statsByBlocId.entrySet()) {
            var blocId = entry.getKey();
            if (!isSelectable.test(blocId)) {
                continue;
            }
            var colourFaction = sector.getFaction(grouping.resolveColourFactionId(blocId));
            var identity = new SelectableBloc(
                blocId,
                resolveName(blocId, grouping, sector, FactionNameFormatChoice.SHORT),
                FactionCrests.resolveCrestPath(colourFaction));

            selectableBlocs.add(new RankedBloc<>(identity, entry.getValue()));
        }
        return selectableBlocs;
    }
}
