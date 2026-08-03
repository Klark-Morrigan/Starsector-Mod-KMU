package kmu.maplayers.politicalmap.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.hashing.Fingerprints;
import kmlib.starsector.factions.FactionCrests;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.BlocStatsAggregator;
import kmu.maplayers.politicalmap.base.tooltip.SystemDominationTooltip;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The faction-territory view's render rules: every faction is its own bloc, only
 * independent space recedes to the muted independent style, and a bloc's label is the
 * owning faction's own display name. These reproduce the political map's original
 * per-faction behaviour exactly - the faction view is the identity case the shared
 * pipeline was carved out of, so its grouping is {@link HolderGrouping#identity()}
 * and its two per-bloc decisions read the bloc id as a plain faction id.
 */
public final class FactionsView implements PoliticalMapView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final FactionsView INSTANCE = new FactionsView();

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
        // "Factions" - the same label the pre-radio faction toggle carried, now this view's
        // segment on the view-selector radio.
        return KmuStrings.POLITICAL_MAP_CTL_FACTIONS;
    }

    @Override
    public int getContentRevision() {
        // The faction view samples nothing live - its grouping is identity and never changes in a
        // session - so it folds no sources and returns the fixed no-source constant. An alliance
        // change (which only the alliances view renders) therefore never churns the faction view.
        return Fingerprints.compute();
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // Every faction is its own bloc, so the pipeline resolves plain faction holding.
        return HolderGrouping.identity();
    }

    @Override
    public boolean shouldUseIndependentStyle(
            String blocId,
            HolderGrouping grouping,
            ElementStyleAdjustment adjustment) {
        // Genuine independent space always takes the independent style. A faction the recede has
        // desaturated takes it too: desaturation means "read as background ground", so the bloc
        // adopts the independent borders and seams paired with the desaturation palette the same
        // adjustment carries, rather than sitting at full faction border weight and slot with only
        // its colour greyed. The grouping is identity here, so no bloc is an alliance and every
        // desaturated faction qualifies; a merely muted (dimmed, not desaturated) faction keeps the
        // faction bundle, so dimming alone never swaps border weight.
        //
        // The test reads the passed adjustment - the filter recede already unioned in - not this
        // view's own toggle, so the border bundle and the desaturation palette can never disagree
        // about whether a bloc has desaturated.
        return Factions.INDEPENDENT.equals(blocId) || adjustment.shouldDesaturate();
    }

    @Override
    public ElementStyleAdjustment resolveBlocStyleAdjustment(
            String blocId,
            HolderGrouping grouping) {
        // The faction view dims or recolours no bloc - every faction paints exactly as its
        // style classification says, so there is nothing for the pipeline to adjust.
        return ElementStyleAdjustment.NONE;
    }

    @Override
    public String resolveName(
            String blocId,
            HolderGrouping grouping,
            SectorAPI sector,
            FactionNameFormatChoice nameFormat) {
        // A faction bloc id is a real faction id, so the label is the faction's own name
        // in the player's chosen form; a faction that will not resolve carries no name.
        var faction = sector.getFaction(blocId);
        return faction == null ? null : resolveFactionName(faction, nameFormat);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // The faction and alliance views share the one domination tooltip: it adapts flat vs nested
        // off the active grouping, so both layers show the same per-system breakdown, flat here.
        return Optional.of(SystemDominationTooltip.INSTANCE);
    }

    @Override
    public List<SelectableBloc> resolveSelectableBlocs(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        // Under identity every faction is its own bloc, so every present bloc is a selectable target -
        // the view maps each straight to an option, leaving the presence gate (a bloc appears in the
        // stats exactly when it holds a market somewhere) to the shared stats read both views draw
        // from, and carrying that bloc's stats onto the option for the picker to sort and label by.
        var grouping = resolveGrouping();
        var pass = new DominancePass(rules, shouldIncludeUndiscoveredMarkets, grouping);
        var selectableBlocs = new ArrayList<SelectableBloc>();

        for (var entry : BlocStatsAggregator.aggregateBlocStats(sector, pass).entrySet()) {
            var blocId = entry.getKey();
            var faction = sector.getFaction(blocId);

            // The crest is the picker row's icon; a faction with no authored crest simply draws its
            // name alone, so a null path is a valid option rather than a dropped one.
            var crestSpritePath = FactionCrests.resolveCrestPath(faction);

            // The picker labels a faction by its short name regardless of the map's name-format
            // setting, so a long-form map label never widens the sidebar's option rows.
            var displayName = resolveName(
                blocId,
                grouping,
                sector,
                FactionNameFormatChoice.SHORT);
                    
            selectableBlocs.add(new SelectableBloc(
                blocId,
                displayName,
                crestSpritePath,
                entry.getValue()));
        }
        return selectableBlocs;
    }

    // The faction's name in the player's chosen format: the abbreviated display name for
    // Short, the long-form title for Full (the default). getDisplayName is a faction's
    // short name and getDisplayNameLong its full title; both may be blank, which the
    // caller then treats as an unresolved name.
    private static String resolveFactionName(
            FactionAPI faction,
            FactionNameFormatChoice nameFormat) {
        return nameFormat == FactionNameFormatChoice.SHORT
            ? faction.getDisplayName()
            : faction.getDisplayNameLong();
    }
}
