package kmu.maplayers.politicalmap.base.render.labels.anchor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.solving.Bisection;
import kmlib.profiling.Timings;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterAnchorPlacement;
import kmu.maplayers.base.labels.anchor.ClusterIdentity;
import kmu.maplayers.base.labels.anchor.ClusterLabelResolvers;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
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

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p>Both also take the caller's {@link StandingClusterAnchors} and leave their own pass in it,
 * rather than reading a list and answering an {@link AnchorFitFingerprint} for it. The pair is
 * the caller's and not kept here, because a fingerprint parked in a static would describe
 * whichever path ran last - exactly the disagreement a second entry point can cause.
 *
 * <p>That pair is what makes a rebuild partial. The standing pair is this rebuild's only record
 * of the previous pass, so it is indexed by the cluster each placement names before this pass
 * replaces it, and the search carries a placement over rather than searching its cluster again.
 * The gate on that reuse is the fingerprint, decided here rather than per cluster: the tuning and
 * the cell geometry each move every fit at once - the keep-out sites the boxes are trimmed clear
 * of are the whole sector's, so a system appearing anywhere moves fits no membership change would
 * touch - and neither shows up in any one cluster's identity. When the two do not match, nothing
 * is offered and the rebuild is total, which is what it was before the reuse existed.
 */
public final class ClusterAnchorsBuilder {
    private static final Logger LOG = Global.getLogger(ClusterAnchorsBuilder.class);

    // The font tolerance last announced, so a knob that sits still is not restated on every
    // fit. Zero cannot come from the read - the tuning floors it above zero - so it doubles
    // as "nothing announced yet" and the first fit after the log opens labels its baseline.
    private static double lastLoggedFontTolerance;

    // Drives only; never instantiated.
    private ClusterAnchorsBuilder() {
    }

    // Rebuilds the cluster-label placements in place: replaces the standing pair with this
    // pass's, having - only when the placements are needed - split the owned systems into
    // contiguous clusters and fitted one anchor to each. The placements feed two consumers: the
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
    // to the selected bloc's name, since the view cannot name a synthetic id. Leaves in the
    // caller's pair what the placements it just fitted were made under: a placement list nothing
    // states the rules of cannot be compared against a later rebuild's, and only this build knows
    // the tuning it read. That same pair is what came in, so the placements already standing can
    // be carried over for the clusters they still name rather than every one of them being
    // searched again. The geometry the fit clips and trims against is the caller's, so the
    // revision naming it is handed in rather than sampled.
    public static void rebuildClusterAnchors(
            StandingClusterAnchors standingAnchors,
            CellGeometryCache geometryCache,
            SectorAPI sector,
            ClusterLabelStylingSnapshot styling,
            int geometryRevision) {

        // Read ahead of the gate rather than inside it, because the standing pair needs
        // labelling either way: a rebuild that fits nothing still leaves a list behind, and an
        // unlabelled one is indistinguishable from one fitted under rules that still hold.
        var fitFingerprint = readFitFingerprint(geometryRevision);

        // Indexed before this pass replaces the pair, since that is where the previous one
        // survives - the two move together, so there is no window where the list is emptied
        // ahead of the fit that fills it.
        var reusableAnchors = indexReusableAnchors(standingAnchors, fitFingerprint);

        if (!NameFormatPreference.getSelectedNameFormat().areNamesDrawn()
                && !KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            standingAnchors.replaceAnchors(List.of(), fitFingerprint);
            return;
        }
        // Timed from here, past the gate: the skipped path does no work worth reporting, and the
        // fit is the rebuild's dominant cost, so it needs a duration of its own beside the
        // politics scan's and the cell shaping's rather than only inside the whole-rebuild total.
        var fitStart = System.nanoTime();

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
        // name matches its recoloured fill and border exactly without re-reading the profile here.
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

        // The tuning comes back off the fingerprint rather than from a second read of the
        // settings, so what the fit ran under and what it reports having run under are one
        // value and cannot drift apart on a rebuild that straddles a settings change.
        var spec = fitFingerprint.specification();
        logFontToleranceChange(spec);

        var fit = ClusterAnchorPlacement.computeClusterAnchors(
            clusters,
            geometryCache.getCellEdgesByCellId(),
            geometryCache.getSiteBySystemId(),
            cellGrouping,
            spec,
            new ClusterLabelResolvers(
                ClusterLabelStyling.newLabelColourResolver(
                    ownerBySystemId,
                    BlocNameStyles.readFromLunaSettings(),
                    styleDecisionByBlocId,
                    styling.desaturationPalette()),
                ClusterLabelStyling.newNameEstimatorResolver(
                    sector,
                    viewGrouping.view(),
                    viewGrouping.grouping(),
                    filter.isFiltering(),
                    filter.selectedBlocId())),
            reusableAnchors);
        standingAnchors.replaceAnchors(fit.anchors(), fitFingerprint);

        // The search's cost is the product of its inputs, so the sweep knobs and the keep-out
        // count are reported beside the duration - a slow fit is read off which multiplicand
        // grew, not off the total alone. Three levels are reported because each understates the
        // next: the direction count reads as swept but is a tuning knob the fan adds fixed
        // extras to, so it prints as swept-over-configured; the candidates are that fan crossed
        // with the offsets over every cluster; and the band fits are what those candidates
        // actually spent, many apiece, which is the level the duration tracks. Measured rather
        // than recomputed from the knobs here, so a sweep that bailed out early reads as cheap.
        LOG.debug("Political map cluster anchors fitted; clusters="
            + clusters.size()
            + " anchors=" + fit.anchors().size()
            + " directions="
            + ClusterAnchorPlacement.countCandidateDirections(spec.search().directionCount())
            + "/" + spec.search().directionCount()
            + " offsets=" + spec.search().offsetCount()
            + " candidates=" + fit.candidateCount()
            + " bandFits=" + fit.bandFitCount()
            + " keepOuts=" + geometryCache.getSiteBySystemId().size()
            + " took=" + Timings.formatMillis(System.nanoTime() - fitStart));
    }

