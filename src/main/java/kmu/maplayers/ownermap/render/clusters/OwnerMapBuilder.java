package kmu.maplayers.ownermap.render.clusters;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileScope;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.Profiler;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.profiling.RebuildStepTerms;
import kmu.maplayers.base.render.clusters.HatchBuildDiagnostics;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;

import java.util.Map;

/**
 * Runs one full owner-map rebuild: asks the layer's source who owns each system, reads the theme,
 * shapes the cached cells into merged clusters, and drives the two per-item builders over
 * every cell and every owned bloc to produce a fresh {@link OwnerMapClusters}.
 *
 * <p>The orchestration only - each stage's actual work belongs to a collaborator, and this
 * class's job is to sample every input exactly once so the whole pass keys off one snapshot.
 * That is what lets an incremental re-shape reuse those same builders
 * ({@link PaintedCellBuilder} and {@link ClusterGroupBuilder}) on a handful of cells and
 * get a result identical to a full rebuild.
 *
 * <p>Two entry points, because a rebuild has two halves that go stale for different reasons. The
 * owners - who owns what, who is where - are a reading of the sector, and are owed again only when
 * the sector or the rule reading it moved. The build - the paint scheme and the shaping it is spent
 * on - is owed on any style pick as well. Resolved apart from built, a rebuild a style pick owes is
 * handed the owners the last one resolved rather than asking the source for an answer it already
 * has. Which of the two is stale is the caller's to decide: this resolves what it is asked to and
 * builds from what it is handed.
 *
 * <p>Nothing here reads a colony. What the owners are made of is the layer's source's business,
 * reached over the walk the rebuild opened; this sees only the answer.
 *
 * <p>Each stage is a method below, apart because their order is the whole of the arrangement - a
 * stage that ran before the one it reads would key off a snapshot nothing else in the pass shares -
 * and a single method long enough to hide that order is a method whose reader has to reconstruct
 * it.
 */
public final class OwnerMapBuilder {

    // The shaping and everything spent on the shapes, logged as one line on every call: a rebuild
    // happens when something changed rather than on a clock, and the two profiled steps below it
    // are parts of this call rather than the whole of it.
    private static final ProfileSection SHAPE_AND_STYLE_SECTION = ProfileSection.registerSection(
        "ownerMap.shapeAndStyleCells", RebuildStepTerms.LOGGED_EVERY_CALL);

    // The whole of each half and the two steps of the shaping worth a row of their own, read in the
    // report rather than the log: what they cost is read against the stage around them. The owners
    // have a row of their own so a rebuild that carried them over reads as missing that row, rather
    // than as a source's scans that each happened to cost nothing - and whatever the source profiles
    // of its own lands beneath it.
    private static final ProfileSection RESOLVE_HOLDING_SECTION =
        ProfileSection.registerSection("ownerMap.resolveHolding");

    private static final ProfileSection REBUILD_SECTION =
        ProfileSection.registerSection("ownerMap.rebuildClusters");

    private static final ProfileSection SHAPE_CELLS_SECTION =
        ProfileSection.registerSection("ownerMap.shapeCells");

    private static final ProfileSection BUILD_CLUSTER_GROUPS_SECTION =
        ProfileSection.registerSection("ownerMap.buildClusterGroups");

    // Builds only; never instantiated.
    private OwnerMapBuilder() {
    }

    /**
     * Asks the layer's source who owns what and who is where: the half of a rebuild that goes stale
     * only when the sector moves or the rule reading it does, and so the half a rebuild owed by a
     * style pick alone is handed rather than made to repeat.
     *
     * <p>Measured on a row of its own, under which whatever the source profiles lands, so a rebuild
     * that kept its owners reads as missing this row.
     *
     * @param source        the view's owner source for this rebuild
     * @param walk          the rebuild's one walk of the sector, which the source opens whatever
     *                      reading it needs over
     * @param contentInputs the sidebar preferences the rebuild sampled, of which only the spotlight
     *                      reaches the owners
     * @return what the source found, ready to be built from - now or by a later rebuild that owes no
     *         new reading
     */
    public static ResolvedOwners resolveOwners(
            OwnerSource source,
            SectorWalk walk,
            ContentInputs contentInputs) {

        try (var holdingScope = ActiveProfiler.resolveProfiler().open(RESOLVE_HOLDING_SECTION)) {
            return source.resolveOwners(walk, contentInputs.selectedBlocId());
        }
    }

