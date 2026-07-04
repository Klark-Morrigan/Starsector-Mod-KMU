package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.render.model.ClusterAnchor;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the cached faction-name labels from the resolved cluster placements. One label
 * per cluster whose placement search accepted a line: the owner's display name, hung at
 * the accepted line's midpoint and slanted to its slope, in the owner's bright colour.
 *
 * <p>Reuses the {@link ClusterAnchorsBuilder} placements rather than re-clustering: the
 * anchor list is the single source of where a name goes, so the labels and the debug
 * anchor overlay never disagree and the (costly) placement search runs once. This class
 * adds only the text - resolving each cluster's faction id to a display name and minting a
 * {@link DrawableString} - so it depends on the sector merely to read faction names, never
 * to re-derive ownership.
 *
 * <p>The {@link DrawableString}s own GL buffers, so a rebuild disposes the previous list's
 * strings before minting the new ones; nothing here runs per frame. The label font is the
 * player's pick from the "Faction name font" setting, resolved to a {@code graphics/fonts}
 * face and loaded lazily; each loaded face is cached by path (so switching fonts and back
 * costs nothing) and a face that fails to load is logged once and skipped, leaving the
 * labels empty rather than retrying every rebuild.
 */
final class FactionLabelsBuilder {
    private static final Logger LOG = Global.getLogger(FactionLabelsBuilder.class);

    // The font setting stores a basename (e.g. insignia15LTaa); the faces all live under
    // graphics/fonts with a .fnt extension, so the basename resolves to a path by wrapping.
    private static final String FONT_DIR = "graphics/fonts/";
    private static final String FONT_EXTENSION = ".fnt";

    // One readable line at a fixed world-unit height. Chunk 7 draws every name at this one
    // size; the placement already carries a solver-fitted band thickness and line count,
    // but sizing the name to the cluster (and wrapping to multiple lines) is Chunk 8's job,
    // so this constant stands in until then. World units because the renderer scales the
    // glyphs by the map factor, so the name grows and shrinks with the territory it labels.
    private static final float LABEL_WORLD_SIZE = 400f;

    // Faces loaded on the render thread, cached by path so a font the player already
    // selected loads once even after switching away and back. failedPaths remembers a face
    // that would not load, so its FontException is logged once, not on every rebuild.
    private static final Map<String, LazyFont> FONT_BY_PATH = new HashMap<>();
    private static final Set<String> FAILED_FONT_PATHS = new HashSet<>();

    // Builds only; never instantiated.
    private FactionLabelsBuilder() {
    }

