package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.hashing.Fingerprints;
import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.BlocStatsAggregator;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.maplayers.politicalmap.base.tooltip.SystemDominationTooltip;
import kmu.maplayers.politicalmap.factions.FactionsView;
import kmu.starsector.nexerelin.NexerelinAlliances;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The alliances view's render rules: allied factions fuse into one bloc per alliance so an
 * alliance reads as a single coloured, named cluster, while every unaligned faction keeps its
 * own border and name. Two orthogonal knobs recede a non-allied faction: Mute dims its opacity
 * by the muted modifier while leaving its faction style and colours intact, and Desaturate makes
 * it adopt the independent style - the independent borders and seams plus the desaturation
 * palette - so it reads as independent ground. Its fill holds the faction fill opacity, since
 * desaturated ground reads as one uniform surface separated by colour alone. With both off it
 * paints exactly as the faction view draws it, so a lone
 * faction reads identically in both views and only the allied factions differ between the two. It
 * exists so the shared pipeline can paint alliances without knowing anything about them - the view
 * supplies only the grouping, the recede test, and the label.
 *
 * <p>The grouping is sampled live from Nexerelin, which is why the view is registered only
 * when Nex is present. It names no {@code exerelin.*} type of its own: {@link NexerelinAlliances}
 * is the gate that keeps every Nex reference behind its mod-enabled check, so this class is
 * safe to load on a Nex-free install even though it is never offered there.
 */
public final class AlliancesView implements PoliticalMapView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final AlliancesView INSTANCE = new AlliancesView();

    private AlliancesView() {
    }

    @Override
    public String getId() {
        // Save-stable identity of the alliances view; frozen once shipped, since renaming it resets
        // a save that selected this view to the default.
        return "alliances";
    }

    @Override
    public String getSegmentLabelKey() {
        // "Alliances" - this view's segment on the view-selector radio, sibling to the faction one.
        return KmuStrings.POLITICAL_MAP_CTL_ALLIANCES;
    }

    @Override
    public int getContentRevision() {
        // This view's rebuild is driven by two live inputs, composed into one fingerprint the content
        // token reads: the alliance set (the sector watcher bumps its revision when membership moves,
        // repainting on a form/dissolve/transfer) and this view's non-allied recede toggles (their
        // setter bumps the recede-style revision on a Mute/Desaturate flip, since those sidebar-only
        // toggles never move settingsRevision). The recede-style revision is one coarse signal every
        // recede set shares, so a filter-recede flip advances it too; harmless here, since this view
        // only draws the non-allied ground and simply rebuilds. Composing the two means a third live
        // input later is one more source here, not a wider contract; a change to either forces a
        // rebuild.
        return Fingerprints.compute(
                () -> MapLayerRefresh.getRevision(PoliticalMapRefreshSignal.ALLIANCES),
                () -> MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE));
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // The live Nexerelin alliance set, sampled once per rebuild. Falls back to identity if Nex
        // is somehow absent, though the view is only ever registered when it is present.
        return NexerelinAlliances.resolveGrouping();
    }

    @Override
    public boolean shouldUseIndependentStyle(
            String blocId,
            HolderGrouping grouping,
            BlocStyleAdjustment adjustment) {
        // Genuine independent space always takes the independent style, exactly as the faction view
        // classifies it. A non-allied faction takes it too once it desaturates: desaturation means
        // "read as independent ground", so the bloc adopts the independent borders and seams, paired
        // with the desaturation palette the same adjustment carries - not a bare independent recolour
        // painted over the faction style. Its fill is the exception: a desaturated bloc fills at the
        // faction opacity, so the desaturated background stays one uniform surface rather than
        // splitting into two weights of grey. An alliance always paints in the full faction style so
        // it stands out. Muting never swaps the bundle; it only dims the active style via the opacity
        // modifier, so a lone faction that neither desaturates nor mutes reads exactly as the faction
        // view draws it.
        //
        // The test reads the passed adjustment - every reason to recede already unioned into it -
        // rather than this view's own toggle, so the bundle and the palette can never disagree about
        // whether a bloc is desaturated. Reading the view's toggle alone would leave a faction the
        // filter recede desaturates painted grey but still in the faction bundle, and flipping this
        // view's toggle would then appear to change nothing but the border weight.
        return Factions.INDEPENDENT.equals(blocId)
                || (!grouping.isAlliance(blocId) && adjustment.desaturate());
    }

    @Override
    public BlocStyleAdjustment resolveBlocStyleAdjustment(
            String blocId,
            HolderGrouping grouping) {
        // An alliance keeps its full colour; only a non-alliance bloc recedes. The view owns just
        // that gate - how far a receded bloc dims or desaturates is its own non-allied recede set's
        // decision, so every non-allied faction takes the one adjustment that set resolves. Keeping
        // the gate here (like the grouping already samples Nex) leaves the pipeline a pure applier
        // that never names an alliance.
        if (grouping.isAlliance(blocId)) {
            return BlocStyleAdjustment.NONE;
        }
        return RecedePreferences.ALLIANCE_NON_ALLIED.resolveRecedeAdjustment();
    }

    @Override
    public String resolveName(
            String blocId,
            HolderGrouping grouping,
            SectorAPI sector,
            FactionNameFormatChoice nameFormat) {
        var allianceName = grouping.resolveAllianceName(blocId);
        if (allianceName != null) {
            return allianceName;
        }
        // A non-alliance bloc is a lone faction, named exactly as the faction view names it, so the
        // two views can never drift on how a plain faction's label reads.
        return FactionsView.INSTANCE.resolveName(blocId, grouping, sector, nameFormat);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // The alliance and faction views share the one domination tooltip: it adapts flat vs nested
        // off the active grouping, so under this view a bloc reads as its members nested beneath it.
        return Optional.of(SystemDominationTooltip.INSTANCE);
    }

    @Override
    public List<SelectableBloc> resolveSelectableBlocs(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        // Under this view only alliances are filter targets - a lone faction is not spotlightable
        // here, matching the view's role of grouping holders by alliance. The alliance grouping
        // folds each alliance's members into one bloc, so the shared stats read already ranks an
        // alliance as a unit; the view just drops the present blocs that are lone factions and
        // carries each surviving alliance's stats onto its option for the picker to sort and label by.
        var grouping = resolveGrouping();
        var pass = new DominancePass(rules, shouldIncludeUndiscoveredMarkets, grouping);
        var selectableBlocs = new ArrayList<SelectableBloc>();
        for (var entry : BlocStatsAggregator.aggregateBlocStats(sector, pass).entrySet()) {
            var blocId = entry.getKey();
            if (!grouping.isAlliance(blocId)) {
                continue;
            }
            // An alliance paints in its lead member's palette, so its picker row carries that
            // member's crest and reads like a faction row rather than a blank one.
            // resolveColorFactionId names the colour (lead) faction; a member with no authored
            // crest leaves the row to draw its name alone, so a null path is a valid option.
            var colorFaction = sector.getFaction(grouping.resolveColorFactionId(blocId));
            var crestSpritePath = FactionCrests.resolveCrestPath(colorFaction);
            // The name comes from the grouping via resolveName, so the format argument never
            // matters here.
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

    @Override
    public List<ControlSpec> getViewBodyControls() {
        // The Mute/Desaturate checkboxes belong only to this view, so they show solely while it is
        // selected; keeping them behind AllianceBodyControls keeps every alliance-only control in the
        // alliances package with the view that owns them.
        return AllianceBodyControls.buildControls();
    }
}
