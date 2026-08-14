package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
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
     * without widening this contract: the faction view samples nothing live and returns a constant,
     * never triggering a rebuild on its own; the alliances view folds the live alliance-set
     * revision and its non-allied recede toggles, so either a membership change or a toggle
     * flip repaints it. This is what lets the shared plugin invalidate on a view's live data
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
     * @param sector   the sector the counts are read from; the planner samples it once for the
     *                 whole pass
     * @param grouping the grouping this pass resolved, so a band folds factions into blocs exactly
     *                 as the fill did
     * @param inputs   where a bloc's shades are read from and how far its runs go, sampled once by
     *                 the pass so every cell's band is planned at one set of proportions
     * @return the planner this view's bands are counted through
     */
    SystemRibbonPlanner resolveRibbonPlanner(
        SectorAPI sector,
        HolderGrouping grouping,
        RibbonPlanInputs inputs);

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
     * <p>The list and the vocabulary are answered together because a view owns its picker end to
     * end: which blocs it offers, what numbers those blocs carry, and which metrics rank them are
     * one decision, and a view painted by one mechanic must never be handed a vocabulary reading
     * numbers its blocs do not carry. That is why the return type is wildcarded - the metrics a
     * view's blocs carry are its own, so the layer above passes the picker on without naming them.
     *
     * <p>There is no new per-bloc seam behind the list: a view decides which of its blocs are
     * targets (every faction, or only the alliance blocs) by handing that one test to
     * {@link #buildSelectableBlocs}, which assembles the options the same way for every view. The
     * convenience overload reads the player's live dominance and dev-reveal toggles so a caller with
     * no pass of its own need not thread them.
     *
     * <p>The spotlight is optional: the default offers an empty picker, so a view with no list to
     * spotlight inherits one rather than overriding with three arguments it would ignore. A view
     * opts into the spotlight by overriding this, the same way it opts into its own body controls.
     *
     * @param sector                           the sector whose economy the visibility gate reads; null
     *                                         yields an empty picker
     * @param rules                            the dominance-weighting rules for this read, so selectable
     *                                         blocs are gated under the same rule the map paints under
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward a bloc's
     *                                         visibility (the "show all factions" dev reveal); false
     *                                         applies the normal known-to-player filter
     * @return this view's picker - its blocs in the order the source walk surfaces them, and the
     *         vocabulary ranking them; empty when no bloc qualifies, and empty by default for a view
     *         with no spotlight
     */
    default ListPicker<?> resolveBlocPicker(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        return ListPicker.empty();
    }

    /**
     * This view's picker under the player's current dominance and dev-reveal settings - the live
     * entry the sidebar and the stale-selection heal call, so neither has to read the toggles a
     * running pass would already hold.
     *
     * @param sector the sector whose economy the visibility gate reads; null yields an empty picker
     * @return this view's picker under the player's live settings; empty when no bloc qualifies
     */
    default ListPicker<?> resolveBlocPicker(SectorAPI sector) {
        return resolveBlocPicker(
            sector,
            DominanceRules.readFromLunaSettings(),
            PoliticalMapDevToggles.readFromLunaSettings().isShowingAllFactions());
    }

    /**
     * Turns a bloc-keyed stats read into the picker options a view offers, so an overriding view
     * declares only what distinguishes it - which of the present blocs are targets - rather than
     * repeating the crest, name, and option assembly every view resolves identically.
     *
     * <p>The crest comes from the bloc's colour faction, which is an alliance's lead member and, for
     * a faction bloc, the faction itself, so one lookup serves a grouped and an ungrouped view alike.
     * A bloc with no authored crest keeps its option and simply draws its name alone. The label is
     * this view's own {@link #resolveName}, always in the short form: the picker labels a bloc by its
     * short name regardless of the map's name-format setting, so a long-form map label never widens
     * the sidebar's option rows.
     *
     * <p>It is parameterised on the stats rather than fixed to {@link DominanceStats} because a view
     * ranks by whatever metrics its own layer is painted from: the identity half of an option is
     * assembled the same way for every view, while the payload half is the calling view's alone. That
     * also keeps this a default method rather than a static - the label is <em>this</em> view's
     * {@link #resolveName}, so no view has to reach into a sibling for a name.
     *
     * @param <S>            the calling view's own metrics type, ranked by that view's vocabulary;
     *                       bounded only by what every option must answer of its metrics, never by
     *                       one layer's numbers
     * @param sector         the sector a bloc's colour faction is read from
     * @param grouping       the grouping the stats were folded under, so the colour faction and the
     *                       name resolve against the same snapshot the numbers came from
     * @param statsByBlocId  each present bloc's metrics, in the order the source walk surfaced them,
     *                       which the returned options preserve
     * @param isSelectable   which of the present blocs this view offers as spotlight targets; the
     *                       one thing that differs between views, so a view that offers every
     *                       present bloc passes an always-true test
     * @return the selectable blocs in stats order, each pairing a bloc's identity with the metrics
     *         this view's picker sorts by
     */
    default <S extends BlocMetrics> List<RankedBloc<S>> buildSelectableBlocs(
            SectorAPI sector,
            HolderGrouping grouping,
            Map<String, S> statsByBlocId,
            Predicate<String> isSelectable) {

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
     * {@link #resolveBlocPicker} defaulting to no spotlight, so a new view opts in only when it
     * has a tooltip to show.
     *
     * @return this view's hover tooltip, or empty for a view that shows none
     */
    default Optional<MapHoverTooltip> resolveHoverTooltip() {
        return Optional.empty();
    }
}
