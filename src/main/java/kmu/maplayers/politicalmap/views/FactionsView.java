package kmu.maplayers.politicalmap.views;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.util.KmuStringKeys;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * The faction-territory view's render rules: every faction is its own bloc. The faction view is
 * the identity case of the shared pipeline, so its grouping is {@link HolderGrouping#identity()},
 * and the holder owner reading it shares with the other dominance-painted view reads every bloc ID
 * as a plain faction ID: only independent space recedes to the muted independent style, and a
 * bloc's label is the owning faction's own display name.
 */
public final class FactionsView implements DominancePaintedView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final FactionsView INSTANCE = new FactionsView();

    // The hover box this view injects, ranking under this view's own grouping - so the box explains
    // the fills this view painted, whichever layer or screen is asking.
    private final SystemDominationTooltip hoverTooltip =
        SystemDominationTooltip.createPaintedBy(this::resolveGrouping);

    private FactionsView() {
    }

    @Override
    public String getId() {
        // Save-stable identity of the faction view; frozen once shipped, since renaming it resets
        // a save that selected this view to the default.
        return "factions";
    }

    @Override
    public String getSegmentLabelKey() {
        // "Factions" - this view's segment on the view-selector radio.
        return KmuStringKeys.POLITICAL_MAP_CTL_FACTIONS;
    }

    @Override
    public int getContentRevision(MapLayerRefreshBoard board) {
        // The alliance set is this view's one live input. Nothing it paints moves with it - the
        // grouping is identity and never changes in a session - but what its bands report does: a run
        // is laid at contested length only against a bloc the painter is not allied with. Without
        // this fold, an alliance formed or dissolved in play leaves every band drawn at stale
        // lengths until an unrelated economy change happens to rebuild the map.
        return PoliticalMapRefreshSignal.computeAllianceContentRevision(board);
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // Every faction is its own bloc, so the pipeline resolves plain faction holding.
        return HolderGrouping.identity();
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // The faction and alliance views share the one domination tooltip: it adapts flat vs nested
        // off the active grouping, so both layers show the same per-system breakdown, flat here.
        return Optional.of(hoverTooltip);
    }

    @Override
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        // Under identity every faction is its own bloc, so every present bloc is a selectable target.
        // The presence gate (a bloc appears in the stats exactly when it holds a market somewhere) is
        // the shared stats read's, which every dominance-painted view draws from.
        return blocId -> true;
    }
}
