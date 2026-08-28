package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.input.PointerButtonHold;
import kmlib.starsector.ui.input.VanillaPointerButtonHold;

/**
 * The cover a held left button lays over the map: while the button is down the pointer is pressing
 * rather than pointing, and what it is pressing on is the map's own business until it comes back
 * up.
 *
 * <p>Written for the menu the sector map opens on a left press over a system marker - the short
 * column offering "Show system info", "Go to system map" and "Lay in course". That menu lives
 * exactly as long as the hold: the map builds it on the press and it takes itself down once the
 * button is no longer down, acting on whichever row the release lands over. While it stands it
 * consumes every input event, mouse moves included, so the vanilla widgets beneath it fall quiet
 * without being told to.
 *
 * <p>A hover does not fall quiet with them, because it never joins that chain - it polls the
 * pointer directly, so a consumed event is not something it can be informed by. Which is why what
 * is read here is the button rather than the menu. The menu is a transient child of the widget
 * tree's top, drawn inside the surface the chrome cover measures and above everything else drawn
 * with it, so no geometry this set already reads can find it; and finding it would mean naming an
 * obfuscated class whose identity is a fact about one game build.
 *
 * <p>The hold is the wider of the two conditions, which is what makes it the right one to state:
 * it opens before the menu is built and closes after the menu is gone, so no frame exists on which
 * the menu stands over an uncovered map. What it covers besides is a left-drag pan, and a hover
 * stood down through one is the better behaviour anyway - the pointer is still while the sector
 * slides beneath it, so every cell crossing under it would otherwise arrive as a fresh hover and
 * sound its own tick.
 *
 * <p>No cursor test, for the same reason the pause menu needs none: a button held is held wherever
 * the pointer is. That leaves the cheapest read in the set, a polled flag with no screen, no
 * geometry and no widget tree behind it, which is what puts it first in the order. The read arrives
 * as a port rather than as the static behind it, so the one rule this class owns can be stated
 * without a mouse to hold. The no-arg constructor is the pairing a running game gets.
 */
public final class HeldPointerMapCover implements MapCover {

    private final PointerButtonHold pointerButtonHold;

    /** Reads the live mouse - the pairing a running game gets. */
    public HeldPointerMapCover() {
        this(new VanillaPointerButtonHold());
    }

    HeldPointerMapCover(PointerButtonHold pointerButtonHold) {
        this.pointerButtonHold = pointerButtonHold;
    }

    @Override
    public boolean isCoveringCursor() {
        return pointerButtonHold.isLeftButtonHeld();
    }
}
