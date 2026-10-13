package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.sidebar.BodyControlTarget;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.holders.alliances.AllianceOwnerReading;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.preferences.RecedePreferences;
import kmu.maplayers.ownermap.sidebar.RecedeControl;
import kmu.maplayers.politicalmap.dominance.tooltip.SystemDominationTooltip;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.util.KmuPoliticalMapStringKeys;
import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The alliances view: allied factions fuse into one bloc per alliance, while every unaligned faction
 * keeps its own border and name and recedes behind them by this view's own Mute and Desaturate
 * toggles. The view supplies the grouping and how far its backdrop recedes; the alliance owner
 * reading answers which blocs recede and what they are called.
 *
 * <p>The grouping is sampled live from Nexerelin, which is why the view is registered only
 * when Nex is present. It names no {@code exerelin.*} type of its own: {@link NexerelinAlliances}
 * is the gate that keeps every Nex reference behind its mod-enabled check, so this class is
 * safe to load on a Nex-free install even though it is never offered there.
 */
public final class AlliancesView
        implements DominancePaintedView {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final AlliancesView INSTANCE = new AlliancesView();

    // This view's own backdrop: every faction outside an alliance, receded so the alliances read as
    // the figure. Independent of the layer's filter recede, so it keeps toggles of its own. The keys
    // are frozen save keys: a rename resets every save's choice.
    //
    // Not folded into the content revision: the bake samples the toggles with every other
    // preference and folds them in as values, so a flip that leaves the recede where it was rebuilds
    // nothing.
    static final RecedePreferences NON_ALLIED_RECEDE = new RecedePreferences(
        "$kmu_political_alliance_recede_mute",
        "$kmu_political_alliance_recede_desaturate");

    // Ranks under this view's own grouping, so the box explains the fills this view painted.
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
        return KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_ALLIANCES;
    }

    @Override
    public HolderGrouping resolveGrouping() {
        // The live Nexerelin alliance set, sampled once per rebuild. Falls back to identity if Nex
        // is somehow absent, though the view is only ever registered when it is present.
        return NexerelinAlliances.resolveGrouping();
    }

    @Override
    public OwnerReading resolveOwnerReading(SectorAPI sector, HolderGrouping grouping) {
        // An alliance reads by its own name in its colour member's shades, and a lone faction as the
        // faction view reads it - the alliance reading adds the first to the second.
        return new AllianceOwnerReading(sector, grouping);
    }

    @Override
    public ElementStyleAdjustment resolveViewRecedeAdjustment(ScreenMemoryScope memoryScope) {
        // How far the non-allied backdrop recedes on the screen being painted, off this view's own
        // toggles. Which blocs are the backdrop is the alliance owner reading's gate: every lone
        // faction, and only where some alliance exists.
        return NON_ALLIED_RECEDE.resolveRecedeAdjustment(memoryScope);
    }

    @Override
    public Optional<MapHoverTooltip> resolveHoverTooltip() {
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

        // The shared recede control bound to this view's own toggles, shown only while it is
        // selected. The checkbox shape and wiring are the control's; the set and caption are this
        // view's.
        return RecedeControl.buildControls(
            NON_ALLIED_RECEDE,
            KmuStringKeys.get(KmuPoliticalMapStringKeys.POLITICAL_MAP_CTL_NON_ALLIED_CAPTION),
            target);
    }
}
