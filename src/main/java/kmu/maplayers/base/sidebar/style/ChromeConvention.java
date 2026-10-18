package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.widgets.tabs.style.HotkeyStyle;
import kmlib.starsector.ui.widgets.tabs.style.TabBox;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;
import kmlib.starsector.ui.widgets.tabs.style.TabPalette;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;
import kmlib.starsector.ui.widgets.tabs.style.TextHalo;

/**
 * Everything a tab row wears because of the chrome it wears. Each part is a convention of the vanilla
 * control the row imitates, not a free choice, so all of them follow from the chrome rather than travelling
 * beside it. The band height alone is the host's, being the one dimension the two screens set apart.
 *
 * <p>One value per chrome rather than one switch per part. A chrome whose convention leaves a part out does
 * not compile, and neither does a part answered for one chrome and forgotten for the other.
 *
 * @param chrome             the surface the row's paint is laid onto
 * @param tabBox             the box the chrome stands its tabs in. A strip copies the sector map's
 *                           Sector/System row, whose tabs are a fixed box the label centres in; a raised row
 *                           copies the intel screen's buttons, which are laid to the row the layout measured
 * @param tabPalette         the paint the row wears. The engine paints a tab and a button from different
 *                           colours, so a palette shared between the two would leave one of them copying
 *                           shades its vanilla counterpart never wears. Both come from the one scheme the
 *                           panel answers to, so the rows part by which control they imitate, never by palette
 * @param hotkeyStyle        how the row marks the key it answers to. The vanilla tabs a strip copies underline
 *                           their key and the vanilla buttons a raised row copies leave theirs bare, so a row
 *                           marked one way while wearing the other chrome cannot be composed at all
 * @param tabFace            the face the row is lettered in. The tabs a strip copies and the buttons a raised
 *                           row copies are set in different faces. The layout snaps a tab to it and the paint
 *                           pass draws in it, so it is what a tab is measured by as well as what it says
 * @param textHalo           whether the labels stand inside a dark ring of themselves. It follows what the
 *                           text is drawn over, not the face: both rows are lettered in hard-edged atlases
 * @param pixelFaceSharpness how hard the row's labels read. Each row is read against the vanilla chrome it
 *                           stands beside, so the two answer it differently
 */
record ChromeConvention(
    TabChrome chrome,
    TabBox tabBox,
    TabPalette tabPalette,
    HotkeyStyle hotkeyStyle,
    TextFace tabFace,
    TextHalo textHalo,
    float pixelFaceSharpness) {

    /**
     * The tab style this convention stands at the host's band height.
     *
     * @param headerBandHeight how tall the band carrying the tabs stands
     * @return the tab style to lay the band out with and paint it from
     */
    TabStyle composeTabStyle(float headerBandHeight) {

        return new TabStyle(
            chrome,
            headerBandHeight,
            tabBox,
            tabPalette,
            hotkeyStyle,
            tabFace,
            textHalo,
            pixelFaceSharpness);
    }
}
