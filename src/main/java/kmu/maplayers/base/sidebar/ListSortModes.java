package kmu.maplayers.base.sidebar;

import java.util.List;

/**
 * The sort vocabulary a layer offers its picker: the modes in the order the selector stacks its
 * rows, and the mode an absent or unrecognised stored key falls back to. Bundled because the two
 * are meaningless apart - a stored key can only be resolved against the set it belongs to, with
 * somewhere to land when it misses - so every reader takes the pair whole rather than as two
 * loose parameters that could be mixed from different layers.
 *
 * @param <T>         the list item type the modes rank
 * @param modes       the modes, in the order a selector lays its rows out top to bottom
 * @param defaultMode the mode an absent or unrecognised stored key falls back to
 */
public record ListSortModes<T>(
    List<? extends ListSortMode<T>> modes,
    ListSortMode<T> defaultMode) {
}
