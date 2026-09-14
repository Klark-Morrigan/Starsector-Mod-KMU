package kmu.maplayers.base.chrome;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerArrangements;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.NoLayer;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the pass adds over the rule it asks: that every screen is asked rather than the one being
 * looked at, since one dialog moves both screens' rows and a screen nobody is on still has to be right
 * when they open it.
 *
 * <p>And the two halves of what "every frame" costs, which are the whole reason a per-frame pass is
 * affordable: a frame whose row has not moved asks nothing at all - not the row, not the save - while
 * a row the player has just changed is healed on the frame they changed it. Posed against the pick
 * read rather than against the write, since a pass that skipped only the write would still be
 * rebuilding the row to find that out.
 *
 * <p>And what a failure costs, which is one attempt at the row it failed on and not the rest of the
 * game: the row reaches every registered layer, a foreign mod's included, and a roster that throws
 * throws again on the identical row - so the throwing row is held as done with, and the player
 * arranging their bar afresh is what puts the screen back in play.
 *
 * <p>Held on the outcome and never on the attempt, which the case over a pick that drops what is
 * written to it is what pins: a screen recorded as settled on the strength of having been written to
 * would be left lit wrong until somebody opened the dialog.
 *
 * <p>What the heal does to a stranded pick, and which row it judges one against, is
 * {@link kmu.maplayers.base.layer.ScreenLayerTabsTest}'s.
 *
 * <p>Also the answer that is not a decision of its own but which the heal rests on: that it runs while
 * the campaign is paused - every screen the bar draws on pauses it, so a pass standing down under one
 * would never see a row that had just moved.
 */
final class MapLayerPickUpkeepTest {

    // The campaign is paused on every screen the bar draws on, so this is the delta the pass actually
    // runs at.
    private static final float PAUSED_FRAME = 0f;

    // The ID the arrangement store would name the layer that paints by, which nothing here arranges -
    // stated because registration reads every layer's id.
    private static final String PAINTING_LAYER_ID = "painting";

    private final MapLayer paintingLayerMock = mock(MapLayer.class);

