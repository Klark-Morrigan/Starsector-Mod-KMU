package kmu.maplayers.politicalmap.base;

/**
 * One option in the political-map filter picker: a bloc the player can spotlight, reduced to the
 * three things the picker row needs to draw and the filter needs to store. A bloc is a faction under
 * the factions view and an alliance under the alliances view, so this carries the stable id the
 * selection serialises ({@link kmu.maplayers.politicalmap.base.refresh.FilterSelection}) plus the
 * label and crest the row renders - nothing about how the id resolves into presence-aware territory,
 * which is the resolver's concern.
 *
 * <p>Plain data with no Starsector types - the crest is the faction's crest sprite path, not a loaded
 * {@code SpriteAPI} - so the selectable list can be built and asserted on hand-built inputs and the
 * picker widget owns the actual sprite load. A faction bloc carries its crest; an alliance bloc has no
 * crest of its own, so {@code crestSpritePath} is null there and the row draws its name alone.
 *
 * @param blocId          the bloc's save-stable id (a faction id, or an alliance id) - the value the
 *                        filter selection stores and the resolver keys presence off
 * @param displayName     the bloc's label for the picker row (a faction's short name, an alliance's
 *                        name); null only when no name resolves, which the row treats as unlabelled
 * @param crestSpritePath the faction crest sprite path the row draws beside the name, or null when the
 *                        bloc has no crest (every alliance bloc, and a faction with no authored crest)
 */
public record SelectableBloc(String blocId, String displayName, String crestSpritePath) {
}
