package kmu.maplayers.politicalmap.base.render.style;

/**
 * The fixed geometry of the political map overlay - what is not player-tunable.
 *
 * <p>The border-channel width is a geometry constant: it drives the merged-cluster
 * shaping, not just the look, so it is fixed rather than exposed as a setting. The
 * colors, opacities, and line weights are player-tunable under the LunaLib "Visuals
 * customisation" tab and are read from {@link kmu.settings.KmuLunaSettings}.
 * Mechanical constants that do not decide the look (the GL vertex stride, the
 * terrain render range and engine layers) stay with the plugin that uses them.
 */
public final class PoliticalMapStyle {
    // Inward inset applied to every national-border edge, so two neighbouring
    // clusters leave a uniform 2 * inset channel; a same-faction seam is left
    // un-inset so the two cells' fills fuse along it. This one drives the merged-cluster
    // shaping (it decides where fills meet), not just the look, so it stays a fixed
    // constant. The border's rounding shape - corner radius, segments, chamfer
    // angle, and vertex weld tolerance - is player-tunable under the LunaLib "Dev"
    // tab instead, read from {@link kmu.settings.KmuLunaSettings}.
    public static final double BORDER_INSET_DISTANCE = 150.0;

    // Constants only; never instantiated.
    private PoliticalMapStyle() {
    }
}
