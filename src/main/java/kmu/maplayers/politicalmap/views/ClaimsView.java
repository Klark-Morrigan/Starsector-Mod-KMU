package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.owners.holders.base.ColonyReadRules;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolveSource;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.claims.ClaimSortModes;
import kmu.maplayers.politicalmap.claims.ClaimStats;
import kmu.maplayers.politicalmap.claims.ClaimStatsAggregator;
import kmu.maplayers.politicalmap.claims.PassClaimReaders;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.claims.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.holders.ClaimSystemHolderResolve;
import kmu.maplayers.politicalmap.holders.ClaimsHolderProvider;
import kmu.util.KmuPoliticalMapStringKeys;

import java.util.Optional;

/**
 * The claims view's render rules: every star system a faction claims shows here as solid territory
 * in the claimant's colours - the faction layer's look, keyed off the vanilla claim mechanic rather
 * than held markets. Claims are grouped strictly by claiming faction with no alliance rollup, so the
 * grouping is {@link HolderGrouping#identity()} and a claimed system reads under its own faction's
 * border and name.
 *
 * <p>A bloc paints and reads exactly as on the factions view, through the faction reading and
 * categories every view of this layer shares.
 *
 * <p>Its spotlight picker is its own, not the held layers': its blocs carry claim metrics rather
 * than domination ones, so the sort selector can only offer numbers this layer is actually painted
 * by. Who it lists is {@link #resolveBlocPickerRead}'s to state.
 *
 * <p>The market-inferred claims that make up most of what is painted move with the colonies behind
 * them, and a colony change already marks its system stale - through the refresh listeners, or the
 * staleness poll's holder diff where no event fires. Explicit claim-flag flips have no live
 * fingerprint, so a flag-only claim change waits until something else marks that system stale or
 * the map is rebuilt.
 */
public final class ClaimsView
        implements PoliticalHolderView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final ClaimsView INSTANCE = new ClaimsView();

    // Where this view's claim reader comes from, held as the means of opening one rather than as
    // a reader for the same reason the claim-reading holder providers hold it that way: a reader
    // answers off the colonies behind it, so one is opened over the read being made and discarded
    // with it. This view outlives every one of them.
    //
    // The one source behind the fills, the bands, the box and the picker, so no two of them can
    // read claims through different bindings.
    private final ClaimBreakdownReaderSource claimReaderSource;

    // Both stateless past the source, so this view builds each once.
    private final ClaimsHolderProvider holderProvider;
    private final SystemClaimTooltip hoverTooltip;

    private ClaimsView() {
        this(VanillaClaimBreakdownReader::new);
    }

    ClaimsView(ClaimBreakdownReaderSource claimReaderSource) {

        this.claimReaderSource = claimReaderSource;
        this.holderProvider = new ClaimsHolderProvider(claimReaderSource);
        this.hoverTooltip = SystemClaimTooltip.createReadingThrough(claimReaderSource);
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
        return KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_CLAIMS;
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // Claims are grouped strictly by claiming faction - no alliance rollup in what this layer
        // paints - so the pipeline resolves plain faction holding.
        //
        // About the fills alone. Both surfaces drawn over them consult the live alliance set
        // rather than this: the hover box to say which of the factions in a system stand with its
        // claim holder, and the band beneath it to judge which of them are actually rivals. Pinned
        // here, the two allied claimants would fuse into one territory in one of their colours and
        // the block naming them would be permanently empty on the one layer that draws it - which
        // is why what fuses and who stands together stay two separate reads.
        return HolderGrouping.identity();
    }

    @Override
    public HolderProvider resolveHolderProvider() {
        // Holder is the claim mechanic itself: every claimed system painted solid in its
        // claimant's colours, rather than the held-plus-claims default the faction view resolves.
        return holderProvider;
    }

    @Override
    public SystemHolderResolveSource resolveSystemHolderResolveSource() {
        // A marked system is re-derived to its claimant, through the claim reader this view reads
        // every claim through - the market contest the other two views re-derive by would hand a
        // claimed system to whoever holds it, which is not what this view painted.
        return ClaimSystemHolderResolve.createSourceReadingThrough(claimReaderSource);
    }

    @Override
    public SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {
        // Every cell here is painted by the claim mechanic, held systems included, so every band is
        // ranked by the contest - where the dominance views' composition would rank a claimed
        // system its claimant does not hold by the weights of whoever does, and lead the band on a
        // bloc the cell is not painted for.
        return ClaimedSystemRibbonPlanner.createForPass(inputs, claimReaderSource);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // The claim breakdown, not the domination one the other two views inject: this layer paints by
        // the claim mechanic, so what a hover has to explain is the claim contest rather than the
        // market standings - the same read the fills are resolved through.
        return Optional.of(hoverTooltip);
    }

    /**
     * The claims picker: every bloc the sector walk surfaced, carrying its whole-sector
     * {@link ClaimStats}, ranked by the
     * {@link ClaimSortModes} vocabulary and whatever the shared assembly offers behind it.
     *
     * <p>No gate of its own, so the list is everyone who paints or lives somewhere, not every faction
     * in the sector. A faction that claims a system but holds no colony anywhere is listed because it
     * paints territory here; a faction with colonies but no claim is listed because leaving it out
     * reads as the map having forgotten a faction the player can plainly see, and its row says what
     * it is - receded, with a claim count of zero - before the pick is made.
     *
     * <p>A claimless bloc stays pickable. Spotlighting one paints no territory, which is the honest
     * answer to "show me what this faction claims" when the answer is nowhere - but the systems it
     * lives in are spared the recede, so the pick still shows where the faction is while showing
     * that it claims none of it. Re-picking the lit row clears it as any other pick does.
     *
     * @param sector          the sector whose systems and colonies the claim stats are read from;
     *                        null yields an empty read
     * @param colonyReadRules what the player may be shown of a colony and what a decivilised world
     *                        counts as, so a bloc's market size counts the very colonies the map
     *                        paints it for
     * @return this view's picker, its blocs in the order the sector walk surfaces them, beside the
     *         systems that walk found each bloc claiming
     */
    @Override
    public OwnerPickerRead<RankedOwner<ClaimStats>> resolveBlocPickerRead(
            SectorAPI sector,
            ColonyReadRules colonyReadRules) {

        return readPickerUnderOneGrouping(
            sector,
            grouping -> {

                // The layer-generic pass: this layer is painted by claims, and a claim count and a
                // colony size are read without weighing anything.
                var pass = HolderPass.createOver(sector, colonyReadRules, grouping);

                // The claim half reads through the pass's own colony walk, so the two metrics cost one
                // traversal of each system between them rather than one apiece - and are answered off
                // the same reading of the sector, which is what keeps a claim count and a colony size
                // from describing a system at two different moments.
                return ClaimStatsAggregator.aggregateClaimStats(
                    pass,
                    PassClaimReaders.openClaimReaderOver(pass, claimReaderSource));

            },
            ClaimSortModes.MODES);
    }
}
