package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.input.PointerButtonHold;
import kmlib.starsector.ui.input.VanillaPointerButtonHold;

/**
 * The cover a held left button lays over the map: while the button is down the pointer is pressing
 * rather than pointing, and what it is pressing on is the map's own business until it comes back
 * up.
 *
 * Written for the menu the sector map opens on a left press over a system marker - the short column
 * offering "Show system info", "Go to system map" and "Lay in course" - which lives exactly as long
 * as the hold, acts on whichever row the release lands over, and consumes every input event
 * meanwhile, mouse moves included, so the vanilla widgets beneath it fall quiet unprompted.
 *
 * <p>A hover does not fall quiet with them, never having joined that chain: it polls the pointer,
 * so a consumed event is not something it can be informed by. Hence the button is read rather than
 * the menu, which is the safer of the two as well as the cheaper. The menu is a transient child of
 * the widget tree's top, drawn inside the surface the chrome cover measures and above everything
 * else drawn with it, so no geometry this set already reads can find it, and finding it would mean
 * naming an obfuscated class whose identity is a fact about one game build - while the hold opens
 * before the menu is built and closes after it is gone, leaving no frame on which the menu stands
 * over an uncovered map.
 *
 * <p>What it covers besides is a left-drag pan, which is wanted: the pointer is still while the
 * sector slides beneath it, so every cell crossing under it would otherwise arrive as a fresh hover
 * and sound its own tick. No cursor test, for the reason the pause menu needs none - a button held
 * is held wherever the pointer is - which leaves the cheapest read in the set, a polled flag with
 * no screen, no geometry and no widget tree behind it, and that is what puts it first in the order.
 * The read arrives as a port rather than as the static behind it, so the rule this class owns can
 * be stated without a mouse to hold.
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
