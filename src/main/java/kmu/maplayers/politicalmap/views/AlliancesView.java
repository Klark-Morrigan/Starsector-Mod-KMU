package kmu.maplayers.politicalmap.views;

import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.preferences.RecedePreferences;
import kmu.maplayers.ownermap.sidebar.BodyControlTarget;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.util.KmuStringKeys;

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
 * anything about them - the view supplies the grouping and how far its backdrop recedes, and the
 * holder owner reading it shares with the faction view answers the recede test and the label off
 * that grouping.
 *
 * <p>The grouping is sampled live from Nexerelin, which is why the view is registered only
 * when Nex is present. It names no {@code exerelin.*} type of its own: {@link NexerelinAlliances}
 * is the gate that keeps every Nex reference behind its mod-enabled check, so this class is
 * safe to load on a Nex-free install even though it is never offered there.
 */
public final class AlliancesView implements DominancePaintedView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final AlliancesView INSTANCE = new AlliancesView();

    // This view's own backdrop: every faction outside an alliance, receded so the alliances read as
    // the figure. Independent of the layer's filter recede, so it keeps its own toggles under the keys
    // they have always been saved under - frozen, since a rename resets every save's choice.
    static final RecedePreferences NON_ALLIED_RECEDE = new RecedePreferences(
        "$kmu_political_alliance_recede_mute",
        "$kmu_political_alliance_recede_desaturate");

    // The hover box this view injects, ranking under this view's own grouping - so the box explains
    // the fills this view painted, whichever layer or screen is asking.
    private final SystemDominationTooltip hoverTooltip =
        SystemDominationTooltip.createPaintedBy(this::resolveGrouping);

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
        // "Alliances" - this view's segment on the view-selector radio, sibling to the faction and
        // claims ones.
        return KmuStringKeys.POLITICAL_MAP_CTL_ALLIANCES;
    }

    @Override
    public int getContentRevision(MapLayerRefreshBoard board) {
        // This view's one live input: the alliance set, whose revision the sector watcher bumps when
        // membership moves, so the map repaints on a form/dissolve/transfer. It is the set this view
        // paints its blocs out of, so a membership change moves the fills themselves rather than only
        // what is reported over them. The non-allied recede toggles are not folded in - they are
        // sampled by the bake with every other preference and folded in as values, so a flip that
        // leaves the recede where it was rebuilds nothing while a real flip rebuilds whichever view
        // is up.
        return PoliticalMapRefreshSignal.computeAllianceContentRevision(board);
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // The live Nexerelin alliance set, sampled once per rebuild. Falls back to identity if Nex
        // is somehow absent, though the view is only ever registered when it is present.
        return NexerelinAlliances.resolveGrouping();
    }

    @Override
    public ElementStyleAdjustment resolveViewRecedeAdjustment(ScreenMemoryScope memoryScope) {
        // How far the non-allied backdrop recedes on the screen being painted, off this view's own
        // toggles. Which blocs are the backdrop is the holder owner reading's gate: every lone
        // faction, and only where some alliance exists.
        return NON_ALLIED_RECEDE.resolveRecedeAdjustment(memoryScope);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
        // The alliance and faction views share the one domination tooltip: it adapts flat vs nested
        // off the active grouping, so under this view a bloc reads as its members nested beneath it.
        return Optional.of(hoverTooltip);
    }

    @Override
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        // Under this view only alliances are filter targets - a lone faction is not spotlightable
        // here, matching the view's role of grouping holders by alliance. The alliance grouping
        // folds each alliance's members into one bloc, so the shared stats read already ranks an
        // alliance as a unit; the gate just drops the present blocs that are lone factions.
        return grouping::isGroupedBloc;
    }

    @Override
    public List<ControlSpec> getViewBodyControls(BodyControlTarget target) {

        // The Mute/Desaturate checkboxes belong only to this view, so they show solely while it is
        // selected; keeping them behind AllianceBodyControls keeps every alliance-only control in one
        // place beside the view that owns them.
        return AllianceBodyControls.buildControls(target);
    }
}
