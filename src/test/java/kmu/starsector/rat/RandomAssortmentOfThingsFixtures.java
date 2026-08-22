package kmu.starsector.rat;

import com.fs.starfarer.api.ui.SectorMapAPI;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.testfixtures.starsector.ui.layout.PositionFake;
import kmlib.testfixtures.starsector.ui.map.presence.CampaignMinimapFake;
import kmlib.testfixtures.starsector.ui.map.probes.PlacedSectorMapWidgetFake;

import java.util.List;

/**
 * Shared fixtures for the rules that hang off the compatibility mode: the mode itself in each of
 * the two states a rule holds still while testing something else, and a docked minimap to aim them
 * at.
 *
 * <p>One home for the mode because every rule here is gated on it and none of them is testing it.
 * Both states have to be composed the same way to mean anything - the engaged one needs a minimap
 * standing in for the radar as well as the switch, and the disengaged one needs that same minimap
 * so what disengages it is the switch and not the absence of a map to adapt to. Two suites spelling
 * that out apart is how a case about the switch quietly starts passing for the other reason.
 *
 * <p>The minimap builders are here for the same reason at the other end: a rule about a box varies
 * the box over a drawn widget, and a rule about a widget varies what it is drawn at over an
 * arbitrary box, so each was pinning the axis the other varied at a value of its own.
 */
public final class RandomAssortmentOfThingsFixtures {

    /** A widget the layout positioned but which shows nothing of itself. */
    public static final float DRAWN_TO_NOTHING = 0f;

    /** A widget at full opacity, for a case whose subject is anything but what it is drawn at. */
    public static final float FULLY_DRAWN = 1f;

    // Where a widget stands when a case has no opinion on it - on screen, and clear of the origin
    // so a box read back as zeroes cannot pass for this one.
    private static final Rectangle MINIMAP_BOX = new Rectangle(20f, 30f, 200f, 150f);

    // Fixtures only; never instantiated.
    private RandomAssortmentOfThingsFixtures() {
    }

    /**
     * @return the mode with both halves true - the player's switch on over a minimap standing in
     *         for the campaign radar
     */
    public static RandomAssortmentOfThingsCompatibilityMode createEngagedMode() {
        return createModeSwitchedTo(true);
    }

    /**
     * @return the mode with the switch off over that same minimap, so a case pinning what the
     *         switch does is not also pinning what a missing minimap does
     */
    public static RandomAssortmentOfThingsCompatibilityMode createDisengagedMode() {
        return createModeSwitchedTo(false);
    }

    /**
     * A map surface with no ancestry, for a case whose subject is the map itself.
     *
     * <p>The chain a live walk carries is what names an owner, and no rule reached from here reads
     * it: the mode names the mod, and what the rules ask of a surface is structural.
     *
     * @param widget the map to stand on screen
     * @return that map as a found surface
     */
    public static EmbeddedMap createEmbeddedMapOf(SectorMapAPI widget) {
        return new EmbeddedMap(widget, List.of());
    }

    /**
     * A fully drawn map widget at a named box, for a case that compares something against where
     * the minimap is.
     *
     * @param x      the box's left edge, in UI units
     * @param y      its bottom edge
     * @param width  its width
     * @param height its height
     * @return that map as a found surface
     */
    public static EmbeddedMap createMinimapDrawnAt(float x, float y, float width, float height) {
        return createEmbeddedMapOf(
            new PlacedSectorMapWidgetFake(
                new PositionFake(new Rectangle(x, y, width, height)), FULLY_DRAWN));
    }

    /**
     * A placed map widget at whatever it is currently drawn at, for a case whose subject is the
     * widget rather than a box.
     *
     * @param opacity what shows of it, from {@link #DRAWN_TO_NOTHING} to {@link #FULLY_DRAWN}
     * @return the widget itself, so a caller can hold it to compare against what a rule hands back
     */
    public static PlacedSectorMapWidgetFake createMinimapWidgetDrawnTo(float opacity) {
        return new PlacedSectorMapWidgetFake(new PositionFake(MINIMAP_BOX), opacity);
    }

    private static RandomAssortmentOfThingsCompatibilityMode createModeSwitchedTo(
            boolean isModeSwitchedOn) {

        var minimapFake = new CampaignMinimapFake();
        minimapFake.replaceRadarWithMinimap();

        return new RandomAssortmentOfThingsCompatibilityMode(() -> isModeSwitchedOn, minimapFake);
    }
}
