package kmu.maplayers.politicalmap.views;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.util.KmuPoliticalMapStringKeys;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * The faction-territory view: every faction is its own bloc, the identity case of the shared
 * pipeline.
 */
public final class FactionsView
        implements DominancePaintedView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final FactionsView INSTANCE = new FactionsView();

    // Ranks under this view's own grouping, so the box explains the fills this view painted.
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
        return KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_FACTIONS;
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // Every faction is its own bloc, so the pipeline resolves plain faction holding.
        return HolderGrouping.identity();
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
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
