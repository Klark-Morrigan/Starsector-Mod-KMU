package kmu.maplayers.politicalmap.base;

/**
 * Who a political-map picker row is about: a bloc the player can spotlight, reduced to what the row
 * draws and what the filter stores. A bloc is a faction under the factions view, an alliance under the
 * alliances view, and a claimant under the claims view, so this carries the stable id the selection
 * serialises ({@link kmu.maplayers.base.sidebar.FilterSelection}) plus the label and crest the row
 * renders - nothing about how the id resolves into territory, which is the resolver's concern, and
 * nothing about how the bloc ranks, which varies by view.
 *
 * <p>It carries no metrics and answers no picker seam of its own: those ride on {@link RankedBloc},
 * which pairs one of these with the stats type its own view ranks by. Keeping identity separate is
 * what stops a layer's option from having to carry another layer's numbers.
 *
 * <p>Plain data with no Starsector types - the crest is the faction's crest sprite path, not a loaded
 * {@code SpriteAPI} - so the selectable list can be built from hand-built inputs with nothing
 * loaded, and the picker widget owns the actual sprite load. A faction bloc carries its own crest; an alliance bloc
 * carries its lead (colour) member's crest, so both draw a crest and only a bloc whose crest faction
 * has no authored crest leaves {@code crestSpritePath} null and draws its name alone.
 *
 * @param blocId          the bloc's save-stable id (a faction id, or an alliance id) - the value the
 *                        filter selection stores and the resolver keys presence off
 * @param displayName     the bloc's label for the picker row (a faction's short name, an alliance's
 *                        name); null only when no name resolves, which the row treats as unlabelled
 * @param crestSpritePath the crest sprite path the row draws beside the name - a faction bloc's own
 *                        crest, or an alliance bloc's lead (colour) member's crest - or null when that
 *                        crest faction has no authored crest
 */
public record SelectableBloc(
    String blocId,
    String displayName,
    String crestSpritePath) {
}
