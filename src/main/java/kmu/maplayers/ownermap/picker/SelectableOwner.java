package kmu.maplayers.ownermap.picker;

/**
 * Who an owner-map picker row is about: an owner the player can spotlight, reduced to what the row
 * draws and what the filter stores. What an owner is is the painting view's call, so this carries
 * the stable ID the selection serialises ({@link kmu.maplayers.base.sidebar.FilterSelection}) plus
 * the label and crest the row renders - nothing about how the ID resolves into painted cells, which
 * is the resolver's concern, and nothing about how the owner ranks, which varies by view.
 *
 * <p>It carries no metrics and answers no picker seam of its own: those ride on {@link RankedOwner},
 * which pairs one of these with the stats type its own view ranks by. Keeping identity separate is
 * what stops a layer's option from having to carry another layer's numbers.
 *
 * <p>Plain data with no Starsector types - the crest is a sprite path, not a loaded
 * {@code SpriteAPI} - so the selectable list can be built with nothing loaded, and the picker
 * widget owns the actual sprite load. An owner with no crest leaves {@code crestSpritePath} null
 * and draws its name alone.
 *
 * @param ownerId         the owner's save-stable ID - the value the filter selection stores and the
 *                        resolver keys presence off
 * @param displayName     the owner's label for the picker row; null only when no name resolves,
 *                        which the row treats as unlabelled
 * @param crestSpritePath the crest sprite path the row draws beside the name, or null when the
 *                        owner has no crest
 */
public record SelectableOwner(
    String ownerId,
    String displayName,
    String crestSpritePath) {
}
