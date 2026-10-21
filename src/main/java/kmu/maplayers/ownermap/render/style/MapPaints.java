package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.HashMap;
import java.util.Map;

/**
 * The paint every cell and cluster group of one build draws in, resolved from three stated inputs:
 * the paint scheme, the painting layer's reading of its owners, and the sidebar picks the build
 * sampled.
 *
 * <p>The style resolvers' one entry point. An owner's fill, its cluster border and its cells' seams
 * all ask here, and so does every cell nobody owns, so no two of them can resolve a paint from two
 * readings of the same inputs. A stage that paints - the cell builder, the cluster-group builder -
 * takes one of these rather than the build it belongs to, which is what lets a layer stand such a
 * stage up without the pipeline around it.
 *
 * <p>Remembers as well as resolves. Every input is fixed for the life of the value, so each answer
 * is resolved on first ask and kept: a map of a few owners and many cells resolves an owner's paint
 * a few times rather than once per cell, and the uninhabited backdrop covering most of the sector
 * resolves its paint once rather than once per cell. Not safe for concurrent use.
 */
public final class MapPaints {

    // An unowned cell's paint takes one of four shapes: settled or empty, crossed with whether the
    // spotlit owner lives there. Each answer is one bit of the slot the shape is kept in.
    private static final int PRESENCE_BIT = 1;
    private static final int SETTLED_BIT = 2;
    private static final int UNOWNED_PAINT_COUNT = 4;

    private final ContentInputs contentInputs;

    // Each owner's paint, resolved on first ask.
    private final Map<SystemOwner, ResolvedOwnerPaint> paintByOwner = new HashMap<>();

    private final OwnerReading reading;
    private final MapStyling styling;

    // Each shape of unowned paint, resolved on first ask and kept in the slot its two answers name.
    private final ResolvedOwnerPaint[] unownedPaints = new ResolvedOwnerPaint[UNOWNED_PAINT_COUNT];

    /**
     * @param styling       the resolved paint scheme: the theme, the layer's categories and the
     *                      palettes an unowned or receded element paints in
     * @param reading       the painting layer's answers about its owners - each one's category and
     *                      recede - resolved once for the build; null only for the placeholder a
     *                      failed build stands behind, which is never asked to paint an owner
     * @param contentInputs the sidebar picks the build sampled: the spotlight and the recede behind
     *                      it
     */
    public MapPaints(
            MapStyling styling,
            OwnerReading reading,
            ContentInputs contentInputs) {

        this.contentInputs = contentInputs;
        this.reading = reading;
        this.styling = styling;
    }

    /**
     * @return the scheme these paints resolve against, for a stage that also shapes what it paints -
     *         its border trace and the sector-wide smoothing and hatch knobs - so the shape and the
     *         paint are read off one scheme
     */
    public MapStyling getStyling() {
        return styling;
    }

    /**
     * Everything one owner's elements paint from: the reading's decision for it mapped onto the
     * theme, carried down to the shades.
     *
     * <p>Under a spotlight every owner but the pick takes the recede behind it; off filter the
     * reading's own decision stands. That rule is {@link OwnerStyleResolver}'s.
     *
     * @param owner whose elements to paint: its ID - an owner ID, or one of the filter's synthetic
     *              keys - names its style, and its shades are what it paints in where nothing
     *              desaturates it
     * @return the bundle, the adjustment over it, and the shades its elements pick from
     */
    public ResolvedOwnerPaint resolveOwnerPaintOf(SystemOwner owner) {
        return paintByOwner.computeIfAbsent(owner, this::resolveUncachedOwnerPaintOf);
    }

    /**
     * Everything a cell nobody owns paints from: the category the layer declares for a settled or
     * an empty cell, the recede a spotlight sinks it under, and the neutral shades it paints in.
     *
     * <p>A cell the spotlit owner lives in is spared the recede and lifted clear of the greys around
     * it, while keeping the neutral rather than the pick's own shades. Those rules are
     * {@link UnownedStyleResolver}'s.
     *
     * @param isSettled             whether something stands in the cell's system, as
     *                              {@link UnownedStyleResolver#isSettledSystem} answers it
     * @param isSpotlitOwnerPresent whether the spotlit owner has something counted standing there;
     *                              false for every cell off filter
     * @return the bundle, the adjustment over it, and the shades the cell's elements pick from
     */
    public ResolvedOwnerPaint resolveUnownedPaintOf(
            boolean isSettled,
            boolean isSpotlitOwnerPresent) {

        var index = indexUnownedPaint(isSettled, isSpotlitOwnerPresent);

        if (unownedPaints[index] == null) {
            unownedPaints[index] = resolveUncachedUnownedPaintOf(isSettled, isSpotlitOwnerPresent);
        }

        return unownedPaints[index];
    }

    // The slot one shape of unowned paint is kept in.
    private static int indexUnownedPaint(
            boolean isSettled,
            boolean isSpotlitOwnerPresent) {

        var settledBit = isSettled
            ? SETTLED_BIT
            : 0;

        var presenceBit = isSpotlitOwnerPresent
            ? PRESENCE_BIT
            : 0;

        return settledBit + presenceBit;
    }

    // The owner paint resolved afresh, for the memo above to remember: the layer's own per-owner
    // decision mapped onto this build's theme, then carried down to the owner's shades.
    private ResolvedOwnerPaint resolveUncachedOwnerPaintOf(SystemOwner owner) {

        var ownerStyling = OwnerStyling.resolveFrom(
            styling.renderStyle(),
            styling.categories(),
            OwnerStyleResolver.resolveOwnerStyleDecision(
                owner.ownerId(),
                reading,
                styling.categories(),
                contentInputs));

        return ResolvedOwnerPaint.resolveFrom(
            ownerStyling,
            owner,
            styling.desaturationPalette());
    }

    // The unowned paint resolved afresh, for the memo above to remember.
    //
    // One classification drives both the bundle and the recede, so a cell cannot take the settled
    // style yet miss the recede that style is meant to draw under. Only the filter's recede reaches
    // such a cell: a recede the view applies of its own accord describes owners, which it is not.
    //
    // A spared cell paints the lifted neutral rather than the plain one: the recede it was spared
    // only stopped it sinking, and the grey it would have kept is the value the background around it
    // sank from - close enough to read as the same surface. Both palettes are the scheme's own, so
    // the lift and the sink are one decision apart.
    private ResolvedOwnerPaint resolveUncachedUnownedPaintOf(
            boolean isSettled,
            boolean isSpotlitOwnerPresent) {

        var adjustment = UnownedStyleResolver.resolveRecedeOf(
            isSettled,
            contentInputs.filterRecedeAdjustment(),
            isSpotlitOwnerPresent);

        var ownPalette = isSpotlitOwnerPresent
            ? styling.presencePalette()
            : styling.neutralPalette();

        return ResolvedOwnerPaint.resolveFrom(
            styling.resolveUnownedStyle(isSettled),
            adjustment,
            ownPalette,
            styling.desaturationPalette());
    }
}
