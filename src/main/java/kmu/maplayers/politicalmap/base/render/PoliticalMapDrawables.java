package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.render.debug.DebugBorderTracingBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterLabelStylingSnapshot;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.ResolvedHolding;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * What the political map has built to draw, and how each part of it is rebuilt: one of the two
 * base views, the cluster-label placements drawn over either, and the name labels minted from them.
 *
 * <p>Apart from the cache that decides <em>when</em> to rebuild, because the two are different
 * questions. The cache reads revisions and settings to say whether anything is owed; this holds
 * the products and knows, for each, which builder makes it from which inputs. A rebuild is then
 * the cache naming the stages in order and this running them, and neither has to know the other's
 * bookkeeping.
 *
 * <p>Exactly one of the two base views is non-null once anything has been built: the debug
 * border-tracing overlay replaces the production draw lists outright rather than drawing over them.
 */
final class PoliticalMapDrawables {

    // The built draw lists plus the holding and style inputs an incremental re-shape needs.
    // Null until the first build.
    private PoliticalMapTerritories territories;

    // The debug border-tracing overlay, built instead of the territories above while the "debug
    // border tracing" dev toggle is on.
    private ClusterBorderStageOverlay borderStageOverlay;

    // The cluster-label placements and what they were fitted under, held here rather than by
    // either view above so they draw over whichever is live - turning border tracing on must not
    // hide them. Empty unless the names or the anchor overlay is on; they feed both. The two are
    // one value because a placement is only reusable as a pair with the rules it was sized under,
    // and this holder is what owns them across the rebuilds that reuse them.
    private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();

    // The cached faction-name labels, built from the placements above. Each owns a GL buffer, so
    // the builder disposes the standing strings whenever it rebuilds this list. Empty unless the
    // "show faction names" toggle is on.
    private final List<Label> factionLabels = new ArrayList<>();

    /** @return the built production draw lists, or null while the debug overlay has replaced them */
    public PoliticalMapTerritories getTerritories() {
        return territories;
    }

    /** @return the built debug border-tracing overlay, or null in the normal (non-debug) view */
    public ClusterBorderStageOverlay getBorderStageOverlay() {
        return borderStageOverlay;
    }

    /** @return the cluster-label placements, drawn over whichever base view is live */
    public List<ClusterAnchor> getClusterAnchors() {
        return standingAnchors.getAnchors();
    }

    /** @return the cached faction-name labels */
    public List<Label> getFactionLabels() {
        return factionLabels;
    }

    /**
     * @return whether the debug border-tracing overlay is the built view - the one branch the
     *         renderer needs to pick which base view to paint
     */
    public boolean isDebug() {
        return borderStageOverlay != null;
    }

    /**
     * @return whether neither view has been built yet, which is what makes a first frame owe a
     *         build whatever the revisions happen to say
     */
    public boolean hasNothingBuilt() {
        return territories == null && borderStageOverlay == null;
    }

    /**
     * @return whether a per-system holder change can be folded into the standing territories: the
     *         production view is built, and not under a filter - the incremental re-shape derives
     *         holders through the normal politics, which would overwrite the spotlit keys
     */
    public boolean canFoldHolderChanges() {
        return territories != null && !territories.isFiltering();
    }

    /**
     * The standing map as one value, for the incremental refresh that patches it in place.
     *
     * @param cellGeometry the cells the standing draw lists were shaped from
     * @return the four halves the refresh reads and writes
     */
    public StandingPoliticalMap toStandingMap(RevisedCellGeometry cellGeometry) {
        return new StandingPoliticalMap(territories, standingAnchors, factionLabels, cellGeometry);
    }

    /**
     * @return what the last rebuild left for the render to paint, in the terms of whichever view
     *         it built
     */
    public String describeBuiltCounts() {
        return borderStageOverlay != null
            ? "debugBaseLoops=" + borderStageOverlay.baseLoops().size()
            : "styledCells=" + territories.getStyledCellByCellKey().size();
    }

    /**
     * Guards the render path after a failed first build: a rebuild that threw before completing can
     * leave the draw lists null, which the renderer would dereference. An empty placeholder makes
     * the render a harmless no-op until a later frame's retry succeeds.
     *
     * @param view the view the placeholder is built under
     */
    public void ensureTerritoriesNonNull(PoliticalMapView view) {
        if (territories == null) {
            territories = PoliticalMapTerritories.createEmpty(view);
        }
    }

