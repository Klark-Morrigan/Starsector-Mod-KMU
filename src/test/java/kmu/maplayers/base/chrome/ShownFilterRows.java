package kmu.maplayers.base.chrome;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.controls.MapFilterRow;
import kmlib.starsector.ui.map.controls.MapFilterRows;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.map.controls.FilteredMapWidgetFake;
import kmlib.testfixtures.starsector.ui.map.controls.MapFilterRowFake;

import java.util.List;

/**
 * A map's filter row as this package meets one: reached off a map widget's own accessor rather than
 * built directly, since that hop is what a row on screen is, and a row handed over without it would
 * stand for a state no screen is ever in.
 *
 * <p>Each call stands a row of its own, which is what makes two of them the two rows a reopened
 * screen leaves behind - the one distinction everything here turns on.
 *
 * <p>The two shapes a row comes in are named here rather than stated at each caller: the game's own
 * strip, which has room to spare, and one somebody else has already filled. Both are facts about
 * what a row can be rather than about any one case, and a caller stating its own could state a row
 * no screen has.
 */
final class ShownFilterRows {

    /** How many buttons the game's own strip carries, which is where an appended control lands. */
    static final int VANILLA_BUTTON_COUNT = 2;

    // The words the game puts on the map strip's own buttons, so a row here reads as one.
    private static final String[] VANILLA_BUTTON_LABELS = {"Starscape", "Names"};

    // A row filled to within a few units of its far edge, which is where a mod that appended to it
    // first leaves it: two buttons of the game's own width, and nowhere to put a third.
    private static final Rectangle FULL_ROW_BOX = new Rectangle(0f, 0f, 250f, 25f);
    private static final float FULL_ROW_BUTTON_WIDTH = 120f;

    private ShownFilterRows() {
    }

    /** The {@code M} screen's strip as the game leaves it, with room to spare for one more control. */
    static MapFilterRow createRowWithRoomToSpare() {
        return createRowOver(createRowFakeWithRoomToSpare());
    }

    /** The same strip kept as the fixture, for a caller reading back what was appended to it. */
    static MapFilterRowFake createRowFakeWithRoomToSpare() {
        return MapFilterRowFake.createMapScreenStrip(VANILLA_BUTTON_LABELS);
    }

    /** A strip somebody else has already filled, which has no room for one more control. */
    static MapFilterRowFake createFullRowFake() {

        return MapFilterRowFake.createRowOfSize(
            FULL_ROW_BOX, FULL_ROW_BUTTON_WIDTH, VANILLA_BUTTON_LABELS);
    }

    /**
     * A stated row put where a map widget offers it.
     *
     * @param rowFake the row to offer
     * @return it as the row on screen
     */
    static MapFilterRow createRowOver(MapFilterRowFake rowFake) {

        return MapFilterRows.resolveEmbeddedMapFilterRow(
            new EmbeddedMap(new FilteredMapWidgetFake(rowFake), List.of()));
    }
}
