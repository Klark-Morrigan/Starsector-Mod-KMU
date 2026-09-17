package kmu.desktop.ui.swing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one thing a control column must never do: ask for more width than it has been given.
 *
 * <p>A stack reports the widest thing in it, and a slider alone wants 200px - so two sharing a
 * line want over 400 whatever the sidebar is, and the viewport grows a bar along the bottom.
 * Sideways is the wrong axis for a column of settings: what scrolls out of reach is the
 * right-hand end of controls whose left-hand end is the part that names them.
 *
 * <p>Asserted on the contract rather than on a laid-out window, because the two properties here
 * ARE the contract - a viewport gives a view its own width unless the view says otherwise, and
 * a bar that is refused cannot appear whatever is put in the column. Measuring a real layout
 * would also need font metrics, which the runner cannot always supply.
 */
final class WindowLayoutTest {

    @Nested
    class BuildControlScroller {

        @Test
        void buildControlScrollerRefusesAHorizontalBar() {

            assertThat(WindowLayout.buildControlScroller(new JPanel())
                    .getHorizontalScrollBarPolicy())
                .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        }

        @Test
        void buildControlScrollerFitsItsColumnToTheViewport() {

            var view = WindowLayout.buildControlScroller(new JPanel()).getViewport().getView();

            assertThat(view)
                .isInstanceOf(Scrollable.class);
            assertThat(((Scrollable) view).getScrollableTracksViewportWidth())
                .isTrue();
        }

        // The other axis is what the column scrolls ON. Tracking that too would squash every
        // row into one screen, which is the failure this pairing is easy to make.
        @Test
        void buildControlScrollerKeepsItsOwnHeight() {

            var view = WindowLayout.buildControlScroller(new JPanel()).getViewport().getView();

            assertThat(((Scrollable) view).getScrollableTracksViewportHeight())
                .isFalse();
        }

        @Test
        void buildControlScrollerHoldsTheColumnItWasGiven() {

            var column = new JPanel();
            var view = WindowLayout.buildControlScroller(column).getViewport().getView();

            assertThat(((java.awt.Container) view).getComponents())
                .containsExactly(column);
        }
    }
}
