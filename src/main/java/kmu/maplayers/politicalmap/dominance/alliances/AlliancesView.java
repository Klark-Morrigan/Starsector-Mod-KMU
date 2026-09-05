package kmu.maplayers.politicalmap.dominance.alliances;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.hashing.Fingerprints;
import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.DominancePaintedView;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefreshSignal;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.tooltip.SystemDominationTooltip;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;
import kmu.starsector.nexerelin.NexerelinAlliances;
import kmu.util.KmuStrings;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The alliances view's render rules: allied factions fuse into one bloc per alliance so an
 * alliance reads as a single coloured, named cluster, while every unaligned faction keeps its
 * own border and name. Two orthogonal knobs recede a non-allied faction: Mute dims its opacity
 * by the muted modifier while leaving its faction style and colours intact, and Desaturate makes
 * it adopt the independent style - the independent borders and seams plus the desaturation
 * palette - so it reads as independent territory. Its fill holds the faction fill opacity, since
 * a desaturated territory reads as one uniform surface separated by colour alone. Desaturate
 * starts on and Mute off, so the view opens with the alliances already reading as the figure;
 * turn both off and a non-allied faction paints exactly as the faction view draws it, so a lone
 * faction reads identically in both views and only the allied factions differ between the two.
 * A sector holding no alliance recedes nothing, whatever the toggles say - there is no figure for
 * a backdrop to sit behind. It exists so the shared pipeline can paint alliances without knowing
 * anything about them - the view supplies only the grouping, the recede test, and the label.
 *
 * <p>The grouping is sampled live from Nexerelin, which is why the view is registered only
 * when Nex is present. It names no {@code exerelin.*} type of its own: {@link NexerelinAlliances}
 * is the gate that keeps every Nex reference behind its mod-enabled check, so this class is
 * safe to load on a Nex-free install even though it is never offered there.
 */
public final class AlliancesView implements DominancePaintedView {

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
    public int getContentRevision(MapLayerRefreshBoard board) {
        // This view's one live input: the alliance set, whose revision the sector watcher bumps when
        // membership moves, so the map repaints on a form/dissolve/transfer. The non-allied recede
        // toggles are not folded in here - they are sampled by the bake with every other preference
        // and folded in as values, so a flip that leaves the recede where it was rebuilds nothing
        // while a real flip rebuilds whichever view is up. Composed through a fingerprint so a second
        // live input later is one more source here rather than a wider contract.
        return Fingerprints.compute(
            () -> board.getRevision(PoliticalMapRefreshSignal.ALLIANCES));
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
            ElementStyleAdjustment adjustment) {

        // Genuine independent space always takes the independent style, exactly as the faction view
        // classifies it. A non-allied faction takes it too once it desaturates: desaturation means
        // "read as independent territory", so the bloc adopts the independent borders and seams, paired
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
            || (!grouping.isAlliance(blocId) && adjustment.shouldDesaturate());
    }

    @Override
    public ElementStyleAdjustment resolveBlocStyleAdjustment(
            String blocId,
            HolderGrouping grouping,
            ContentInputs contentInputs) {

        // An alliance keeps its full colour; only a non-alliance bloc recedes. The view owns just
        // that gate - how far a receded bloc dims or desaturates is its own non-allied recede set's
        // decision, taken off the bake's one sampling so every cell of this rebuild recedes by the
        // same reading of the toggles. Keeping the gate here (like the grouping already samples Nex)
        // leaves the pipeline a pure applier that never names an alliance.
        //
        // A sector holding no alliance at all recedes nothing. Every bloc would otherwise be
        // non-allied and the whole map would sink at once, with no figure left for it to be the
        // backdrop to - which reads as the layer having failed rather than as an answer. This
        // matches how the filter recede is only resolved while a bloc is actually spotlit; it is
        // load-bearing now that Desaturate starts on, since the empty-alliance case is where a
        // fresh campaign opens.
        if (!grouping.hasAnyAlliance() || grouping.isAlliance(blocId)) {
            return ElementStyleAdjustment.NONE;
        }
        return contentInputs.allianceRecedeAdjustment();
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
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        // Under this view only alliances are filter targets - a lone faction is not spotlightable
        // here, matching the view's role of grouping holders by alliance. The alliance grouping
        // folds each alliance's members into one bloc, so the shared stats read already ranks an
        // alliance as a unit; the gate just drops the present blocs that are lone factions.
        return grouping::isAlliance;
    }

    @Override
    public List<ControlSpec> getViewBodyControls(
            MapLayerRefreshBoard board,
            ScreenMemoryScope memoryScope) {

        // The Mute/Desaturate checkboxes belong only to this view, so they show solely while it is
        // selected; keeping them behind AllianceBodyControls keeps every alliance-only control in the
        // alliances package with the view that owns them.
        //
        // TODO: hand memoryScope to those checkboxes - the non-allied recede is still one slot per sector.
        return AllianceBodyControls.buildControls(board);
    }
}
