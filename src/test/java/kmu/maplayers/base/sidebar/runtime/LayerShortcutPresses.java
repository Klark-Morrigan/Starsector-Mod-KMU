package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRosters;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * One shortcut press against one registered layer, arranged the same way whichever host takes it.
 *
 * <p>The two cases it serves are deliberately parallel - each pins that its host writes its own screen's
 * pick and no other's, so a host wired to the neighbouring screen's selection fails one of them - and an
 * arrangement written out twice is exactly where that parallel stops holding quietly: one suite left
 * registering a layer bound to a different key, or asserting a different ID, goes on passing while
 * testing something else.
 *
 * <p>The host arrives as a supplier rather than as a value because a host resolves the fold it opens at
 * out of sector memory, so it cannot be built before the running game is stood in for. The assertion
 * arrives as a callback for the matching reason at the other end: a {@link org.mockito.MockedStatic}
 * cannot be verified once it has closed, so the caller's claim has to be made inside the stubbing.
 */
final class LayerShortcutPresses {

    /** The registered layer's ID - what a host writes into its own screen's active-layer slot. */
    static final String LAYER_ID = "political_map";

    // The key that layer answers to, and the one the press carries. LWJGL's KEY_P, but arbitrary here:
    // what these cases turn on is which slot the press writes, not which key reached it.
    private static final int SHORTCUT_KEYCODE = 25;

    private LayerShortcutPresses() {
    }

    // Registers one bound layer, presses its key on the given host, and hands the caller the sector
    // memory the press wrote through.
    static void pressTheBoundKeyOn(Supplier<SidebarHost> host, Consumer<MemoryAPI> assertion) {

        var layerMock = mock(MapLayer.class);

        when(layerMock.getId())
            .thenReturn(LAYER_ID);
        when(layerMock.resolveShortcutKeycode())
            .thenReturn(SHORTCUT_KEYCODE);

        // The registry is static, so a neighbour's layers would otherwise outlive their test.
        MapLayerRosters.replaceRosterWith(layerMock);

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.getEventValue())
            .thenReturn(SHORTCUT_KEYCODE);

        try (var globalMock = mockStatic(Global.class)) {

            var memoryMock = mock(MemoryAPI.class);
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getMemoryWithoutUpdate())
                .thenReturn(memoryMock);
            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            host.get().handleKeyPress(eventMock);

            assertion.accept(memoryMock);
        }
    }
}
