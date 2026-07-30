package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.ownership.ClaimAugmentedOwnershipProvider;
import kmu.maplayers.politicalmap.base.politics.ownership.OwnershipProvider;

import java.util.List;
import java.util.Optional;

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
     * The ownership grouping this view resolves its pass under: identity for the
     * faction view (every faction its own bloc), alliance blocs for the alliances
     * view. Resolved once per rebuild and threaded through the pipeline, so a live set
     * is sampled a single time per pass and every stage keys off the same snapshot.
     *
     * @return the grouping that collapses factions into blocs for this pass
     */
    OwnershipGrouping resolveGrouping();

    /**
     * The source this view resolves its per-system ownership from: for the faction and alliance
     * views (the default), each inhabited system's dominant owner, extended with each system a
     * bloc claims but does not hold, drawn as an unfilled part of that bloc's territory; a view
     * painting ownership it derives some other way supplies its own source. Supplied per view so
     * the pipeline reads ownership through one seam without naming a concrete resolver, exactly as
     * it reads {@link #resolveGrouping}.
     *
     * @return the provider that resolves this view's per-system ownership
     */
    default OwnershipProvider resolveOwnershipProvider() {
        return ClaimAugmentedOwnershipProvider.INSTANCE;
    }

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
            OwnershipGrouping grouping,
            BlocStyleAdjustment adjustment);

    /**
     * How a bloc's fills, borders, and name are dimmed or recoloured before the pipeline
     * paints it, applied uniformly wherever the style classification is read. The faction
     * view never adjusts a bloc ({@link BlocStyleAdjustment#NONE}); the alliances view dims
     * and/or desaturates every non-alliance bloc when the player has asked it to, leaving
     * alliances untouched. Resolving it here lets the pipeline apply the two knobs without
     * knowing why a view wanted them, mirroring the {@link #shouldUseIndependentStyle} seam.
     *
     * @param blocId   the winning bloc for a system, as resolved under {@code grouping}
     * @param grouping the grouping this pass resolved, so the decision is a pure lookup over
     *                 the once-sampled snapshot rather than a fresh read
     * @return the per-bloc styling adjustment; {@link BlocStyleAdjustment#NONE} to draw the
     *         bloc exactly as classified
     */
    BlocStyleAdjustment resolveBlocStyleAdjustment(String blocId, OwnershipGrouping grouping);

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
            OwnershipGrouping grouping,
            SectorAPI sector,
            FactionNameFormatChoice nameFormat);

    /**
     * The blocs the filter picker offers under this view: factions with a visible weighted market
     * under the factions view, current alliances under the alliances view. Each carries the id the
     * filter stores, its picker label, and (for a faction) its crest. The list is what the picker
     * draws and what {@link kmu.maplayers.politicalmap.base.refresh.FilterSelection} heals a stale
     * saved selection against, so a bloc that is no longer here is no longer spotlightable.
     *
     * <p>Only blocs present somewhere qualify - a bloc holding a visible market in at least one system
     * (the {@code presence > 0} gate the shared stats read applies), so a bloc is selectable exactly
     * when it holds territory it could paint. The view supplies its own grouping and name resolver;
     * there is no new per-bloc seam, so a view decides which of its blocs are targets (every faction,
     * or only the alliance blocs) inside its own implementation. Each option carries that bloc's
     * whole-sector stats for the picker to sort and label by. The convenience overload reads the
     * player's live dominance and dev-reveal toggles so a caller with no pass of its own need not
     * thread them.
     *
     * <p>The spotlight is optional: the default offers no selectable blocs, so a view that paints an
     * ownership the shared market-presence gate cannot rank (the claims view, whose presence is claim
     * presence, not market presence) inherits an empty picker rather than overriding with three
     * arguments it would ignore. A view opts into the spotlight by overriding this, the same way it
     * opts into its own body controls.
     *
     * @param sector                       the sector whose economy the visibility gate reads; null
     *                                     yields an empty list
     * @param rules                        the dominance-weighting rules for this read, so selectable
     *                                     blocs are gated under the same rule the map paints under
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward a bloc's
     *                                     visibility (the "show all factions" dev reveal); false
     *                                     applies the normal known-to-player filter
     * @return the selectable blocs, in the order the economy walk surfaces them; empty when no bloc
     *         holds a visible weighted market, and empty by default for a view with no spotlight
     */
    default List<SelectableBloc> resolveSelectableBlocs(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        return List.of();
    }

    /**
     * The selectable blocs under this view, gated by the player's current dominance and dev-reveal
     * settings - the live entry the picker and the stale-selection heal call, so neither has to read
     * the toggles a running pass would already hold.
     *
     * @param sector the sector whose economy the visibility gate reads; null yields an empty list
     * @return the selectable blocs under the player's live settings; empty when none qualify
     */
    default List<SelectableBloc> resolveSelectableBlocs(SectorAPI sector) {
        return resolveSelectableBlocs(
                sector,
                DominanceRules.readFromLunaSettings(),
                PoliticalMapDevOverrides.readFromLunaSettings().isShowingAllFactions());
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
     * the faction and alliance views inject the domination breakdown, and the claims view - whose
     * ownership that breakdown does not describe - injects none and gets its own surface later.
     * Defaulting to empty makes "no tooltip" the base case, the same shape as
     * {@link #resolveSelectableBlocs} defaulting to no spotlight, so a new view opts in only when it
     * has a tooltip to show.
     *
     * @return this view's hover tooltip, or empty for a view that shows none
     */
    default Optional<MapHoverTooltip> resolveHoverTooltip() {
        return Optional.empty();
    }
}
