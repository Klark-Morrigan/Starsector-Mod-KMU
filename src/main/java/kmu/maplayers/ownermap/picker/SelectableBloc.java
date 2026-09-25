package kmu.maplayers.ownermap.picker;

/**
 * Who an owner-map picker row is about: a bloc the player can spotlight, reduced to what the row
 * draws and what the filter stores. What a bloc is - a lone faction, or a group of several - is the
 * painting view's call, so this carries the stable ID the selection serialises
 * ({@link kmu.maplayers.base.sidebar.FilterSelection}) plus the label and crest the row renders -
 * nothing about how the ID resolves into painted cells, which is the resolver's concern, and nothing
 * about how the bloc ranks, which varies by view.
 *
 * <p>It carries no metrics and answers no picker seam of its own: those ride on {@link RankedBloc},
 * which pairs one of these with the stats type its own view ranks by. Keeping identity separate is
 * what stops a layer's option from having to carry another layer's numbers.
 *
 * <p>Plain data with no Starsector types - the crest is the faction's crest sprite path, not a loaded
 * {@code SpriteAPI} - so the selectable list can be built with nothing loaded, and the picker widget
 * owns the actual sprite load. A faction bloc carries its own crest; a grouped bloc carries its lead
 * (colour) member's crest, so both draw a crest and only a bloc whose crest faction has no authored
 * crest leaves {@code crestSpritePath} null and draws its name alone.
 *
 * @param blocId          the bloc's save-stable ID (a faction ID, or a group's ID) - the value the
 *                        filter selection stores and the resolver keys presence off
 * @param displayName     the bloc's label for the picker row (a faction's short name, a group's
 *                        name); null only when no name resolves, which the row treats as unlabelled
 * @param crestSpritePath the crest sprite path the row draws beside the name - a faction bloc's own
 *                        crest, or a grouped bloc's lead (colour) member's crest - or null when that
 *                        crest faction has no authored crest
 */
public record SelectableBloc(
    String blocId,
    String displayName,
    String crestSpritePath) {
}