    // Shapes the cached raw cells into merged clusters and partitions them into the
    // cluster-filled (owned) and per-cell (decivilised/uninhabited) draw lists, baking in each
    // cell's colours, opacities, and widths resolved from the current settings, then
    // flattens each to GL-ready vertex runs. Reads the settings once per category, not
    // per cell. The view's reading arrives with it, retained on the clusters so an incremental
    // re-shape classifies against the same view, reading and source.
    //
    // The reading arrives rather than being resolved here because the owners were resolved through
    // the source that came with it, both over one sampling of the view's live inputs - a reading
    // resolved again here could name and colour a fold the owners were never resolved under. The
    // sidebar preferences arrive for a like reason: the rebuild sampled them once when it decided it
    // was owed, so reading them again here could paint the map under a pick the decision never saw.
    // The owners arrive for a third: they may be the answer a previous rebuild got, kept because
    // nothing they read has moved since.
    public static OwnerMapClusters buildClusters(
            CellGeometryCache geometryCache,
            ViewReading viewReading,
            ContentInputs contentInputs,
            ResolvedOwners owners) {

        var profiler = ActiveProfiler.resolveProfiler();

        try (var rebuildScope = profiler.open(REBUILD_SECTION)) {

            var styling = readMapStyling(
                viewReading.view().resolveCategories(),
                viewReading.reading(),
                contentInputs);

            var clusters = new OwnerMapClusters(
                // Copied out of the owners rather than adopted: the incremental refresh folds
                // into what the clusters hold, and owners handed to a later rebuild have to still
                // say what they said.
                SystemOccupancy.createCopyOf(
                    owners.ownerBySystemKey(),
                    owners.inhabitedSystemKeys(),
                    owners.spotlitPresenceSystemKeys()),
                new OwnerMapBuildInputs(
                    styling,
                    viewReading,
                    contentInputs,
                    // The owned systems this resolution paints no fill for - owned for border and
                    // label but drawn empty inside the one frontier. Filled only by a source that
                    // extends what an owner holds with systems it does not, and empty otherwise.
                    owners.unfilledSystemKeys(),
                    // The one thing this build derived about the spotlight: which of the spotlit
                    // owner's systems it is present in without winning.
                    owners.contestedSystemKeys()));

            shapeAndStyleCells(profiler, clusters, geometryCache);

            return clusters;
        }
    }

