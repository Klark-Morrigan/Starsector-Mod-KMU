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
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.base.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;
import kmu.util.KmuStrings;

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
 * claim a system or hold a colony, and they carry claim metrics rather than domination ones, so the
 * sort selector can only offer numbers this layer is actually painted by. A bloc that holds colonies
 * while claiming nothing is listed receded at a count of zero rather than dropped, so the list
 * accounts for every faction the player can see instead of appearing to have forgotten one.
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
    public SystemRibbonPlanner resolveRibbonPlanner(
            SectorAPI sector,
            HolderGrouping grouping,
            RibbonSegmentLengths lengths) {
        // Every cell here is painted by the claim mechanic, held systems included, so every band is
        // counted from the contest - where the held-plus-claims default would count a claimed system
        // its claimant does not hold by the markets of whoever does, and lead the band on a bloc the
        // cell is not painted for.
        return ClaimedSystemRibbonPlanner.createForSector(sector, grouping, lengths);
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
     * The claims picker: every bloc the sector walk surfaced - each one that claims a system or
     * holds a colony - carrying its whole-sector {@link ClaimStats}, paired with
     * {@link ClaimSortMode}'s vocabulary.
     *
     * <p>No gate of its own, so the list is whoever paints or holds something. A faction that claims
     * a system but holds no colony anywhere is listed because it paints territory here; a faction
     * with colonies but no claim is listed because leaving it out reads as the map having forgotten
     * a faction the player can plainly see, and its row says what it is - receded, with a claim count
     * of zero - before the pick is made. A bloc with neither never reached the fold, so the list is
     * everyone who paints or holds, not every faction in the sector.
     *
     * <p>A claimless bloc stays pickable. Spotlighting one paints no territory, which is the honest
     * answer to "show me what this faction claims" when the answer is nowhere - but the systems it
     * lives in are spared the recede, so the pick still shows where the faction is while showing
     * that it claims none of it. Re-picking the lit row clears it as any other pick does.
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
                ClaimStatsAggregator.aggregateClaimStats(sector, pass, claimReader),
                blocId -> true),
            ClaimSortMode.MODES);
    }
}
