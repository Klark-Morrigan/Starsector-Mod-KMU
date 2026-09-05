package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.opengl.GlVertexRuns;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.Profiler;
import kmlib.starsector.factions.StarsectorFactionColours;
import kmlib.time.Timings;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.render.clusters.HatchBuildDiagnostics;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.holders.HolderResolution;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import org.apache.log4j.Logger;

import java.util.Set;
import java.util.function.Supplier;

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
 *
 * <p>It runs as four stages, each a method below: the holding, the occupancy asked of what that
 * holding left out, the paint scheme, and the shaping the first three are spent on. They are apart
 * because their order is the whole of the arrangement - a stage that ran before the one it reads
 * would key off a snapshot nothing else in the pass shares - and a single method long enough to
 * hide that order is a method whose reader has to reconstruct it.
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
    // per cell. The active view supplies the style classifier each cell reads, beside the
    // grouping the handed pass was opened under; both are retained on the territories so an
    // incremental re-shape classifies against the same view and grouping snapshot.
    //
    // The reading of the sector arrives rather than being opened here, so this build's walk of
    // each system is the same walk the geometry and the band bake either side of it make. It is
    // the rebuild's, and is discarded with it. The sidebar preferences arrive for the same reason:
    // the rebuild sampled them once when it decided it was owed, so reading them again here could
    // paint the map under a pick the decision never saw.
    public static PoliticalMapTerritories buildTerritories(
            CellGeometryCache geometryCache,
            HolderPass pass,
            PoliticalMapView view,
            ContentInputs contentInputs) {

        var profiler = ActiveProfiler.resolveProfiler();
        return profiler.measure("politicalMap.rebuildTerritories", () -> {

            // The three readings a build is made of, in this order because each needs the one
            // before it: who holds what, then who is in each system (asked of what the holding left
            // out), then the theme those two are painted in.
            var resolution = resolveHolding(profiler, pass, view, contentInputs);
            var occupancy = readOccupancyOf(profiler, pass, contentInputs, resolution);
            var styling = readMapStyling(pass.sector(), contentInputs);

            var territories = new PoliticalMapTerritories(
                occupancy,
                // The owned systems this resolution paints no fill for - held by their bloc but
                // drawn empty inside its one border. Empty for the faction/alliance and filter
                // paths today; the split reads it so a source that populates it needs no wiring.
                resolution.unfilledSystemIds(),
                styling,
                // The grouping is the pass's rather than a second sampling of the view's (the
                // alliances view reads Nexerelin), so the holding this build resolved and the
                // grouping the retained copy names cannot be two readings of it.
                new ViewGrouping(view, pass.grouping()),
                contentInputs,
                // The one thing this build derived about the spotlight: which of the spotlit bloc's
                // systems it holds without dominating.
                resolution.contestedSystemIds());

            shapeAndStyleCells(profiler, territories, geometryCache);

            return territories;
        });
    }

    // Who holds each system under this view, and - under a filter - which of the spotlit systems
    // are contested. The politics scan walks the whole economy, the priciest content step, so it is
    // profiled and timed on its own and its counts logged independent of the profiler's accumulated
    // view. The contested set is reported here because the whole spotlit footprint shares one key,
    // leaving that set the only record of the dominant/contested split.
    private static HolderResolution resolveHolding(
            Profiler profiler,
            HolderPass pass,
            PoliticalMapView view,
            ContentInputs contentInputs) {

        var politicsStart = System.nanoTime();
        var resolution = profiler.measure(
            "politicalMap.resolvePolitics",
            () -> view
                    .resolveHolderProvider()
                    .resolveHolder(pass, contentInputs.selectedBlocId()));

        LOG.debug("Political map politics resolved; ownedSystems="
            + resolution.ownerBySystemId().size()
            + " filtering=" + contentInputs.isFiltering()
            + " contested=" + resolution.contestedSystemIds().size()
            + " unfilled=" + resolution.unfilledSystemIds().size()
            + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

        return resolution;
    }

    // Who is in each system: the resolved holding, what stands there, and where the spotlit pick
    // lives in a system nobody holds. Assembled here rather than by the caller because the third
    // fact is asked of the first two - of the settled systems the holding left out - and folded
    // straight back in, so no pass ever holds a loose presence set the incremental refresh would
    // then have to be handed separately.
    private static SystemOccupancy readOccupancyOf(
            Profiler profiler,
            HolderPass pass,
            ContentInputs contentInputs,
            HolderResolution resolution) {

        // What stands in each system, read once for the whole pass. Independent of the holding and
        // deliberately so: the holding answers who this view gives a system to, and a view whose
        // rule admits only some markets - claims, for the several reasons base.politics.holders
        // sets out - leaves inhabited systems with no holder. Only this read tells those apart
        // from empty space.
        //
        // Taken off the pass the holding resolved through, so a colony the dev toggle admits to
        // one is admitted to the other and the two cannot disagree about whether a system holds
        // anything - and each system is read once for both rather than once apiece.
        var inhabitedSystemIds = measureSystemScan(
            profiler,
            "politicalMap.findInhabited",
            "inhabitation scan",
            () -> PoliticalMapInhabitation.readInhabitedSystemIds(pass));

        var occupancy = SystemOccupancy.createCopyOf(
            resolution.ownerBySystemId(),
            inhabitedSystemIds,
            Set.of());

        // Where the spotlit bloc is living outside anything this build attributed to it, so the
        // factionless cells over its own colonies are spared the recede. Asked only of the
        // inhabited systems the holding left out, and which view is painting decides whether that
        // set holds anything for it: the claims views leave a system unheld whenever nobody claims
        // it, so a bloc's own unclaimed colonies land here and this read is what spares their
        // cells. The faction and alliance views resolve holding through FilteredPolitics, which
        // already keys every system the bloc is present in and can be coloured for, so their
        // leftovers are systems it is absent from and the read comes back empty for the cost of
        // the set arithmetic.
        //
        // Asked of the same habitation the scan above classified by, so a cell spared here is
        // never one that scan called empty space.
        var spotlitPresenceSystemIds = measureSystemScan(
            profiler,
            "politicalMap.findSpotlitPresence",
            "spotlit presence scan",
            () -> FilteredPolitics.findPresentSystemIds(
                pass,
                contentInputs.selectedBlocId(),
                occupancy.selectUnheldSystemIdsAmong(inhabitedSystemIds)));

        for (var systemId : spotlitPresenceSystemIds) {
            occupancy.foldSpotlitPresenceOf(systemId, true);
        }
        return occupancy;
    }

    // The paint scheme this build styles every cell from: the whole theme read once through the
    // single reader seam, the sector's neutral shade, and the two palettes a spotlight separates
    // its subject from its backdrop with. Held on the territories so the incremental refresh
    // re-shapes cells against the same snapshot this pass used.
    private static MapStyling readMapStyling(SectorAPI sector, ContentInputs contentInputs) {

        var renderStyle = RenderStyleReader.readRenderStyle(
            contentInputs.isUninhabitedOutlineDrawn());

        var neutralColour = StarsectorFactionColours.resolveNeutralColour(sector);

        // Stated once here, ahead of any geometry, because it does not vary across the bodies this
        // pass then cuts: it is the heading the per-body hatch lines are read under, and the one
        // place their units are given.
        HatchBuildDiagnostics.logHatchSpecification(renderStyle.global().hatch());

        return new MapStyling(
            renderStyle,
            MapPalettes.resolveNeutralPalette(neutralColour),
            // The receded background desaturates to a uniform Independent-based grey, darkened by
            // the live setting so it sits below genuine independent-held space - a spotlit bloc,
            // even Independent at full strength, therefore reads distinctly against it.
            MapPalettes.resolveDesaturationPalette(
                sector,
                renderStyle.global().desaturationDarkening()),
            // The other end of that separation: the neutral a spared factionless cell paints in,
            // lifted toward white so it clears the greys the recede just sank. Resolved beside its
            // counterpart, both once per pass, so the two ends cannot be read from different
            // snapshots of the same two knobs.
            MapPalettes.resolvePresencePalette(
                neutralColour,
                renderStyle.global().presenceLightening()));
    }

    // Turns the cached raw cells into the two draw lists: each cell shaped against the holding and
    // styled, the clusters re-derived off the keys that shaping fused them by, and each bloc's
    // bodies traced across all of its cells.
    //
    // Written into the territories rather than returned, because the per-cell builder reads the
    // very model it fills - a cell's style is resolved from the holding, theme and spotlight
    // already retained on it, which is what makes an incremental re-shape of one cell identical to
    // this build's.
    private static void shapeAndStyleCells(
            Profiler profiler,
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {

        // Shape the raw cells into merged clusters once, holding-aware. The agnostic geometry
        // clusters by holder, so hand it each system's faction id as the key. Cells consumed by
        // the inset (fewer than three vertices left) drop out.
        var shapeStart = System.nanoTime();
        var cellGrouping = resolveCellGrouping(territories, geometryCache);
        var shapedCells = profiler.measure(
            "politicalMap.shapeCells",
            () -> CellShaper.shapeCells(
                geometryCache.getCellEdgesByCellId(),
                cellGrouping,
                CellShaper.BORDER_INSET_DISTANCE));

        // No band is laid here. A band keeps clear of the cluster names, and the names are fitted
        // after this pass - a name is placed inside the border these very cells trace - so bands
        // are baked in their own pass afterwards, over the shapes recorded below.
        for (var entry : shapedCells.entrySet()) {

            var styled = StyledCellBuilder.buildStyledCellForSystem(
                territories,
                cellGrouping.resolveDrawnSystemIdOf(entry.getKey()),
                entry.getValue());

            if (styled == null) {
                continue;
            }
            territories.putStyledCell(
                entry.getKey(),
                styled,
                entry.getValue().fillPolygon());
        }
        // The clusters the cursor read resolves a hovered cell's whole territory through. Derived
        // here off the same keys the shaping just fused the cells by, so a highlighted territory
        // is exactly the one the map merged into a single cluster.
        territories.reindexClusters(
            geometryCache.getCellEdgesByCellId(),
            geometryCache.getSystemIdByCellId());

        // Each owned faction's territory: one cluster per cluster (traced across all its cells so
        // a multi-system cluster reads as one frontier), tessellated for the fill and flattened
        // for the border - the same shape for both. Built off the same raw cells and holders the
        // seams used, and profiled on its own since chaining, smoothing, and tessellating every
        // faction's outline is comparable in cost to shaping the cells.
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
    }

    // One profiled sector scan yielding a set of system ids, timed and logged on its own. The two
    // such scans state their cost identically rather than each spelling out the clock, the profiler
    // key, and the log line - three chances for one of them to report itself differently from the
    // other. The holding resolve above keeps its own line, having four counts to report rather
    // than one.
    private static Set<String> measureSystemScan(
            Profiler profiler,
            String profileKey,
            String scanLabel,
            Supplier<Set<String>> scan) {

        var start = System.nanoTime();
        var systemIds = profiler.measure(profileKey, scan);

        LOG.debug("Political map " + scanLabel + "; systems="
            + systemIds.size()
            + " took=" + Timings.formatMillis(System.nanoTime() - start));

        return systemIds;
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
