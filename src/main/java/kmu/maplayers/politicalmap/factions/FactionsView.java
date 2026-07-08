package kmu.maplayers.politicalmap.factions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.settings.FactionNameFormatChoice;
import kmu.util.KmuStrings;

/**
 * The faction-territory view's render rules: every faction is its own bloc, only
 * independent space recedes to the muted independent style, and a bloc's label is the
 * owning faction's own display name. These reproduce the political map's original
 * per-faction behaviour exactly - the faction view is the identity case the shared
 * pipeline was carved out of, so its grouping is {@link OwnershipGrouping#identity()}
 * and its two per-bloc decisions read the bloc id as a plain faction id.
 */
public final class FactionsView implements PoliticalMapView {

    // The faction grouping is identity - every faction is its own bloc - and that never
    // changes in a session, so this view's live-data revision is a fixed value. An
    // alliance change (which only the alliances view renders) can therefore never churn
    // the faction view's content token.
    private static final int STATIC_GROUPING_REVISION = 0;

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
    public int getGroupingRevision() {
        // Identity grouping never changes in a session, so the token contribution is fixed.
        return STATIC_GROUPING_REVISION;
    }

    @Override
    public OwnershipGrouping resolveGrouping() {
        // Every faction is its own bloc, so the pipeline resolves plain faction ownership.
        return OwnershipGrouping.identity();
    }

    @Override
    public boolean shouldUseIndependentStyle(String blocId, OwnershipGrouping grouping) {
        // Only independent space is drawn muted; every other faction paints in the full
        // faction style. The grouping is identity here, so the bloc id is the faction id.
        return Factions.INDEPENDENT.equals(blocId);
    }

    @Override
    public String resolveName(String blocId, OwnershipGrouping grouping, SectorAPI sector,
            FactionNameFormatChoice nameFormat) {
        // A faction bloc id is a real faction id, so the label is the faction's own name
        // in the player's chosen form; a faction that will not resolve carries no name.
        var faction = sector.getFaction(blocId);
        return faction == null ? null : resolveFactionName(faction, nameFormat);
    }

    // The faction's name in the player's chosen format: the abbreviated display name for
    // Short, the long-form title for Full (the default). getDisplayName is a faction's
    // short name and getDisplayNameLong its full title; both may be blank, which the
    // caller then treats as an unresolved name.
    private static String resolveFactionName(FactionAPI faction, FactionNameFormatChoice nameFormat) {
        return nameFormat == FactionNameFormatChoice.SHORT
                ? faction.getDisplayName()
                : faction.getDisplayNameLong();
    }
}
