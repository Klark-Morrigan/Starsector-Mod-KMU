package kmu.maplayers.politicalmap.base.render;

import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/**
 * The static seams one arrangement has standing, closed in reverse on the way out so a seam opened
 * over another is never left standing when the inner one is already gone.
 *
 * <p>Shared because every arrangement in this package that stands in for more than one class was
 * carrying its own copy of the list, the reverse loop and the open-and-remember helper, differing in
 * nothing but the name of the field. What each of them varies is which seams it opens and what it
 * answers on them, which stays where it is read.
 */
final class StaticSeams {

    private final List<MockedStatic<?>> openSeams = new ArrayList<>();

    /**
     * Closes every seam this opened, innermost first, and forgets them - what an arrangement runs on
     * the way out, since a seam left standing stands in for its class for whatever runs next.
     */
    void closeEverySeam() {

        for (var index = openSeams.size() - 1; index >= 0; index--) {
            openSeams.get(index).close();
        }
        openSeams.clear();
    }

    /**
     * @param seamedClass the class whose statics are stood in for
     * @param <T>         that class, so the caller states its answers without a cast
     * @return the open seam, held for closing and handed back for the caller to answer on
     */
    <T> MockedStatic<T> openSeam(Class<T> seamedClass) {

        var seamMock = mockStatic(seamedClass);
        openSeams.add(seamMock);

        return seamMock;
    }
}