    // The paint scheme this build styles every cell from: the whole theme read once through the
    // single reader seam for the layer's own categories, the reading's unowned shade, and the two
    // palettes a spotlight separates its subject from its backdrop with. Held on the clusters so the
    // incremental refresh re-shapes cells against the same snapshot this pass used.
    private static MapStyling readMapStyling(
            OwnerCategories categories,
            OwnerReading reading,
            ContentInputs contentInputs) {

        var renderStyle = RenderStyleReader.readRenderStyle(categories, contentInputs);

        var neutralColour = reading.resolveUnownedColour();

        // Stated once here, ahead of any geometry, because it does not vary across the bodies this
        // pass then cuts: it is the heading the per-body hatch lines are read under, and the one
        // place their units are given.
        HatchBuildDiagnostics.logHatchSpecification(renderStyle.global().hatch());

        return new MapStyling(
            renderStyle,
            categories,
            MapPalettes.resolveNeutralPalette(neutralColour),
            // The receded background desaturates to one uniform palette, the reading's recede
            // shades darkened by the live setting so it sits below the quieter owners' own space -
            // a spotlit owner at full strength therefore reads distinctly against it.
            MapPalettes.resolveDesaturationPalette(
                reading.resolveRecedePalette(),
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
    // Written into the clusters rather than returned, because the per-cell builder reads the
    // very model it fills - a cell's style is resolved from the holding, theme and spotlight
    // already retained on it, which is what makes an incremental re-shape of one cell identical to
    // this build's.
    private static void shapeAndStyleCells(
            Profiler profiler,
            OwnerMapClusters clusters,
            CellGeometryCache geometryCache) {

        try (var shapeScope = profiler.open(SHAPE_AND_STYLE_SECTION)) {
            shapeAndStyleCellsInScope(profiler, clusters, geometryCache, shapeScope);
        }
    }

    // The shaping itself, reporting onto the scope above it. The two steps it profiles separately
    // are parts of this call, so their spans and whatever they counted are inside its own.
    private static void shapeAndStyleCellsInScope(
            Profiler profiler,
            OwnerMapClusters clusters,
            CellGeometryCache geometryCache,
            ProfileScope shapeScope) {

        // Shape the raw cells into merged clusters once, holding-aware. The agnostic geometry
        // clusters by owner, so hand it each system's owner ID as the key. Cells consumed by
        // the inset (fewer than three vertices left) drop out.
        var cellGrouping = clusters.resolveCellGroupingOver(
            geometryCache.getSystemKeyByCellKey());

        var shapedCells = shapeCells(profiler, geometryCache, cellGrouping);

        // No band is laid here. A band keeps clear of the cluster names, and the names are fitted
        // after this pass - a name is placed inside the border these very cells trace - so bands
        // are baked in their own pass afterwards, over the shapes recorded below.
        for (var entry : shapedCells.entrySet()) {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                clusters,
                cellGrouping.resolveDrawnSystemKeyOf(entry.getKey()),
                entry.getValue());

            if (painted == null) {
                continue;
            }
            clusters.getPaintedCells().putPaintedCell(entry.getKey(), painted);
        }
        // The clusters the cursor read resolves a hovered cell's whole cluster group through. Derived
        // here off the same keys the shaping just fused the cells by, so a highlighted cluster group
        // is exactly the one the map merged into a single cluster.
        clusters.reindexClusters(
            geometryCache.getCellEdgesByCellKey(),
            geometryCache.getSystemKeyByCellKey());

        // Each owned faction's cluster group: one entry per cluster (traced across all its cells so
        // a multi-system cluster reads as one frontier), tessellated for the fill and flattened
        // for the border - the same shape for both. Built off the same raw cells and holders the
        // seams used, and profiled on its own since chaining, smoothing, and tessellating every
        // faction's outline is comparable in cost to shaping the cells.
        try (var clusterGroupsScope = profiler.open(BUILD_CLUSTER_GROUPS_SECTION)) {
            ClusterGroupBuilder.buildAllClusterGroups(
                clusters,
                geometryCache,
                cellGrouping);
        }

        // The cells this pass shaped are what its duration is read against. What became of them -
        // how many were styled, into how many blocs - is a fact about this one call, and the hatch
        // strokes it cut reach the row from where they were cut rather than being summed back out
        // of what was built.
        shapeScope.addCount(MapBuildCounters.CELLS, shapedCells.size());
        shapeScope.tagCall("styled=" + clusters.getStyledCellByCellKey().size()
            + " blocs=" + clusters.getStyledClusterGroupByOwnerId().size());
    }

    // The cell shaping as a row of its own: fusing same-owner cells and insetting each is comparable
    // in cost to tracing the clusters after it, and a reader wants the two apart.
    private static Map<SystemKey, ShapedCell> shapeCells(
            Profiler profiler,
            CellGeometryCache geometryCache,
            CellGrouping cellGrouping) {

        try (var shapeScope = profiler.open(SHAPE_CELLS_SECTION)) {
            return CellShaper.shapeCells(
                geometryCache.getCellEdgesByCellKey(),
                cellGrouping,
                EdgeInset.asTheMapDraws());
        }
    }

}
