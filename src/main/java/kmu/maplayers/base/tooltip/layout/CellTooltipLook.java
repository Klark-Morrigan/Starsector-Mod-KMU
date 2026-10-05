package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.font.installed.InstalledFaces;
import kmlib.starsector.ui.render.gl.tooltip.CursorTooltipStyle;
import kmlib.starsector.ui.render.gl.tooltip.TooltipLeaderLineStyle;
import kmlib.starsector.ui.text.TextStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineGaps;
import kmlib.starsector.ui.widgets.tooltip.TooltipStyle;

import kmu.maplayers.base.faces.ProbedText;
import kmu.maplayers.base.faces.SettledFaces;
import kmu.settings.KmuMapTooltipSettings;

import java.util.Set;

/**
 * How every map-layer hover box is set: the face each kind of line draws in, the frame around them
 * all, and how far apart they stand.
 *
 * <p>One look for every layer, so the difference between two layers' hovers is what they say about
 * the system rather than how the box is framed. Settled apart from the shape that assembles the box
 * because it answers a different kind of question - what a line looks like, against what a line is -
 * and because half of it is the player's: the densities are read live, so a slider moved with the map
 * open takes effect on the next paint rather than at the next restart.
 */
final class CellTooltipLook {

    // The two faces the box draws in, each at its own atlas's native size - which is also that row's line
    // height for the box fit. Titles are set apart from body text by typeface rather than by colour or
    // size alone because that is how the game's own tooltips are set: reusing vanilla's title-over-body
    // pairing is what makes a KM hover read as part of the interface rather than as text laid over it.
    private static final StarsectorFont HEADER_FONT = StarsectorFont.VANILLA_ORBITRON_20AA;
    private static final StarsectorFont BODY_FONT = StarsectorFont.VANILLA_INSIGNIA_15;

    // The face the box ends its key hint in - the very one the game sets its own "Press F1 for more
    // info" line in, so a KM box tells the player about a key the way every vanilla box does. Its
    // narrowness is what sets the line apart from the body; the size is not, so it is drawn at the
    // body's rather than at the atlas's own, which reads as fine print beside the body's content.
    private static final StarsectorFont FOOTNOTE_FONT = StarsectorFont.VANILLA_ORBITRON_12_CONDENSED;

    // What each face has to draw, which is what it is settled against: the heading titles a system, the
    // body names factions, systems and colonies among KMU's own words, and the foot is KMU's words alone.
    private static final Set<ProbedText> HEADER_TEXTS = Set.of(ProbedText.PLACE_NAMES, ProbedText.MOD_STRINGS);
    private static final Set<ProbedText> BODY_TEXTS = ProbedText.EVERY_KIND;

    private static final Set<ProbedText> FOOTNOTE_TEXTS = Set.of(ProbedText.MOD_STRINGS);

    // The box's own look, handed to the tooltip widget as its style: a thin bright frame over a near
    // opaque black fill, so the content reads over the map without blocking it entirely.
    private static final float BORDER_WIDTH = 1f;
    private static final float OPACITY = 0.9f;

    // The two depths the tier gap sliders are bound to. A box states the terms one listed thing's
    // number was summed from two steps under its own voice, and breaks one of those terms down a step
    // below that - so those are the runs of like lines long enough to be worth tightening, whatever a
    // given layer lists there. Named here rather than in the layer that fills them because the look is
    // shared: two layers binding the sliders to different depths would leave the same knob doing
    // different things depending on which box is open.
    private static final int TIER_2_LEVEL = 2;
    private static final int TIER_3_LEVEL = 3;

    private CellTooltipLook() {
    }