    // Rebuilds the label list in place from the current placements: disposes the standing
    // strings (they hold GL buffers), clears, and - only when the names toggle is on -
    // mints one string per cluster that accepted a line. Profiled and timed on its own so
    // the label build's cost is visible next to the drawables and anchor builds; a failed
    // font load leaves the list empty. Runs at rebuild time only, never per frame.
    static void rebuildFactionLabels(List<FactionLabel> labels, List<ClusterAnchor> anchors,
            SectorAPI sector) {
        disposeAll(labels);
        labels.clear();
        if (!KmuLunaSettings.getPoliticalMapShowFactionNames()) {
            return;
        }
        var fontPath = FONT_DIR + KmuLunaSettings.getPoliticalMapFactionNameFont() + FONT_EXTENSION;
        var resolvedFont = getFont(fontPath);
        if (resolvedFont == null) {
            return;
        }
        var buildStart = System.nanoTime();
        KmuProfiling.getProfiler().measure("politicalMap.buildFactionLabels", () -> {
            // The plan step (which clusters get a name, its text, colour, hang point, and
            // slant) is pure; only the mint below touches GL, so the decision is unit-
            // testable without a font or a GL context.
            for (var plan : planLabels(anchors, sector)) {
                var text = resolvedFont.createText(plan.name(), plan.color(), LABEL_WORLD_SIZE);
                text.setAnchor(LazyFont.TextAnchor.CENTER);
                labels.add(new FactionLabel(text, plan.color(), plan.hangX(), plan.hangY(),
                        plan.slantDegrees()));
            }
        });
        if (LOG.isDebugEnabled()) {
            LOG.debug("Political map faction labels built; labels=" + labels.size()
                    + " ofClusters=" + anchors.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - buildStart));
        }
    }

    /**
     * A single cluster's resolved label decision, before any GL string is minted: the
     * display name, the colour to draw it in, the world point it hangs on, and its slant.
     * The pure output of {@link #planLabels}, so the placement-to-name logic is testable
     * without a font or a GL context.
     */
    record LabelPlan(String name, Color color, float hangX, float hangY, float slantDegrees) {
    }

    // Decides one label per cluster whose search accepted a line: skipping a collapsed
    // placement (dot only, no accepted axis) and any cluster whose faction name will not
    // resolve (missing faction, blank name), so a stray cluster never mints an empty label.
    // Pure - no GL, no font - so the whole decision is unit-testable.
    static List<LabelPlan> planLabels(List<ClusterAnchor> anchors, SectorAPI sector) {
        var plans = new ArrayList<LabelPlan>(anchors.size());
        for (var anchor : anchors) {
            var acceptedAxis = anchor.acceptedAxis();
            if (acceptedAxis == null) {
                continue;
            }
            var name = resolveFactionName(sector, anchor.factionId());
            if (name == null || name.isBlank()) {
                continue;
            }
            plans.add(new LabelPlan(name, anchor.color(), anchor.anchorX(), anchor.anchorY(),
                    computeSlantDegrees(acceptedAxis)));
        }
        return plans;
    }

    // Disposes every standing label's DrawableString so their GL buffers are freed at the
    // moment of rebuild rather than left to LazyLib's finalizer sweep. Public entry for the
    // plugin to call on cleanup as well as the rebuild here.
    static void disposeAll(List<FactionLabel> labels) {
        for (var label : labels) {
            label.text().dispose();
        }
    }

    // The owner's on-map name: its long display name, or null when the faction cannot be
    // resolved (an owner id with no live faction, e.g. a mod removed mid-save).
    private static String resolveFactionName(SectorAPI sector, String factionId) {
        var faction = sector.getFaction(factionId);
        return faction == null ? null : faction.getDisplayNameLong();
    }

    // The slope of the accepted line in degrees, folded upright so the name reads
    // left-to-right: a line pointing into the left half-plane is reversed first, so a name
    // never renders upside down. The remaining lean (bounded by the anchor's max-slant cap)
    // is kept as the label's slant. Chunk 8's fuller upright handling supersedes this.
    private static float computeSlantDegrees(ClusterAnchor.AxisSegment axis) {
        var deltaX = axis.endX() - axis.startX();
        var deltaY = axis.endY() - axis.startY();
        if (deltaX < 0f) {
            deltaX = -deltaX;
            deltaY = -deltaY;
        }
        return (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
    }

    // Loads one label face and caches it by path, so the player's current pick loads once
    // even after switching fonts and back. A face known to have failed returns null without
    // retrying; a FontException (a missing or malformed .fnt) is logged once for that path
    // and the names simply do not draw rather than the map render throwing every frame.
    private static LazyFont getFont(String fontPath) {
        var cached = FONT_BY_PATH.get(fontPath);
        if (cached != null) {
            return cached;
        }
        if (FAILED_FONT_PATHS.contains(fontPath)) {
            return null;
        }
        try {
            var loaded = LazyFont.loadFont(fontPath);
            FONT_BY_PATH.put(fontPath, loaded);
            return loaded;
        } catch (FontException exception) {
            FAILED_FONT_PATHS.add(fontPath);
            LOG.error("Political map could not load label font '" + fontPath
                    + "'; faction names using this font disabled this session", exception);
            return null;
        }
    }
}