    @AfterEach
    void restoreTheRosterTheCasesReplaced() {
        // The registry is static, so a roster of stand-ins would otherwise outlive its case.
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @AfterEach
    void forgetTheArrangementThisCaseMade() {
        // The holder is static too, so a bar arranged here would otherwise reorder every later row.
        MapLayerArrangements.forgetTheArrangement();
    }

    @Nested
    class Advance {

        @Test
        void advanceHealsEveryScreenRatherThanTheOneOnShow() {

            registerTheEmptyViewBesideALayerThatPaints();

            var mapScreenPicks = buildScreenPicksOnTheEmptyView();
            var intelScreenPicks = buildScreenPicksOnTheEmptyView();

            new MapLayerPickUpkeep(() -> List.of(mapScreenPicks, intelScreenPicks))
                .advance(PAUSED_FRAME);

            // One dialog moves both screens' rows at once, and the screen nobody is looking at is the
            // one whose disagreement would first be seen on the frame it is opened at.
            verify(mapScreenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
            verify(intelScreenPicks.layerSelection())
                .selectLayer(paintingLayerMock);
        }

        @Test
        void advanceLeavesAScreenAlreadyOnAnOfferedTabAlone() {

            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = buildScreenPicks();

            when(screenPicks.layerSelection().getActiveLayer())
                .thenReturn(paintingLayerMock);

            new MapLayerPickUpkeep(() -> List.of(screenPicks)).advance(PAUSED_FRAME);

            // The common frame, and the whole of what makes a per-frame pass affordable: nothing has
            // moved, so nothing is written and no save is touched.
            verify(screenPicks.layerSelection(), never())
                .selectLayer(any());
        }

        @Test
        void advanceSwallowsAFailedRowAndGoesOnRunning() {

            registerARosterWhoseLayerRefusesItsOwnId();

            var screenPicks = buildScreenPicks();
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();

            // The row reaches every registered layer, a foreign mod's included, and this pass runs on
            // frames the bar is nowhere near - so a stranger that throws must cost the heal and not
            // the game.
            assertThatCode(() -> upkeep.advance(PAUSED_FRAME))
                .doesNotThrowAnyException();
        }

        @Test
        void advanceStopsAskingAFailingScreenUntilItsRowMoves() {

            var idReadCount = new AtomicInteger();

            registerARosterWhoseLayerRefusesItsOwnId(idReadCount);

            var screenPicks = buildScreenPicks();
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // A roster that throws throws again on the identical row, so retrying it would be a
            // caught exception per screen per frame behind a log line written once. The row is
            // recorded as done with instead - one attempt, not sixty a second.
            assertThat(idReadCount)
                .hasValue(1);
        }

        @Test
        void advanceAsksAFailedScreenAgainOnceItsRowMoves() {

            var idReadCount = new AtomicInteger();

            registerARosterWhoseLayerRefusesItsOwnId(idReadCount);

            var screenPicks = buildScreenPicks();
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);

            MapLayerArrangements.arrangeBarWith(List.of(), List.of(PAINTING_LAYER_ID));

            upkeep.advance(PAUSED_FRAME);

            // Which is the answer to what a failure costs: not the rest of the game. The player
            // arranging their bar afresh is a different row, so whatever the fault was, it is not
            // being asked the same question again.
            assertThat(idReadCount)
                .hasValue(2);
        }

        @Test
        void advanceAsksNothingOfAScreenWhoseRowHasNotMoved() {

            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = buildScreenPicksAlreadyOnTheLayerThatPaints();
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // Building the offered row costs an index of the roster by ID, three lists and two stream
            // passes, which is not a thing to spend sixty times a second on an answer that moves when
            // the player opens a dialog. Counted on the pick read, since a pass that skipped only the
            // write would still be rebuilding the row to find out it had nothing to write - so the
            // one read is the frame that settled this screen, and the second frame asked nothing.
            verify(screenPicks.layerSelection(), times(1))
                .getActiveLayer();
        }

        @Test
        void advanceHealsAgainOnceTheRowMoves() {

            registerTheEmptyViewBesideALayerThatPaints();

            var screenPicks = buildScreenPicksAlreadyOnTheLayerThatPaints();
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);

            MapLayerArrangements.arrangeBarWith(List.of(), List.of(PAINTING_LAYER_ID));

            upkeep.advance(PAUSED_FRAME);

            // The player taking that tab off the bar is exactly what the skip above must not swallow,
            // and it is the case the whole pass exists for: the pick follows onto the one tab the row
            // has left.
            verify(screenPicks.layerSelection())
                .selectLayer(NoLayer.INSTANCE);
        }

        @Test
        void advanceAsksAgainNextFrameWhereTheMoveDidNotTake() {

            registerTheEmptyViewBesideALayerThatPaints();

            // A pick that reports the tab it was on whatever is written to it, which is what a save
            // with nowhere to write behaves like.
            var screenPicks = buildScreenPicksOnTheEmptyView(new AtomicReference<>(NoLayer.INSTANCE));
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);
            upkeep.advance(PAUSED_FRAME);

            // Held on the outcome and never on the attempt: a screen recorded as settled on the
            // strength of having been written to would be left lit wrong until somebody opened the
            // dialog, which is a thing the player has no reason to do.
            verify(screenPicks.layerSelection(), times(2))
                .selectLayer(paintingLayerMock);
        }
    }

    @Nested
    class IsDone {

        @Test
        void isDoneIsFalseSoThePassRunsForTheSession() {
            // Rows go on moving for as long as the player keeps arranging their bar, so a pass that
            // ended would leave every arrangement made after it unhealed.
            assertThat(new MapLayerPickUpkeep().isDone())
                .isFalse();
        }

        @Test
        void isDoneIsFalseEvenAfterAFailure() {

            registerARosterWhoseLayerRefusesItsOwnId();

            var screenPicks = buildScreenPicks();
            var upkeep = new MapLayerPickUpkeep(() -> List.of(screenPicks));

            upkeep.advance(PAUSED_FRAME);

            // A fault on one row is not a reason to stop reading the next. What keeps a broken
            // roster from repeating is the row being recorded as done with, not the pass ending -
            // one that ended could not be put back by the player arranging their bar afresh.
            assertThat(upkeep.isDone())
                .isFalse();
        }
    }

    @Nested
    class RunWhilePaused {

        @Test
        void runWhilePausedIsTrueSoTheHealReachesTheScreensThatCanMoveARow() {
            // Every screen the bar draws on pauses the campaign, so a pass that stood down while
            // paused would run on none of the frames it exists for.
            assertThat(new MapLayerPickUpkeep().runWhilePaused())
                .isTrue();
        }
    }

    // The roster the heal is about: the empty view beside a layer that paints, which is the shape
    // every install ships.
    private void registerTheEmptyViewBesideALayerThatPaints() {

        when(paintingLayerMock.getId())
            .thenReturn(PAINTING_LAYER_ID);

        MapLayerRosters.replaceRosterWith(NoLayer.INSTANCE, paintingLayerMock);
    }

    // One screen carrying a control of its own, which is what withholds the empty view's tab from it.
    private static ScreenLayerPicks buildScreenPicks() {

        var screenPicks = new ScreenLayerPicks(
            mock(ActiveLayerSelection.class),
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
            ScreenMemoryScopes.createStandInScreen());

        screenPicks.layerVisibility().recordControlAttached();

        return screenPicks;
    }

    // The same screen still set to the tab its control has taken over, which is a pick its row offers
    // no place for and so the state the heal acts on. Its pick actually holds what is written to it,
    // since the heal reads back what it wrote to say whether the screen settled - a stand-in that
    // answered the same tab forever would report every heal as having failed.
    private static ScreenLayerPicks buildScreenPicksOnTheEmptyView() {

        var screenPicks = buildScreenPicks();
        var heldPick = new AtomicReference<MapLayer>(NoLayer.INSTANCE);

        when(screenPicks.layerSelection().getActiveLayer())
            .thenAnswer(read -> heldPick.get());

        doAnswer(selection -> {
            heldPick.set(selection.getArgument(0));
            return null;
        }).when(screenPicks.layerSelection()).selectLayer(any());

        return screenPicks;
    }

    // A screen already sitting on a tab its row offers, which is the settled state a frame with
    // nothing to do starts from - and the one that costs a single pick read, the heal answering
    // before it writes anything to read back.
    private ScreenLayerPicks buildScreenPicksAlreadyOnTheLayerThatPaints() {

        var screenPicks = buildScreenPicks();
        var heldPick = new AtomicReference<MapLayer>(paintingLayerMock);

        when(screenPicks.layerSelection().getActiveLayer())
            .thenAnswer(read -> heldPick.get());

        doAnswer(selection -> {
            heldPick.set(selection.getArgument(0));
            return null;
        }).when(screenPicks.layerSelection()).selectLayer(any());

        return screenPicks;
    }

    // The same screen over a pick that reports what it was asked for and drops what is written to
    // it, which is what a selection with no save behind it does.
    private static ScreenLayerPicks buildScreenPicksOnTheEmptyView(AtomicReference<MapLayer> heldPick) {

        var screenPicks = buildScreenPicks();

        when(screenPicks.layerSelection().getActiveLayer())
            .thenAnswer(read -> heldPick.get());

        return screenPicks;
    }

    // A roster whose one layer refuses to answer its own ID, which is how a foreign mod's layer takes
    // the row read down with it - and the only fault this pass can actually have.
    private void registerARosterWhoseLayerRefusesItsOwnId() {
        registerARosterWhoseLayerRefusesItsOwnId(new AtomicInteger());
    }

    // The same, counting how often the ID was asked for, so a case can tell one attempt from sixty.
    //
    // Registered while the layer still answers, the registry reading every ID to place its layer: a
    // layer that refused from the outset could not get onto a bar to break one. What is posed is a
    // read that starts failing later, which is what a layer reaching a settings file or a sector for
    // its own name does.
    private void registerARosterWhoseLayerRefusesItsOwnId(AtomicInteger idReadCount) {

        when(paintingLayerMock.getId())
            .thenReturn(PAINTING_LAYER_ID);

        MapLayerRosters.replaceRosterWith(paintingLayerMock);

        when(paintingLayerMock.getId())
            .thenAnswer(read -> {
                idReadCount.getAndIncrement();
                throw new IllegalStateException("this layer will not say what it is called");
            });
    }
}