    /**
     * The look one paint draws in: the typography each kind of row takes, the shared opacity, and the
     * frame over a black fill in the map's own player palette.
     *
     * <p>Built per paint so its colours resolve live rather than being baked at class load, and so the
     * density knobs below are the player's current ones.
     *
     * <p>The heading and the body name no size: each face is a bitmap atlas crisp at exactly one size,
     * and the box has no fit of its own to squeeze text into, so a line speaking in the box's own voice
     * takes the native size and is drawn 1:1 rather than scaled. That size is the installed atlas's own,
     * read each paint, since a localised install carries a taller atlas under the same basename. Each face
     * is the one the sector settled it on, which is the face it asks for wherever the install's atlas holds
     * what it draws.
     *
     * <p>Two kinds of line are scaled off their atlas anyway, both knowingly. The note at the foot,
     * because its atlas is rasterised small enough that beside the body it reads as fine print rather
     * than as a quieter line of the same box - it takes the body's size instead, and what sets it apart
     * is its narrowness and its colours, neither of which costs it a size of its own. And any line
     * standing under that voice, by the player's own step per level, which is the whole point of asking
     * for it.
     *
     * <p>How dense the box is set is read live rather than fixed here: a box lists as much as the
     * hovered system holds, so what reads comfortably on a two-colony system and what fits on screen for
     * a twelve-colony one are not the same setting, and which of the two matters is the player's call.
     *
     * <p>The two solid marks the box draws among its glyphs - the rule from a label across to its value,
     * and the blocks a withheld name stands as - take the player's weights for the same reason the
     * leader line does: how heavy a solid run looks beside text is a judgement made on screen, at
     * whatever scale the game is run at.
     *
     * @param settledFaces the faces settled on the sector the box describes
     * @return the box's look for this paint
     */
    static CursorTooltipStyle buildStyle(SettledFaces settledFaces) {

        var bodyFace = InstalledFaces.createNativeFace(settledFaces.settleFace(BODY_FONT, BODY_TEXTS));
        var headerFace = InstalledFaces.createNativeFace(settledFaces.settleFace(HEADER_FONT, HEADER_TEXTS));
        var footnoteAtlas = settledFaces.settleFace(FOOTNOTE_FONT, FOOTNOTE_TEXTS);

        return CursorTooltipStyle.createStyle(
                TooltipStyle
                    .createStyle(
                        TextStyle.createStyle(headerFace),
                        TextStyle.createStyle(bodyFace))
                    .footnotedIn(TextStyle.createStyle(new TextFace(footnoteAtlas, bodyFace.size())))
                    .shrunkPerLevel(KmuMapTooltipSettings.getMapTooltipNestingLevelShrink())
                    .stackedAt(buildLineGaps()),
                OPACITY,
                BORDER_WIDTH,
                StarsectorUiColour.BLACK.resolve(),
                StarsectorUiColour.VANILLA_PLAYER_BASE.resolve())
            .ruledBy(buildLeaderLineStyle())
            .redactedAt(KmuMapTooltipSettings.getMapTooltipRedactionDarkeningStrength());
    }

    // How heavily the line from a label across to its value draws. Layered over KMLib's own weights
    // rather than left at them, because how heavy a solid run looks beside a line of glyphs turns on the
    // face, the size, and the atlas behind it - so where it sits against the text is a judgement made on
    // screen, at whatever scale the player runs the game at, and therefore the player's to make.
    private static TooltipLeaderLineStyle buildLeaderLineStyle() {

        return new TooltipLeaderLineStyle(
            KmuMapTooltipSettings.getMapTooltipLeaderThickness(),
            KmuMapTooltipSettings.getMapTooltipLeaderOpacity());
    }

    // How far apart the box's lines stand, by the depth of the line above the gap: the box's own spacing
    // everywhere, and the two depths a listing runs long at tightened on their own. Each gap belongs to
    // the tier just drawn, so a slider closes up a run of like lines and leaves the line that opens it
    // standing where the shallower line above it put it.
    private static TooltipLineGaps buildLineGaps() {

        return TooltipLineGaps
            .createGaps(KmuMapTooltipSettings.getMapTooltipLineGap())
            .gappedAtLevel(TIER_2_LEVEL, KmuMapTooltipSettings.getMapTooltipTier2LineGap())
            .gappedAtLevel(TIER_3_LEVEL, KmuMapTooltipSettings.getMapTooltipTier3LineGap());
    }
}
