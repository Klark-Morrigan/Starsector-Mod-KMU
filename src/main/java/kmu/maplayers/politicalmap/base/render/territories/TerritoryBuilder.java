package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileScope;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.Profiler;
import kmlib.starsector.factions.StarsectorFactionColours;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.profiling.RebuildStepTerms;
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

import java.util.Map;
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
 * <p>Two entry points, because a rebuild has two halves that go stale for different reasons. The
 * holding - who holds what, who is where - is a reading of the sector, and is owed again only when
 * the sector or the rule reading it moved. The build - the paint scheme and the shaping it is spent
 * on - is owed on any style pick as well. Resolved apart from built, a rebuild a style pick owes is
 * handed the holding the last one resolved rather than walking the economy for an answer it already
 * has. Which of the two is stale is the caller's to decide: this resolves what it is asked to and
 * builds from what it is handed.
 *
 * <p>Each stage is a method below, apart because their order is the whole of the arrangement - a
 * stage that ran before the one it reads would key off a snapshot nothing else in the pass shares -
 * and a single method long enough to hide that order is a method whose reader has to reconstruct
 * it.
 */
public final class TerritoryBuilder {

    // The four stages that used to time and report themselves by hand. Each writes its line on
    // every call: a rebuild happens when something changed rather than on a clock, so the trace a
    // reader follows through the log wants every one of them, fast ones included.
    private static final ProfileSection RESOLVE_POLITICS_SECTION = ProfileSection.registerSection(
        "politicalMap.resolvePolitics", RebuildStepTerms.LOGGED_EVERY_CALL);

    private static final ProfileSection FIND_INHABITED_SECTION = ProfileSection.registerSection(
        "politicalMap.findInhabited", RebuildStepTerms.LOGGED_EVERY_CALL);

    private static final ProfileSection FIND_SPOTLIT_PRESENCE_SECTION =
        ProfileSection.registerSection(
            "politicalMap.findSpotlitPresence", RebuildStepTerms.LOGGED_EVERY_CALL);

    // The shaping and everything spent on the shapes, which is what the hand-written line covered:
    // the two profiled steps below it are parts of this call rather than the whole of it.
    private static final ProfileSection SHAPE_AND_STYLE_SECTION = ProfileSection.registerSection(
        "politicalMap.shapeAndStyleCells", RebuildStepTerms.LOGGED_EVERY_CALL);

    // The whole of each half and the two steps of the shaping worth a row of their own, read in the
    // report rather than the log: what they cost is read against the stage around them. The holding
    // has a row of its own so a rebuild that carried it over reads as missing that row, rather than
    // as three scans that each happened to cost nothing.
    private static final ProfileSection RESOLVE_HOLDING_SECTION =
        ProfileSection.registerSection("politicalMap.resolveHolding");

    private static final ProfileSection REBUILD_SECTION =
        ProfileSection.registerSection("politicalMap.rebuildTerritories");

    private static final ProfileSection SHAPE_CELLS_SECTION =
        ProfileSection.registerSection("politicalMap.shapeCells");

    private static final ProfileSection BUILD_FACTION_TERRITORIES_SECTION =
        ProfileSection.registerSection("politicalMap.buildFactionTerritories");

    // Builds only; never instantiated.
    private TerritoryBuilder() {
    }

