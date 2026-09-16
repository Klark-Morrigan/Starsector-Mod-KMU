package kmu.desktop.ui.swing;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the controls a builder actually put on screen, wherever it nested them.
 *
 * <p>Every suite over this package asks the same question - which checkboxes came out, which
 * value boxes, which resets - and the answer is always the same walk with a different type in
 * it. Written per suite it was four copies, and a copy that stopped at the first level would
 * quietly assert about a row rather than about the whole of what was built.
 *
 * <p>Depth-first and in the order components were added, so the order a suite asserts on is the
 * order a reader would see down the panel.
 */
final class ComponentTreeFixture {

    private ComponentTreeFixture() {
    }

    /**
     * Every control of one kind under a container.
     *
     * @param root the container to search, itself excluded
     * @param kind what to look for
     * @param <T>  that kind
     * @return the ones found, in the order they were added
     */
    static <T extends Component> List<T> findAll(Container root, Class<T> kind) {

        var found = new ArrayList<T>();

        collectInto(root, kind, found);

        return found;
    }

    /**
     * The one control of a kind under a container.
     *
     * @param root the container to search, itself excluded
     * @param kind what to look for
     * @param <T>  that kind
     * @return the only one found
     * @throws IllegalStateException where there is not exactly one, since a suite asking for
     *                               "the" reset has assumed something the build no longer does
     */
    static <T extends Component> T findOnly(Container root, Class<T> kind) {

        var found = findAll(root, kind);

        if (found.size() != 1) {
            throw new IllegalStateException(
                "expected one " + kind.getSimpleName() + ", found " + found.size());
        }
        return found.get(0);
    }

    // A container that is itself of the wanted kind is taken AND descended into: a panel can
    // hold panels, and a checkbox drawn with something inside it is still a checkbox.
    private static <T extends Component> void collectInto(
            Container parent,
            Class<T> kind,
            List<T> found) {

        for (var child : parent.getComponents()) {

            if (kind.isInstance(child)) {
                found.add(kind.cast(child));
            }

            if (child instanceof Container nested) {
                collectInto(nested, kind, found);
            }
        }
    }
}