    /**
     * Releases everything built, when the machinery holding it goes.
     *
     * <p>The labels' GL buffers are released as part of it: they are freed at the moment the lists
     * are dropped rather than left to LazyLib's finalizer sweep. The placements go with the record
     * of what they were fitted under, since a statement of the rules this sector's labels were made
     * under must not outlive the labels.
     */
    public void disposeAll() {

        LabelsBuilder.disposeAll(factionLabels);
        factionLabels.clear();

        standingAnchors.discardAnchors();
        territories = null;
        borderStageOverlay = null;
    }

    /**
     * Builds the debug border-tracing view, which replaces the production draw lists outright.
     *
     * <p>It reads holding from the sector rather than through the rebuild's own reading, and
     * deliberately: the overlay is gated behind a dev toggle and builds no draw lists for the
     * anchors to borrow a holder map from, so what it costs is paid only while somebody is looking
     * at it.
     *
     * @param cellGeometry  the cells to trace, carrying the cut they stand at
     * @param sector        the sector whose holding the overlay reads
     * @param view          the view the overlay is traced under
     * @param contentInputs the sidebar preferences the rebuild sampled
     */
    public void rebuildBorderTracingOverlay(
            RevisedCellGeometry cellGeometry,
            SectorAPI sector,
            PoliticalMapView view,
            ContentInputs contentInputs) {

        borderStageOverlay = DebugBorderTracingBuilder.buildDebugDrawables(
            cellGeometry.cells(),
            sector,
            contentInputs);

        territories = null;

        ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
            standingAnchors,
            cellGeometry,
            sector,
            view,
            contentInputs);
    }

    /**
     * Builds the production view's three stages, driven off the one reading of the sector the
     * rebuild opened: the fills, the names fitted inside the borders they trace, and the bands laid
     * around wherever those names ended up.
     *
     * <p>The grouping the pass was opened under is the view's, sampled once, which is the condition
     * the bake shares this reading on: a pass folded by another grouping would plan bands against
     * blocs the fills never drew.
     *
     * @param cellGeometry  the cells to shape, carrying the cut they stand at
     * @param pass          the rebuild's one reading of the sector
     * @param view          the view the territories are built under
     * @param contentInputs the sidebar preferences the rebuild sampled
     * @param holding       who holds what and who is where, resolved off the pass by this rebuild
     *                      or kept from the one before it - which of the two is the caller's call,
     *                      being a question about what moved since
     */
    public void rebuildTerritoriesAndBands(
            RevisedCellGeometry cellGeometry,
            HolderPass pass,
            PoliticalMapView view,
            ContentInputs contentInputs,
            ResolvedHolding holding) {

        territories = TerritoryBuilder.buildTerritories(
            cellGeometry.cells(),
            pass,
            view,
            contentInputs,
            holding);
        borderStageOverlay = null;

        // The cells go over carrying the revision they stand at rather than the live
        // signal read at the top - the two agree here, and only the pair is true of the
        // geometry in hand. The standing placements go in whole beside it, which is what
        // lets the rebuild keep the ones whose clusters this pass has not moved.
        ClusterAnchorsBuilder.rebuildClusterAnchors(
            standingAnchors,
            cellGeometry,
            pass.sector(),
            ClusterLabelStylingSnapshot.resolveFrom(territories));

        // The bands come last, after the names have places, because they are laid around
        // them: a band is cut by the room the names take, so baking one before the fit
        // would leave it running under a word rather than clear of it. What the fit
        // reports about the names it moved is dropped here: every cell was just rebuilt
        // from nothing, so all of them owe a band whatever the names did.
        //
        // Baked through this rebuild's own reading rather than one of its own: the bake runs in
        // the same frame the fills were painted in, so a fresh reading could only report the
        // same sector at the cost of walking it again.
        CellRibbonsBaker
            .createForPass(
                territories,
                cellGeometry.cells(),
                pass,
                standingAnchors.getAnchors())
            .bakeAllCellRibbons();
    }

    /**
     * Mints the name labels from the placements just rebuilt, keeping them in step with the fills
     * and borders and reusing the one placement search both consumers share.
     *
     * @param areNamesDrawn whether names are showing at all - the rebuild's own sampling of the
     *                      preference, so what is minted matches what was fitted
     */
    public void rebuildLabels(boolean areNamesDrawn) {
        LabelsBuilder.rebuildLabels(factionLabels, standingAnchors.getAnchors(), areNamesDrawn);
    }
}