    /**
     * Reads the sector for who holds what and who is where: the half of a rebuild that goes stale
     * only when the sector moves or the rule reading it does, and so the half a rebuild owed by a
     * style pick alone is handed rather than made to repeat.
     *
     * <p>Three readings, in this order because each is asked of what the one before left: who holds
     * each system, then what stands in each, then where the spotlit pick lives among the settled
     * systems nobody holds. All three off the one handed pass, so each system is walked once for
     * the three and the answers cannot disagree about a colony the dev toggle admits.
     *
     * @param pass          the rebuild's one reading of the sector, opened under the view's grouping
     * @param view          the view whose rule resolves holding
     * @param contentInputs the sidebar preferences the rebuild sampled, of which only the spotlight
     *                      reaches the holding
     * @return what the three readings found, ready to be built from - now or by a later rebuild
     *         that owes no new reading
     */
    public static ResolvedHolding resolveHolding(
            HolderPass pass,
            PoliticalMapView view,
            ContentInputs contentInputs) {

        var profiler = ActiveProfiler.resolveProfiler();

        try (var holdingScope = profiler.open(RESOLVE_HOLDING_SECTION)) {

            var resolution = resolveHolders(profiler, pass, view, contentInputs);

            // What stands in each system, read once for the whole pass. Independent of the holding
            // and deliberately so: the holding answers who this view gives a system to, and a view
            // whose rule admits only some markets - claims, for the several reasons
            // base.politics.holders sets out - leaves inhabited systems with no holder. Only this
            // read tells those apart from empty space.
            var inhabitedSystemKeys = measureSystemScan(
                profiler,
                FIND_INHABITED_SECTION,
                () -> PoliticalMapInhabitation.readInhabitedSystemKeys(pass));

            // Where the spotlit bloc is living outside anything this build attributed to it, so
            // the factionless cells over its own colonies are spared the recede. Asked only of the
            // inhabited systems the holding left out, and which view is painting decides whether
            // that set holds anything for it: the claims views leave a system unheld whenever
            // nobody claims it, so a bloc's own unclaimed colonies land here and this read is what
            // spares their cells. The faction and alliance views resolve holding through
            // FilteredPolitics, which already keys every system the bloc is present in and can be
            // coloured for, so their leftovers are systems it is absent from and the read comes
            // back empty for the cost of the set arithmetic.
            //
            // Asked of the same habitation the scan above classified by, so a cell spared here is
            // never one that scan called empty space.
            var spotlitPresenceSystemKeys = measureSystemScan(
                profiler,
                FIND_SPOTLIT_PRESENCE_SECTION,
                () -> FilteredPolitics.findPresentSystemKeys(
                    pass,
                    contentInputs.selectedBlocId(),
                    selectUnheldSystemKeysAmong(resolution, inhabitedSystemKeys)));

            return new ResolvedHolding(resolution, inhabitedSystemKeys, spotlitPresenceSystemKeys);
        }
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
    // paint the map under a pick the decision never saw. The holding arrives for a third: it may
    // be the reading a previous rebuild made, kept because nothing it read has moved since.
    public static PoliticalMapTerritories buildTerritories(
            CellGeometryCache geometryCache,
            HolderPass pass,
            PoliticalMapView view,
            ContentInputs contentInputs,
            ResolvedHolding holding) {

        var profiler = ActiveProfiler.resolveProfiler();

        try (var rebuildScope = profiler.open(REBUILD_SECTION)) {

            var resolution = holding.resolution();
            var styling = readMapStyling(pass.sector(), contentInputs);

            var territories = new PoliticalMapTerritories(
                // Copied out of the holding rather than adopted: the incremental refresh folds
                // into what the territories hold, and a holding handed to a later rebuild has to
                // still say what it said.
                SystemOccupancy.createCopyOf(
                    resolution.ownerBySystemKey(),
                    holding.inhabitedSystemKeys(),
                    holding.spotlitPresenceSystemKeys()),
                // The owned systems this resolution paints no fill for - held by their bloc but
                // drawn empty inside its one border. Empty for the faction/alliance and filter
                // paths today; the split reads it so a source that populates it needs no wiring.
                resolution.unfilledSystemKeys(),
                styling,
                // The grouping is the pass's rather than a second sampling of the view's (the
                // alliances view reads Nexerelin), so the holding this build resolved and the
                // grouping the retained copy names cannot be two readings of it.
                new ViewGrouping(view, pass.grouping()),
                contentInputs,
                // The one thing this build derived about the spotlight: which of the spotlit bloc's
                // systems it holds without dominating.
                resolution.contestedSystemKeys());

            shapeAndStyleCells(profiler, territories, geometryCache);

            return territories;
        }
    }

    // Who holds each system under this view, and - under a filter - which of the spotlit systems
    // are contested. The politics scan walks the whole economy, the priciest content step, so it is
    // profiled on its own and names what it resolved on the call, which is the reading its duration
    // has to be judged against. The contested set is reported here because the whole spotlit
    // footprint shares one key, leaving that set the only record of the dominant/contested split.
    private static HolderResolution resolveHolders(
            Profiler profiler,
            HolderPass pass,
            PoliticalMapView view,
            ContentInputs contentInputs) {

        try (var politicsScope = profiler.open(RESOLVE_POLITICS_SECTION)) {

            var resolution = view
                .resolveHolderProvider()
                .resolveHolder(pass, contentInputs.selectedBlocId());

            politicsScope.tagCall("owned=" + resolution.ownerBySystemKey().size()
                + " filtering=" + contentInputs.isFiltering()
                + " contested=" + resolution.contestedSystemKeys().size()
                + " unfilled=" + resolution.unfilledSystemKeys().size());

            return resolution;
        }
    }

    // The settled systems the holding gave to nobody, which is the only place a spotlit pick can
    // be living unattributed. Asked through the occupancy's own rule rather than restated here, so
    // a full build and an incremental refresh cannot disagree about what "unheld" means.
    private static Set<SystemKey> selectUnheldSystemKeysAmong(
            HolderResolution resolution,
            Set<SystemKey> inhabitedSystemKeys) {

        return SystemOccupancy
            .createCopyOf(resolution.ownerBySystemKey(), inhabitedSystemKeys, Set.of())
            .selectUnheldSystemKeysAmong(inhabitedSystemKeys);
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

        try (var shapeScope = profiler.open(SHAPE_AND_STYLE_SECTION)) {
            shapeAndStyleCellsInScope(profiler, territories, geometryCache, shapeScope);
        }
    }

    // The shaping itself, reporting onto the scope above it. The two steps it profiles separately
    // are parts of this call, so their spans and whatever they counted are inside its own.
    private static void shapeAndStyleCellsInScope(
            Profiler profiler,
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            ProfileScope shapeScope) {

        // Shape the raw cells into merged clusters once, holding-aware. The agnostic geometry
        // clusters by holder, so hand it each system's faction id as the key. Cells consumed by
        // the inset (fewer than three vertices left) drop out.
        var cellGrouping = resolveCellGrouping(territories, geometryCache);
        var shapedCells = shapeCells(profiler, geometryCache, cellGrouping);

        // No band is laid here. A band keeps clear of the cluster names, and the names are fitted
        // after this pass - a name is placed inside the border these very cells trace - so bands
        // are baked in their own pass afterwards, over the shapes recorded below.
        for (var entry : shapedCells.entrySet()) {

            var styled = StyledCellBuilder.buildStyledCellForSystem(
                territories,
                cellGrouping.resolveDrawnSystemKeyOf(entry.getKey()),
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
            geometryCache.getCellEdgesByCellKey(),
            geometryCache.getSystemKeyByCellKey());

        // Each owned faction's territory: one cluster per cluster (traced across all its cells so
        // a multi-system cluster reads as one frontier), tessellated for the fill and flattened
        // for the border - the same shape for both. Built off the same raw cells and holders the
        // seams used, and profiled on its own since chaining, smoothing, and tessellating every
        // faction's outline is comparable in cost to shaping the cells.
        try (var territoriesScope = profiler.open(BUILD_FACTION_TERRITORIES_SECTION)) {
            FactionTerritoryBuilder.buildAllFactionTerritories(territories, geometryCache);
        }

        // The cells this pass shaped are what its duration is read against. What became of them -
        // how many were styled, into how many blocs - is a fact about this one call, and the hatch
        // strokes it cut reach the row from where they were cut rather than being summed back out
        // of what was built.
        shapeScope.addCount(MapBuildCounters.CELLS, shapedCells.size());
        shapeScope.tagCall("styled=" + territories.getStyledCellByCellKey().size()
            + " blocs=" + territories.getStyledClusterGroupByOwnerId().size());
    }

    // The cell shaping as a row of its own: fusing same-owner cells and insetting each is comparable
    // in cost to tracing the territories after it, and a reader wants the two apart.
    private static Map<SystemKey, ShapedCell> shapeCells(
            Profiler profiler,
            CellGeometryCache geometryCache,
            CellGrouping cellGrouping) {

        try (var shapeScope = profiler.open(SHAPE_CELLS_SECTION)) {
            return CellShaper.shapeCells(
                geometryCache.getCellEdgesByCellKey(),
                cellGrouping,
                CellShaper.BORDER_INSET_DISTANCE);
        }
    }

    // One profiled sector scan yielding a set of system keys, naming what it selected on the call.
    // The two such scans report identically rather than each spelling out a section and a reading
    // of its own - two chances for one of them to state its cost differently from the other.
    private static Set<SystemKey> measureSystemScan(
            Profiler profiler,
            ProfileSection section,
            Supplier<Set<SystemKey>> scan) {

        try (var scanScope = profiler.open(section)) {

            var systemKeys = scan.get();

            // What it selected rather than what it examined: the systems it went over are counted
            // by the readers it scans through, and appear on this row by the roll-up alone.
            scanScope.tagCall("systems=" + systemKeys.size());

            return systemKeys;
        }
    }

    // The drawn cells' grouping this pass shapes and traces against: which system each cell draws
    // as, off the geometry cache, paired with each owned system's faction id. Resolved once per
    // pass so every stage groups the cells identically.
    private static CellGrouping resolveCellGrouping(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {
        return DominantHolder.mapCellGrouping(
            geometryCache.getSystemKeyByCellKey(),
            territories.getHolderBySystemKey());
    }
}
