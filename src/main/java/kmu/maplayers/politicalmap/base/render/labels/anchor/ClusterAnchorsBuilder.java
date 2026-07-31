package kmu.maplayers.politicalmap.base.render.labels.anchor;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterAnchorPlacement;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.FilterSnapshot;
import kmu.maplayers.politicalmap.base.render.territories.ViewGrouping;
import kmu.settings.KmuLunaSettings;

import java.util.List;

/**
 * Drives the cluster-label placements: clusters the owned systems, reads the live tuning and
 * palette, wires the active view and any active filter into the per-bloc colour and name
 * resolvers, and hands it all to the pure {@link ClusterAnchorPlacement} search - then keeps
 * the resulting overlay list current in place.
 *
 * <p>The collaborators split by responsibility so this one stays the thin, settings-fed seam:
 * {@link ClusterLabelStyling} resolves each label's colour and name (view off filter, filter
 * rules under one), {@link ClusterAnchorPlacement} runs the pure geometric search over the
 * plain functions of a bloc id those resolvers hand it, and {@link LabelAnchorSpecification}
 * carries the search's tuning. This is the whole of what the political map contributes to a
 * label: everything from the resolved colour and name onward is layer-agnostic framework.
 *
 * <p>Kept apart from
 * {@link kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder} because the anchors
 * are an independent overlay, not part of the production draw lists: they draw over the normal
 * render and the debug border-tracing overlay alike, so they cannot live inside either view's
 * build. The overlay list is owned by the terrain plugin and rebuilt in place here, whichever
 * base view a rebuild produced.
 *
 * <p>Both entry points take a {@link ClusterLabelStylingSnapshot} rather than the holders,
 * palette, view, grouping and filter one by one, so the two paths differ only in where that
 * snapshot came from and a label can never be styled from two passes at once.
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
    // by the full rebuild and the incremental refresh so an holder change keeps the
    // placements in step with the fills and borders. Reads the toggles here (not at the
    // call sites) so all paths gate identically; the search's tuning is read here too, so
    // a settings change re-fits on the rebuild it triggers. The snapshot's view supplies each
    // bloc's label (which the fit sizes the boxes for) and the style classifier the label
    // colour follows, over the grouping snapshot the holder map was resolved under. Under a
    // filter the label styling follows the same shared decision the fills do - receding every
    // non-spotlit bloc, leaving the spotlit one full - and the synthetic spotlight keys resolve
    // to the selected bloc's name, since the view cannot name a synthetic id.
    public static void rebuildClusterAnchors(
            List<ClusterAnchor> anchors,
            CellGeometryCache geometryCache,
            SectorAPI sector,
            ClusterLabelStylingSnapshot styling) {

        anchors.clear();
        if (!NameFormatPreference.getSelectedNameFormat().areNamesDrawn()
                && !KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }

        // The agnostic clustering and border trace group the drawn cells, resolving each to
        // the system it draws as and that system to its bloc id; the holder map is still
        // carried for the per-holder colour. Under a filter that key is a synthetic spotlight
        // key, so the solid and contested clusters trace as their own territories exactly as
        // the fills do.
        var ownerBySystemId = styling.holderBySystemId();
        var cellGrouping = DominantHolder.mapCellGrouping(
            geometryCache.getSystemIdByCellId(),
            ownerBySystemId);
        var clusters = SystemClusters.findClusters(
            geometryCache.getCellEdgesByCellId(),
            cellGrouping);

        // The desaturation palette is handed in already resolved - off the production build's
        // drawables, or by the debug path from the same profile seam - so a desaturated bloc's
        // name matches its recolored fill and border exactly without re-reading the profile here.
        // The style decision every label follows is the same one the fills read
        // (BlocStyleResolver.resolveBlocStyleDecision), cached per bloc since the colour
        // resolver reads both halves of it - the independent-style test and the adjustment:
        // under a filter it recedes every non-spotlit bloc and leaves the spotlit one full, so a
        // receded name matches its receded fill and a spotlit name stays full, the drift a
        // filter opens.
        var filter = styling.filterSnapshot();
        var viewGrouping = styling.viewGrouping();
        var styleDecisionByBlocId = ClusterLabelStyling.newBlocStyleDecisionResolver(
            filter.isFiltering(),
            viewGrouping.view(),
            viewGrouping.grouping(),
            filter.recedeAdjustment());

        anchors.addAll(ClusterAnchorPlacement.computeClusterAnchors(
            clusters,
            geometryCache.getCellEdgesByCellId(),
            geometryCache.getSiteBySystemId(),
            cellGrouping,
            LabelAnchorSpecification.readFromLunaSettings(),
            ClusterLabelStyling.newLabelColorResolver(
                ownerBySystemId,
                BlocNameStyles.readFromLunaSettings(),
                styleDecisionByBlocId,
                styling.desaturationPalette()),
            ClusterLabelStyling.newNameEstimatorResolver(
                sector,
                viewGrouping.view(),
                viewGrouping.grouping(),
                filter.isFiltering(),
                filter.selectedBlocId())));
    }

    // The rebuild for a path with no holder map at hand - the debug border-tracing view,
    // which builds no production draw lists to borrow one from. Resolves holding from
    // the sector itself, gated behind the toggle so the economy scan only runs while
    // someone is actually looking at the anchors.
    public static void rebuildClusterAnchorsFromSector(
            List<ClusterAnchor> anchors,
            CellGeometryCache geometryCache,
            SectorAPI sector,
            PoliticalMapView view) {

        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }

        // Sample the view's grouping once and resolve holding under it, so the anchors
        // key off the same snapshot their names and colours are classified against.
        var grouping = view.resolveGrouping();

        // This path builds no drawables to borrow the palette from, so resolve it here - through
        // the same darkening seam the theme reads, so the debug names desaturate exactly as
        // production does and the setting still has a single reader.
        var desaturationPalette = MapPalettes.resolveDesaturationPalette(
            sector,
            RenderStyleReader.readGlobalStyle().desaturationDarkening());
                
        // The debug border-tracing path never filters - it resolves real dominant holders from the
        // sector - so it recedes nothing and names no synthetic spotlight key.
        rebuildClusterAnchors(
            anchors,
            geometryCache,
            sector,
            new ClusterLabelStylingSnapshot(
                SectorPolitics.resolveDominantHolderBySystemId(sector, grouping),
                desaturationPalette,
                new ViewGrouping(view, grouping),
                FilterSnapshot.unfiltered()));
    }
}