    // The rebuild for a path with no holder map at hand - the debug border-tracing view,
    // which builds no production draw lists to borrow one from. Resolves holding from
    // the sector itself, gated behind the toggle so the economy scan only runs while
    // someone is actually looking at the anchors. Leaves the caller's pair labelled with what
    // produced it exactly as the shared path does, so the caller holds one fact about its
    // placements whichever view built them.
    public static void rebuildClusterAnchorsFromSector(
            StandingClusterAnchors standingAnchors,
            CellGeometryCache geometryCache,
            SectorAPI sector,
            PoliticalMapView view,
            int geometryRevision) {

        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            // The economy scan is what the toggle is guarding, so it is skipped - but the
            // list it leaves empty still has to say what produced it, which costs a settings
            // read and no sector work at all. Emptying it and labelling it is the one write
            // the pair takes, so this path's skip cannot leave the two disagreeing.
            standingAnchors.replaceAnchors(List.of(), readFitFingerprint(geometryRevision));
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
            standingAnchors,
            geometryCache,
            sector,
            new ClusterLabelStylingSnapshot(
                SectorPolitics.resolveDominantHolderBySystemId(sector, grouping),
                desaturationPalette,
                new ViewGrouping(view, grouping),
                FilterSnapshot.unfiltered()),
            geometryRevision);
    }

    // The standing placements a fit made now may carry over, filed under the cluster each of
    // them names. Empty unless this rebuild would run under exactly what the standing pair says
    // its list was fitted under: the tuning and the geometry each invalidate every placement at
    // once rather than any one of them in particular, so the whole map is dropped on a mismatch
    // instead of any cluster being asked to notice a change it cannot see. That is also what
    // keeps a rebuild the caller has no record for - the first of a session, or one after a
    // discard - total, since a null fingerprint matches nothing.
    private static Map<ClusterIdentity, ClusterAnchor> indexReusableAnchors(
            StandingClusterAnchors standingAnchors,
            AnchorFitFingerprint fitFingerprint) {

        if (!fitFingerprint.equals(standingAnchors.getFitFingerprint())) {
            return Map.of();
        }
        var anchorByIdentity = new HashMap<ClusterIdentity, ClusterAnchor>();
        for (var anchor : standingAnchors.getAnchors()) {
            anchorByIdentity.put(anchor.identity(), anchor);
        }
        return anchorByIdentity;
    }

    // Mints the fingerprint a fit made now would run under: the live tuning read off the
    // settings, against the revision the cells it clips and trims within stand at. One point
    // reads the tuning for both the fit and its fingerprint, so no path can fit under one
    // reading and report another.
    private static AnchorFitFingerprint readFitFingerprint(int geometryRevision) {
        return new AnchorFitFingerprint(
            LabelAnchorSpecification.readFromLunaSettings(),
            geometryRevision);
    }

    // Announces the font tolerance when it moves, not on every fit: it is a static setting,
    // so restating it per fit would only pad a line already carrying the counts that do
    // move. A capture still needs each reading attributable to the precision behind it,
    // which one line per change gives at a fraction of the noise. The halving count comes
    // with it because the mapping is a step function - neighbouring tolerances can resolve
    // to the same count, and this is what says an unmoved band-fit total is the knob doing
    // nothing rather than the sweep failing to take.
    private static void logFontToleranceChange(LabelAnchorSpecification spec) {

        var tolerance = spec.bandFit().fontHeightTolerance();
        if (tolerance == lastLoggedFontTolerance || !LOG.isDebugEnabled()) {
            return;
        }
        lastLoggedFontTolerance = tolerance;

        LOG.debug("Political map anchor font search precision;"
            + " tolerance=" + tolerance
            + " bisections=" + Bisection.countStepsForTolerance(
                spec.nameFit().minFontHeight(),
                spec.nameFit().maxFontHeight(),
                tolerance));
    }
}
