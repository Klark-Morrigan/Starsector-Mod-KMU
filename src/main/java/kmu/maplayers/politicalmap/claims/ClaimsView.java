package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.hashing.Fingerprints;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.holders.ClaimsHolderProvider;
import kmu.maplayers.politicalmap.base.politics.holders.HolderProvider;
import kmu.maplayers.politicalmap.base.tooltip.SystemClaimTooltip;
import kmu.maplayers.politicalmap.factions.FactionsView;
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
 * independent ground, and a bloc is named by its claiming faction's own display name - so those three
 * seams delegate to {@link FactionsView} rather than restating them, which keeps the two views from
 * drifting on how a plain faction bloc paints and reads.
 *
 * <p>It offers no spotlight: the shared picker's selectable set is gated by market presence, which is
 * not the same as claim presence, so the view returns no selectable blocs and the sidebar draws no
 * filter for it. Every claim therefore paints at full strength.
 */
public final class ClaimsView implements PoliticalMapView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final ClaimsView INSTANCE = new ClaimsView();

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
        // claimant recedes to the muted style like independent ground; delegated so the two views
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

    // resolveSelectableBlocs is left to the interface default (no selectable blocs): claim presence is
    // not the market presence the shared picker ranks, so the claims view offers no spotlight and the
    // sidebar draws no filter for it, exactly as a view with no body controls inherits an empty list.
}
