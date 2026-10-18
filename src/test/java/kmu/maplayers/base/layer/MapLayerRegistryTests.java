package kmu.maplayers.base.layer;

import kmlib.testfixtures.logging.LogAppenderFake;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the roster's contract with stand-in layers: the tab order it hands back, how a second
 * registration under one ID is arbitrated, and the pick an untouched save resolves to.
 *
 * <p>Which layer is on the screen showing this frame is not the roster's question but the screens';
 * it is {@link MapLayerScreensTests}'s.
 */
final class MapLayerRegistryTests {

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    private MapLayerRegistry layerRegistry;

    @BeforeEach
    void registerTwoLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second layer offers itself as the default, so an untouched save resolves to it - the same
        // shape the real composition root leaves, the leading tab declining the pick.
        when(secondLayerMock.isOfferedAsDefaultPick())
            .thenReturn(true);

        layerRegistry = MapLayerRosters.createRosterOf(firstLayerMock, secondLayerMock);
    }

    @Nested
    class GetLayers {

        @Test
        void returnsTheRegisteredLayersInOrder() {

            assertThat(layerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);
        }

        @Test
        void isEmptyBeforeAnyLayerIsRegistered() {
            // A fresh roster starts from nothing, which is what lets each holder of one be built
            // without anything having to empty a roster another left behind.
            assertThat(new MapLayerRegistry().getLayers())
                .isEmpty();
        }
    }

    @Nested
    class RegisterLayer {

        @Test
        void appendsToTheRightHandEndOfTheRow() {
            // Registration order is row order, and it is all the order there is: nothing on a layer
            // states where it belongs, so a layer arriving later lands to the right of everything
            // already there.
            var thirdLayerMock = createLayerMockWithId("third");

            layerRegistry.registerLayer(thirdLayerMock);

            assertThat(layerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock, thirdLayerMock);
        }

        @Test
        void reachesARosterThatHasAlreadyBeenRead() {
            // The constraint the whole additive form is shaped by: a mod that depends on this one
            // loads after it, so its layer registers after the bar has been drawn from the roster at
            // least once. A registry that settled its row on first read would leave that mod's tab
            // off until the next load.
            var lateLayerMock = createLayerMockWithId("late");

            assertThat(layerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);

            layerRegistry.registerLayer(lateLayerMock);

            assertThat(layerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock, lateLayerMock);
        }

        @Test
        void leavesARowAlreadyHandedOutAsItWas() {
            // The row is replaced on registration rather than added to in place, so a caller midway
            // through walking the row it was handed is not walking one that grows beneath it.
            var handedRow = layerRegistry.getLayers();

            layerRegistry.registerLayer(createLayerMockWithId("late"));

            assertThat(handedRow)
                .containsExactly(firstLayerMock, secondLayerMock);
        }

        @Test
        void putsALayerSharingAnIdInThePlaceOfTheOneItReplaces() {
            // Two mods shipping one ID will happen, and both tabs would read and write the one stored
            // pick that names it. Arbitrated, the row holds one tab that the save agrees with; left
            // alone, it would hold two the save cannot tell apart.
            var replacementLayerMock = createLayerMockWithId("first");

            layerRegistry.registerLayer(replacementLayerMock);

            assertThat(layerRegistry.getLayers())
                .containsExactly(replacementLayerMock, secondLayerMock);
        }

        @Test
        void leavesTheRowAloneWhenOneLayerRegistersTwice() {
            // Not every second registration is a clash: a mod wiring from two lifecycle hooks, or a
            // composition root run again, re-registers what is already standing. The row it was
            // already in is the row it stays in.
            layerRegistry.registerLayer(firstLayerMock);

            assertThat(layerRegistry.getLayers())
                .containsExactly(firstLayerMock, secondLayerMock);
        }

        @Test
        void reportsNothingWhenOneLayerRegistersTwice() {
            // The arbitration line names the two sides of a clash, so writing one for a layer giving
            // way to itself would report a conflict between a mod and itself - and it would be
            // written on every load of any mod that registers more than once.
            assertThat(recordLinesWrittenWhileRegistering(firstLayerMock))
                .isEmpty();
        }

        @Test
        void reportsTheTwoLayersThatShareAnId() {
            // The arbitration is silent to the player, so the log is the only place a mod author
            // finds out why their tab is not the one on the bar - which makes the line naming both
            // sides the whole of what the outcome is worth.
            var replacementLayerMock = createLayerMockWithId("first");
            var writtenWarnings = recordWarningsWrittenWhileRegistering(replacementLayerMock);

            assertThat(writtenWarnings)
                .hasSize(1);
            assertThat(writtenWarnings.get(0))
                .contains("first")
                .contains(firstLayerMock.getClass().getName())
                .contains(replacementLayerMock.getClass().getName());
        }
    }

    @Nested
    class ResolveLayerById {

        @Test
        void answersTheLayerRegisteredUnderIt() {

            assertThat(layerRegistry.resolveLayerById("first"))
                .isSameAs(firstLayerMock);
        }

        @Test
        void isNullForAnIdNothingRegistered() {
            // What a save holds after the mod that shipped that layer is uninstalled, which is why
            // the answer is an absence to fall back from rather than a fault.
            assertThat(layerRegistry.resolveLayerById("removed_long_ago"))
                .isNull();
        }
    }

    @Nested
    class GetDefaultLayer {

        @Test
        void answersTheOfferingLayerRatherThanTheLeadingOne() {
            // The row leads with a layer that declines, and the pick is the one that offered.
            assertThat(layerRegistry.getDefaultLayer())
                .isSameAs(secondLayerMock);
        }

        @Test
        void answersTheEarlierOfTwoLayersOffering() {
            // Which is what makes load order settle the pick without any mod stating a rank: a
            // foreign layer offering itself arrives after the host's and loses by arriving later.
            when(firstLayerMock.isOfferedAsDefaultPick())
                .thenReturn(true);

            assertThat(layerRegistry.getDefaultLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void fallsBackToTheLeadingLayerWhereNobodyOffers() {
            // A roster of layers that all decline is a roster nobody arranged - foreign layers alone,
            // say. Answering nothing there would leave a bar of tabs with none of them lit and a map
            // that paints nothing to explain it.
            assertThat(MapLayerRosters.createRosterOf(firstLayerMock).getDefaultLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void isNullBeforeAnyLayerIsRegistered() {

            assertThat(new MapLayerRegistry().getDefaultLayer())
                .isNull();
        }
    }

    // Registers one layer with the registry's own log captured, and hands back what it wrote.
    //
    // The capture is KMLib's, which owns the attachment, the level and the additivity for the length
    // of the call: the arbitration line is written at a level the running game decides, so a capture
    // reading it off whatever level happened to be in force would report silence for a line the
    // registry did write.
    private List<String> recordLinesWrittenWhileRegistering(MapLayer layer) {
        return captureLogWhileRegistering(layer).getMessages();
    }

    // The same, kept to the warnings: every registration that lands also leaves a trace of the row it
    // made, and that line is no part of what the arbitration reports.
    private List<String> recordWarningsWrittenWhileRegistering(MapLayer layer) {

        return captureLogWhileRegistering(layer)
            .getEvents()
            .stream()
            .filter(event -> Level.WARN.equals(event.getLevel()))
            .map(LoggingEvent::getRenderedMessage)
            .toList();
    }

    private LogAppenderFake captureLogWhileRegistering(MapLayer layer) {
        return LogAppenderFake.captureLogOf(MapLayerRegistry.class, () -> layerRegistry.registerLayer(layer));
    }

    // A layer answering nothing but the ID it registers under, which is all the registration cases
    // are about: where it lands in the row, and who it displaces.
    private static MapLayer createLayerMockWithId(String layerId) {

        var layerMock = mock(MapLayer.class);

        when(layerMock.getId())
            .thenReturn(layerId);

        return layerMock;
    }
}
