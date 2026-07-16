package kmu.maplayers.politicalmap.base.render.labels.anchor;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.factions.StarsectorFactionColors;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.geometry.FrontierSettings;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.geometry.SystemClusters;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.settings.KmuLunaSettings;

import java.util.List;
import java.util.Map;

/**
 * Drives the cluster-label placements: clusters the owned systems, reads the live tuning and
 * palette, wires the active view and any active filter into the per-bloc colour and name
 * resolvers, and hands it all to the pure {@link ClusterAnchorPlacement} search - then keeps
 * the resulting overlay list current in place.
 *
 * <p>The three collaborators split by responsibility so this one stays the thin, settings-fed
 * seam: {@link ClusterLabelStyling} resolves each label's colour and name (view off filter,
 * filter rules under one), {@link ClusterAnchorPlacement} runs the pure geometric search over
 * injected data, and {@link LabelAnchorSpecification} carries the tuning both read.
 *
 * <p>Apart from {@link kmu.maplayers.politicalmap.base.render.TerritoryBuilder} because the anchors
 * are an independent overlay, not
 * part of the production draw lists: they draw over the normal render and the debug
 * border-tracing overlay alike, so they cannot live inside either view's build. The overlay
 * list is owned by the terrain plugin and rebuilt in place here, whichever base view a rebuild
 * produced.
 */
public final class ClusterAnchorsBuilder {

    // Drives only; never instantiated.
    private ClusterAnchorsBuilder() {
    }

    // Rebuilds the cluster-label placements in place: clears the standing list, then -
    // only when the placements are needed - splits the owned systems into contiguous
    // clusters and fits one anchor to each. The placements feed two consumers: the
    // faction-name labels and the debug anchor overlay. Building whenever either is on
    // keeps them a single computation (an SSOT the labels and the overlay share), so the
    // search never runs twice; each consumer then draws only under its own toggle. Shared
    // by the full rebuild and the incremental refresh so an ownership change keeps the
    // placements in step with the fills and borders. Reads the toggles here (not at the
    // call sites) so all paths gate identically; the search's tuning is read here too, so
    // a settings change re-fits on the rebuild it triggers. The active view supplies each
    // bloc's label (which the fit sizes the boxes for) and the style classifier the label
    // colour follows, over the grouping snapshot the owner map was resolved under. Under a
    // filter the label styling follows the same shared decision the fills do - receding every
    // non-spotlit bloc, leaving the spotlit one full - and the synthetic spotlight keys resolve
    // to the selected bloc's name, since the view cannot name a synthetic id.
    public static void rebuildClusterAnchors(
            List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache,
            Map<String, DominantOwner> ownerBySystemId,
            SectorAPI sector,
            FactionPalette desaturationPalette,
            PoliticalMapView view,
            OwnershipGrouping grouping,
            boolean isFiltering,
            BlocStyleAdjustment recedeAdjustment,
            String selectedBlocId) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowNames()
                && !KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        // The agnostic clustering and border trace key by grouping id, so hand them each
        // system's bloc id; the owner map is still carried for the per-owner colour. Under a
        // filter that key is a synthetic spotlight key, so the solid and contested clusters
        // trace as their own territories exactly as the fills do.
        var groupKeyBySystemId = DominantOwner.mapFactionIdBySystemId(ownerBySystemId);
        var clusters = SystemClusters.findClusters(
                geometryCache.getCellEdgesBySystemId(),
                groupKeyBySystemId);
        // The anchor search traces the same cluster border the fills stroke, so it reads the
        // pass's frontier snapshot too: an open frontier pushes the ring out toward its dead
        // star, and the anchor must clip against that pushed ring to stay on the fill.
        var frontier = FrontierSettings.readFromLunaSettings(geometryCache.getSiteBySystemId());
        // The desaturation palette is handed in already resolved - off the production build's
        // drawables, or by the debug path from the same profile seam - so a desaturated bloc's
        // name matches its recolored fill and border exactly without re-reading the profile here.
        // The style decision every label follows is the same one the fills read
        // (BlocStyleResolver.resolveBlocStyleDecision), cached per bloc since its two label
        // consumers - the independent-style test and the adjustment - both read it: under a
        // filter it recedes every non-spotlit bloc and leaves the spotlit one full, so a receded
        // name matches its receded fill and a spotlit name stays full, the drift a filter opens.
        var styleDecisionByBlocId = ClusterLabelStyling.newBlocStyleDecisionResolver(
                isFiltering, view, grouping, recedeAdjustment);
        anchors.addAll(ClusterAnchorPlacement.computeClusterAnchors(
                clusters,
                geometryCache.getCellEdgesBySystemId(),
                geometryCache.getSiteBySystemId(),
                ownerBySystemId,
                groupKeyBySystemId,
                LabelAnchorSpecification.readFromLunaSettings(frontier),
                blocId -> styleDecisionByBlocId.apply(blocId).usesIndependentStyle(),
                blocId -> styleDecisionByBlocId.apply(blocId).adjustment(),
                desaturationPalette,
                ClusterLabelStyling.newNameEstimatorResolver(
                        sector, view, grouping, isFiltering, selectedBlocId)));
    }

    // The rebuild for a path with no owner map at hand - the debug border-tracing view,
    // which builds no production draw lists to borrow one from. Resolves ownership from
    // the sector itself, gated behind the toggle so the economy scan only runs while
    // someone is actually looking at the anchors.
    public static void rebuildClusterAnchorsFromSector(
            List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache,
            SectorAPI sector,
            PoliticalMapView view) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        // Sample the view's grouping once and resolve ownership under it, so the anchors
        // key off the same snapshot their names and colours are classified against.
        var grouping = view.resolveGrouping();
        // This path builds no drawables to borrow the palette from, so resolve it here - through
        // the same profile seam the theme reads, so the debug names desaturate exactly as production
        // does and the profile setting still has a single reader.
        var neutralColor = StarsectorFactionColors.resolveNeutralColor(sector);
        var desaturationPalette = MapPalettes.resolveDesaturationPalette(
                RenderStyleReader.readGlobalStyle().desaturationProfile(),
                sector,
                neutralColor);
        // The debug border-tracing path never filters - it resolves real dominant owners from the
        // sector - so it recedes nothing and names no synthetic spotlight key.
        rebuildClusterAnchors(
                anchors,
                geometryCache,
                SectorPolitics.resolveDominantOwnerBySystemId(sector, grouping),
                sector,
                desaturationPalette,
                view,
                grouping,
                false,
                BlocStyleAdjustment.NONE,
                null);
    }
}
