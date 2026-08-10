package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.opengl.GlVertexRuns;
import kmlib.profiling.Timings;
import kmlib.starsector.factions.StarsectorFactionColours;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.render.clusters.HatchBuildDiagnostics;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import org.apache.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runs one full political-map rebuild: resolves who holds each system, reads the theme,
 * shapes the cached cells into merged clusters, and drives the two per-item builders over
 * every cell and every owned bloc to produce a fresh {@link PoliticalMapTerritories}.
 *
 * <p>The orchestration only - each stage's actual work belongs to a collaborator, and this
 * class's job is to sample every input exactly once so the whole pass keys off one snapshot.
 * That is what lets an incremental re-shape reuse those same builders
 * ({@link StyledCellBuilder} and {@link FactionTerritoryBuilder}) on a handful of cells and
 * get a result identical to a full rebuild.
 */
public final class TerritoryBuilder {
    private static final Logger LOG = Global.getLogger(TerritoryBuilder.class);

    // Builds only; never instantiated.
    private TerritoryBuilder() {
    }

    // Shapes the cached raw cells into merged clusters and partitions them into the
    // cluster-filled (owned) and per-cell (decivilised/uninhabited) draw lists, baking in each
    // cell's colours, opacities, and widths resolved from the current settings, then
    // flattens each to GL-ready vertex runs. Reads the settings once per category, not
    // per cell. The active view supplies the holder grouping the pass resolves under
    // and the style classifier each cell reads; both are retained on the territories so an
    // incremental re-shape classifies against the same view and grouping snapshot.
    public static PoliticalMapTerritories buildTerritories(
            CellGeometryCache geometryCache,
            SectorAPI sector,
            PoliticalMapView view) {

        var profiler = KmuProfiling.getProfiler();
        return profiler.measure("politicalMap.rebuildTerritories", () -> {

            // Sample the view's grouping once for the whole pass (the alliances view reads
            // Nexerelin), so every stage keys off one snapshot and the retained copy the
            // incremental re-shape reads matches the holding this build resolved.
            var grouping = view.resolveGrouping();

            // The spotlighted bloc, read once so the whole pass keys off one snapshot - the
            // holding provider (which keeps a spotlit bloc drawn wherever it is present), the
            // recede the rest of the sector takes, and the retained filter snapshot all resolve
            // from this one read, exactly like the grouping.
            var selectedBlocId = FilterSelection.getSelectedIdOf(view.getId());
            var isFiltering = selectedBlocId != null;

            // The politics scan walks the whole economy - the priciest content step -
            // so it is profiled and timed on its own, and the holder count logged
            // independent of the profiler's accumulated view. Under a filter it also reports
            // which spotlit systems are contested, since the whole spotlit footprint shares one
            // key and that set is the only record of the dominant/contested split.
            var politicsStart = System.nanoTime();
            var resolution = profiler.measure(
                "politicalMap.resolvePolitics",
                () -> view
                        .resolveHolderProvider()
                        .resolveHolder(sector, grouping, selectedBlocId));

            var ownerBySystemId = resolution.ownerBySystemId();
            var contestedSystemIds = resolution.contestedSystemIds();

            // The owned systems this resolution paints no fill for - held by their bloc but drawn
            // empty inside its one border. Empty for the faction/alliance and filter paths today;
            // the split reads it so a source that populates it needs no further wiring.
            var unfilledSystemIds = resolution.unfilledSystemIds();
            LOG.debug("Political map politics resolved; ownedSystems="
                + ownerBySystemId.size()
                + " filtering=" + isFiltering
                + " contested=" + contestedSystemIds.size()
                + " unfilled=" + unfilledSystemIds.size()
                + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

            // What stands in each system, read once for the whole pass. Independent of the
            // holding resolved above and deliberately so: the holding answers who this view
            // gives a system to, and a view whose rule admits only some factions - claims, where
            // vanilla lets only a territorial faction claim - leaves inhabited systems with no
            // holder. Only this read can tell those apart from empty space.
            //
            // Under the same reveal the holding resolved under, so a colony the dev toggle
            // admits to one is admitted to the other and the two cannot disagree about whether
            // a system holds anything.
            var inhabitedStart = System.nanoTime();
            var inhabitedSystemIds = profiler.measure(
                "politicalMap.findInhabited",
                () -> PoliticalMapInhabitation.readInhabitedSystemIds(sector));

            LOG.debug("Political map inhabitation scan; systems="
                + inhabitedSystemIds.size()
                + " took=" + Timings.formatMillis(System.nanoTime() - inhabitedStart));

            // Where the spotlit bloc is living outside anything this build attributed to it, so
            // the factionless cells over its own colonies are spared the recede. Asked only of the
            // inhabited systems the holding left out - on the faction and alliance views that is
            // the dead worlds alone, which no bloc lives in, so the read comes back empty for the
            // cost of the set arithmetic.
            var presenceStart = System.nanoTime();
            var spotlitPresenceSystemIds = profiler.measure(
                "politicalMap.findSpotlitPresence",
                () -> FilteredPolitics.findPresentSystemIds(
                    sector,
                    grouping,
                    selectedBlocId,
                    selectUnheldSystemIds(inhabitedSystemIds, ownerBySystemId)));

            LOG.debug("Political map spotlit presence scan; systems="
                + spotlitPresenceSystemIds.size()
                + " took=" + Timings.formatMillis(System.nanoTime() - presenceStart));

            // The whole theme - the global tier plus one style per category - read once here
            // through the single reader seam, plus the shared neutral colour and the desaturation
            // palette the profile resolves to. Held on the territories so the incremental refresh
            // re-shapes cells against the same snapshot this pass used.
            var renderStyle = RenderStyleReader.readRenderStyle();
            var neutralColour = StarsectorFactionColours.resolveNeutralColour(sector);

            // Stated once here, ahead of any geometry, because it does not vary across the bodies
            // this pass then cuts: it is the heading the per-body hatch lines below are read
            // under, and the one place their units are given.
            HatchBuildDiagnostics.logHatchSpecification(renderStyle.global().hatch());

            // The receded background desaturates to a uniform Independent-based grey, darkened by the
            // live setting so it sits below genuine independent-held space - a spotlit bloc, even
            // Independent at full strength, therefore reads distinctly against it.
            var desaturationPalette = MapPalettes.resolveDesaturationPalette(
                sector,
                renderStyle.global().desaturationDarkening());

            // The styling every non-spotlighted bloc recedes to, resolved once from the filter recede
            // toggles - the "rest of the sector" set, shared across both views under a filter; the
            // identity adjustment off filter, so a normal pass touches no bloc.
            var recedeAdjustment = isFiltering
                ? RecedePreferences.FILTER.resolveRecedeAdjustment()
                : ElementStyleAdjustment.NONE;

            var territories = new PoliticalMapTerritories(
                ownerBySystemId,
                inhabitedSystemIds,
                unfilledSystemIds,
                new MapStyling(renderStyle, neutralColour, desaturationPalette),
                new ViewGrouping(view, grouping),
                new FilterSnapshot(
                    selectedBlocId,
                    recedeAdjustment,
                    contestedSystemIds,
                    spotlitPresenceSystemIds));

            // Shape the raw cells into merged clusters once, holding-aware. The agnostic
            // geometry clusters by holder, so hand it each system's faction id as the
            // key. Cells consumed by the inset (fewer than three vertices left) drop out.
            var shapeStart = System.nanoTime();
            var cellGrouping = resolveCellGrouping(territories, geometryCache);
            var shapedCells = profiler.measure(
                "politicalMap.shapeCells",
                () -> CellShaper.shapeCells(
                    geometryCache.getCellEdgesByCellId(),
                    cellGrouping,
                    CellShaper.BORDER_INSET_DISTANCE));

            for (var entry : shapedCells.entrySet()) {

                var styled = StyledCellBuilder.buildStyledCellForSystem(
                    territories,
                    cellGrouping.resolveDrawnSystemIdOf(entry.getKey()),
                    entry.getValue());

                if (styled != null) {
                    territories.putStyledCell(
                        entry.getKey(),
                        styled,
                        entry.getValue().fillPolygon());
                }
            }
            // The clusters the cursor read resolves a hovered cell's whole territory through.
            // Derived here off the same keys the shaping just fused the cells by, so a highlighted
            // territory is exactly the one the map merged into a single cluster.
            territories.reindexClusters(
                geometryCache.getCellEdgesByCellId(),
                geometryCache.getSystemIdByCellId());

            // Each owned faction's territory: one cluster per cluster (traced across all
            // its cells so a multi-system cluster reads as one frontier), tessellated for
            // the fill and flattened for the border - the same shape for both. Built off
            // the same raw cells and holders the seams used, and profiled on its own since
            // chaining, smoothing, and tessellating every faction's outline is comparable
            // in cost to shaping the cells.
            profiler.measure(
                "politicalMap.buildFactionTerritories",
                () -> FactionTerritoryBuilder.buildAllFactionTerritories(
                    territories,
                    geometryCache));

            LOG.debug("Political map cells shaped; shaped="
                + shapedCells.size()
                + " styledCells=" + territories.getStyledCellByCellId().size()
                + " blocs=" + territories.getStyledClusterGroupByOwnerId().size()
                + " hatchSegments=" + countHatchSegments(territories)
                + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));

            return territories;
        });
    }

