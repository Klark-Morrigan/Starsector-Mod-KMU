package kmu.maplayers.base.labels;

import kmlib.colour.Colours;

import kmu.maplayers.base.labels.anchor.ClusterAnchor;

import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;

/**
 * One drawn line of a cluster name: a cached {@link DrawableString} plus where and how it
 * hangs on the map. Built from a
 * {@link ClusterAnchor}'s accepted placement - a
 * multi-line name mints one of these per wrapped line, each hung at its own point (stacked
 * perpendicular to the accepted line, centred as a block on the anchor) with the shared
 * slant - so the block sits inside the border, clear of icons, leaning along the cluster's
 * axis, in the owner's bright colour, at the font size the fit achieved. Per-line strings
 * rather than one embedded-newline string because each line centres on the block's axis
 * individually and the stack honours the line-spacing knob, which a single
 * {@link DrawableString} (fixed one-font-size line advance, left-aligned block) cannot do.
 *
 * <p>The owner's bright shade at full opacity is kept alongside the string because the renderer
 * refades it to the map's alpha: the string bakes only one colour, so the un-faded original has to
 * live here to be re-derived. Setting the string's colour costs no buffer rebuild (there is no
 * per-substring colour data), and the fade is applied only when the map's alpha has moved since
 * the last frame, so a steady map allocates nothing to draw its names.
 *
 * <p>Not pure data, which is why it lives in the labels package beside the builder and the
 * renderer: the {@link DrawableString} owns a GL vertex buffer that must be
 * {@link DrawableString#dispose() disposed} when the label is rebuilt. Caching it (rather
 * than rebuilding a string per frame) is what keeps the name draw a buffered blit instead
 * of per-frame VBO churn; the builder disposes the previous strings whenever it rebuilds
 * the list.
 */
public final class Label {

    // No fade applied yet. Unequal to every alpha, so the first frame always sets the string's colour
    // rather than trusting whatever colour it was minted in.
    private static final float NO_FADE_APPLIED = Float.NaN;

    private final Color baseColour;
    private final float hangX;
    private final float hangY;
    private final float slantDegrees;
    private final DrawableString text;

    // The map alpha the string's colour was last faded to.
    private float appliedAlphaMult = NO_FADE_APPLIED;

    /**
     * @param text         the minted string, owning its GL buffer
     * @param baseColour   the owner's bright shade at full opacity, which every fade is taken from
     * @param hangX        the world X the string's centre hangs on
     * @param hangY        the world Y the string's centre hangs on
     * @param slantDegrees the slant the string is drawn at
     */
    public Label(
            DrawableString text,
            Color baseColour,
            float hangX,
            float hangY,
            float slantDegrees) {

        this.text = text;
        this.baseColour = baseColour;
        this.hangX = hangX;
        this.hangY = hangY;
        this.slantDegrees = slantDegrees;
    }

    /** @return the world X the string's centre hangs on */
    public float getHangX() {
        return hangX;
    }

    /** @return the world Y the string's centre hangs on */
    public float getHangY() {
        return hangY;
    }

    /** @return the slant the string is drawn at, in degrees */
    public float getSlantDegrees() {
        return slantDegrees;
    }

    /** @return the minted string */
    public DrawableString getText() {
        return text;
    }

    /**
     * Fades the string to the map's alpha, so it dims in step with the cluster it sits on.
     *
     * <p>A no-op while the alpha holds, which is every frame but those of the map's own fade: the
     * faded colour is a new {@link Color}, and minting one per label per frame would be garbage
     * for a colour that has not moved.
     *
     * @param alphaMult the map's alpha this frame
     */
    public void applyFade(float alphaMult) {

        // Compared as floats on purpose: a held alpha is the same value frame to frame, and any
        // difference at all is a fade that moved.
        if (alphaMult == appliedAlphaMult) {
            return;
        }

        text.setBaseColor(Colours.scaleAlpha(baseColour, alphaMult));

        appliedAlphaMult = alphaMult;
    }
}
