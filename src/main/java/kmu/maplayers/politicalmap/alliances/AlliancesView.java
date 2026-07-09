package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.maplayers.politicalmap.factions.FactionsView;
import kmu.settings.FactionNameFormatChoice;
import kmu.settings.KmuLunaSettings;
import kmu.starsector.nexerelin.NexerelinAlliances;
import kmu.util.KmuStrings;

/**
 * The alliances view's render rules: allied factions fuse into one bloc per alliance so an
 * alliance reads as a single coloured, named region, while every unaligned faction and
 * neutral recedes to the muted independent style yet keeps its own border and name. It
 * exists so the shared pipeline can paint alliances without knowing anything about them -
 * the view supplies only the grouping, the recede test, and the label.
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
    public int getGroupingRevision() {
        // The alliance set is live, so this view's live-data revision is the shared alliance
        // revision the sector watcher bumps when membership moves; folding it into the content
        // token is what repaints the alliances view on a form/dissolve/transfer without a reload.
        return PoliticalMapRefresh.getAllianceRevision();
    }

    @Override
    public OwnershipGrouping resolveGrouping() {
        // The live Nexerelin alliance set, sampled once per rebuild. Falls back to identity if Nex
        // is somehow absent, though the view is only ever registered when it is present.
        return NexerelinAlliances.resolveGrouping();
    }

    @Override
    public boolean shouldUseIndependentStyle(String blocId, OwnershipGrouping grouping) {
        // Only alliances paint in full faction colour; every lone faction and neutral recedes to the
        // muted independent style, so the alliances stand out against a common muted ground.
        return !grouping.isAlliance(blocId);
    }

    @Override
    public BlocStyleAdjustment resolveBlocStyleAdjustment(String blocId, OwnershipGrouping grouping) {
        // An alliance keeps its full colour; only a non-alliance bloc recedes, and then only as
        // far as the player's Mute/Desaturate choices ask. Sampling the per-save toggles and the
        // modifier here (like the grouping already samples Nex) keeps the pipeline a pure applier
        // that never names an alliance. Mute scales opacity by the modifier value, not a constant,
        // so 0 hides a non-allied bloc and 1 leaves it untouched.
        if (grouping.isAlliance(blocId)) {
            return BlocStyleAdjustment.NONE;
        }
        boolean isMuted = AllianceStylePreferences.isNonAlliedMuted();
        boolean shouldDesaturate = AllianceStylePreferences.isNonAlliedDesaturated();
        double opacityMultiplier = isMuted
                ? KmuLunaSettings.getPoliticalMapAllianceMutedOpacityModifier()
                : 1.0;
        return new BlocStyleAdjustment(opacityMultiplier, shouldDesaturate);
    }

    @Override
    public String resolveName(String blocId, OwnershipGrouping grouping, SectorAPI sector,
            FactionNameFormatChoice nameFormat) {
        var allianceName = grouping.resolveAllianceName(blocId);
        if (allianceName != null) {
            return allianceName;
        }
        // A non-alliance bloc is a lone faction, named exactly as the faction view names it, so the
        // two views can never drift on how a plain faction's label reads.
        return FactionsView.INSTANCE.resolveName(blocId, grouping, sector, nameFormat);
    }
}