    // The inhabited systems this build resolved no holder for - every cell that will reach the
    // factionless classifier with something standing in it. The only systems a spotlit bloc's
    // presence can change anything for, since one it does hold already draws in its territory.
    private static Set<String> selectUnheldSystemIds(
            Set<String> inhabitedSystemIds,
            Map<String, DominantHolder> ownerBySystemId) {

        var unheldSystemIds = new LinkedHashSet<String>();

        for (var systemId : inhabitedSystemIds) {
            if (!ownerBySystemId.containsKey(systemId)) {
                unheldSystemIds.add(systemId);
            }
        }
        return unheldSystemIds;
    }

    // How many hatch strokes this pass baked, as a count of GL_LINES segments rather than of the
    // floats that pack them. Summed across every territory because which one carries the run
    // depends on the filter, and all but the spotlit bloc hatch nothing.
    private static int countHatchSegments(PoliticalMapTerritories territories) {
        var hatchSegments = 0;
        for (var clusterGroup : territories.getStyledClusterGroupByOwnerId().values()) {
            for (var cluster : clusterGroup.clusters()) {
                hatchSegments += cluster.hatchSegments().length / GlVertexRuns.FLOATS_PER_SEGMENT;
            }
        }
        return hatchSegments;
    }

    // The drawn cells' grouping this pass shapes and traces against: which system each cell draws
    // as, off the geometry cache, paired with each owned system's faction id. Resolved once per
    // pass so every stage groups the cells identically.
    private static CellGrouping resolveCellGrouping(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {
        return DominantHolder.mapCellGrouping(
            geometryCache.getSystemIdByCellId(),
            territories.getHolderBySystemId());
    }
}
