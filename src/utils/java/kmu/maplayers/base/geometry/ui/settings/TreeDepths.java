package kmu.maplayers.base.geometry.ui.settings;

/**
 * How deep each kind of row sits in a construction's layer tree.
 *
 * <p>Named once for every section that builds one, because the depth is what decides what a row
 * folds away with: a row written a level off from its siblings folds under the wrong one, and
 * two sections spelling their levels apart are two trees a reader has to learn separately.
 */
final class TreeDepths {

    /** The roll-up over a whole construction. */
    static final int ROOT = 0;

    /** A tier or a lone layer directly under the root, and the roll-ups across branches. */
    static final int BRANCH = 1;

    /** A switch inside a branch. */
    static final int LEAF = 2;

    /** A rule of one leaf's lines, folded away with the leaf it governs. */
    static final int RULE = 3;

    private TreeDepths() {
    }
}
