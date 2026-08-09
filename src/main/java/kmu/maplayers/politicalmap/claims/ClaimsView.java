package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.hashing.Fingerprints;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.VanillaClaimReader;
import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.maplayers.politicalmap.base.politics.ClaimStatsAggregator;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimsHolderProvider;
import kmu.maplayers.politicalmap.base.politics.holders.HolderProvider;
import kmu.maplayers.politicalmap.base.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.factions.FactionsView;
import kmu.util.KmuStrings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The claims view's render rules: every star system a faction claims shows here as solid territory
 * in the claimant's colours - the faction layer's look, keyed off the vanilla claim mechanic rather
 * than held markets. Claims are grouped strictly by claiming faction with no alliance rollup, so the
 * grouping is {@link HolderGrouping#identity()} and a claimed system reads under its own faction's
 * border and name.
 *
 * <p>A bloc's styling and label are the faction view's exactly - an independent claimant recedes like
 * independent territory, and a bloc is named by its claiming faction's own display name - so those three
 * seams delegate to {@link FactionsView} rather than restating them, which keeps the two views from
 * drifting on how a plain faction bloc paints and reads.
 *
 * <p>Its spotlight picker is its own, not the held layers': the blocs it offers are the ones that
 * claim a system, and they carry claim metrics rather than domination ones, so the sort selector can
 * only offer numbers this layer is actually painted by.
 */
public final class ClaimsView implements PoliticalMapView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final ClaimsView INSTANCE = new ClaimsView();

    // The claim source the picker's counts are read through - the same port the view's holder provider
    // resolves its territory from, so the list and the map can never disagree on who claims what.
    private final ClaimReader claimReader = new VanillaClaimReader();

    private ClaimsView() {
    }

    @Override
    public String getId() {
        // Save-stable identity of the claims view; frozen once shipped, since renaming it resets a
        // save that selected this view to the default.
        return "claims";
    }

    @Override
    public String getSegmentLabelKey() {
        // "Claims" - this view's segment on the view-selector radio, sibling to the faction and
        // alliance ones.
        return KmuStrings.POLITICAL_MAP_CTL_CLAIMS;
    }

    @Override
    public int getContentRevision() {
        // The market-inferred claims that make up the majority change with the economy, which the
        // shared economy revision already repaints on, so this view samples no live input of its own
        // and returns the fixed no-source constant. A live fingerprint for explicit claim-flag flips
        // is a later concern; until then a flag-only claim change waits for the next economy rebuild.
        return Fingerprints.compute();
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // Claims are grouped strictly by claiming faction - no alliance rollup on this layer - so the
        // pipeline resolves plain faction holding.
        return HolderGrouping.identity();
    }

    @Override
    public HolderProvider resolveHolderProvider() {
        // Holder is the claim mechanic itself: every claimed system painted solid in its
        // claimant's colours, rather than the held-plus-claims default the faction view resolves.
        return ClaimsHolderProvider.INSTANCE;
    }

    @Override
    public boolean shouldUseIndependentStyle(
            String blocId,
            HolderGrouping grouping,
            ElementStyleAdjustment adjustment) {
        // A claimant bloc styles exactly as the faction view styles a held one, so an independent
        // claimant recedes to the muted style like independent territory; delegated so the two views
        // can never diverge on the classification.
        return FactionsView.INSTANCE.shouldUseIndependentStyle(blocId, grouping, adjustment);
    }

    @Override
    public ElementStyleAdjustment resolveBlocStyleAdjustment(
            String blocId,
            HolderGrouping grouping) {
        // The claims view dims or recolours no bloc, exactly as the faction view does not; delegated
        // to keep that one decision in a single place.
        return FactionsView.INSTANCE.resolveBlocStyleAdjustment(blocId, grouping);
    }

    @Override
    public String resolveName(
            String blocId,
            HolderGrouping grouping,
            SectorAPI sector,
            FactionNameFormatChoice nameFormat) {
        // A claim bloc id is a plain faction id under identity grouping, so the label is the claiming
        // faction's own name - resolved by the faction view so the two never drift on a faction label.
        return FactionsView.INSTANCE.resolveName(blocId, grouping, sector, nameFormat);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // The claim breakdown, not the domination one the other two views inject: this layer paints by
        // the claim mechanic, so what a hover has to explain is the claim contest rather than the
        // market standings - the same read the fills are resolved through.
        return Optional.of(SystemClaimTooltip.INSTANCE);
    }

    /**
     * The claims picker: every bloc that claims at least one system, carrying its whole-sector
     * {@link ClaimStats}, paired with {@link ClaimSortMode}'s vocabulary.
     *
     * <p>The gate is claim presence, not the market presence the held layers list by, because a
     * picker's job is to spotlight something the layer draws. A faction that claims a system but
     * holds no colony anywhere is therefore listed - it paints territory here - while a faction with
     * colonies but no claim is dropped, since spotlighting it would recede the whole sector in
     * favour of nothing.
     *
     * @param sector                           the sector whose systems and economy the claim stats are
     *                                         read from; null yields an empty picker
     * @param rules                            the dominance-weighting rules for this read; carried by
     *                                         the shared pass, which the market-size half of the stats
     *                                         reads its economy through
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward a bloc's
     *                                         market size (the "show all factions" dev reveal)
     * @return this view's picker, its blocs in the order the sector walk surfaces them
     */
    @Override
    public ListPicker<RankedBloc<ClaimStats>> resolveBlocPicker(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        // The grouping is resolved once and handed to both halves, so the numbers, the crest, and the
        // name all read against one snapshot rather than three live samples.
        var grouping = resolveGrouping();
        var pass = new DominancePass(rules, shouldIncludeUndiscoveredMarkets, grouping);

        return new ListPicker<>(
            buildSelectableBlocs(
                sector,
                grouping,
                listClaimingBlocs(
                    ClaimStatsAggregator.aggregateClaimStats(sector, pass, claimReader)),
                blocId -> true),
            ClaimSortMode.MODES);
    }

    // Drops the blocs the fold surfaced for their colonies alone, leaving the claimants in the walk
    // order the fold produced. Gating the map rather than the option assembly is what lets the shared
    // assembly take an always-true test: the assembly's gate reads a bloc id, which cannot answer how
    // much that bloc claims.
    private static Map<String, ClaimStats> listClaimingBlocs(Map<String, ClaimStats> statsByBlocId) {

        var claimingBlocs = new LinkedHashMap<String, ClaimStats>();
        for (var entry : statsByBlocId.entrySet()) {
            if (entry.getValue().claims() > 0) {
                claimingBlocs.put(entry.getKey(), entry.getValue());
            }
        }
        return claimingBlocs;
    }
}
