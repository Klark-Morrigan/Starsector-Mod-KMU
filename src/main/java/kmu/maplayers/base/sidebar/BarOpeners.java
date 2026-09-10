package kmu.maplayers.base.sidebar;

import kmlib.starsector.graphics.StarsectorSprites;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.widgets.tabs.BandButtonSpec;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.maplayers.base.chrome.arrange.MapLayerArrangementDialog;
import kmu.maplayers.base.sidebar.style.SidebarStyles;

import java.util.List;

/**
 * The button standing past the sidebar's last tab: the one way into the dialog the player arranges that
 * bar in, there being no key bound to it. What it shows, what it does, and the box it stands in.
 *
 * <p>Its own home rather than a corner of the layout that places it, because what a control says and does
 * is not where it lands: the placement asks for a button and this answers what button that is, so an
 * asset moving or a mark changing is a change here and nothing about how a band is laid out.
 */
public final class BarOpeners {

    // The mark the bar's opener carries: the game's own storage crate, which reads as "the things you
    // keep, arranged" and needs no bundle entry to say so in every language the game ships in.
    private static final String OPENER_ICON_PATH = "graphics/factions/storage.png";

    // What the opener says in words, which is nothing: the picture is the whole of it.
    private static final String NO_LABEL = "";

    // What an image that cannot be measured is assumed to be, so a missing asset costs the drawing and
    // not the control.
    private static final float SQUARE_ICON_ASPECT = 1f;

    private BarOpeners() {
    }

    /**
     * The opener as the band's own button: its mark, the dialog a press opens, and the host's own tab
     * style boxed at the image's width.
     *
     * <p>Its own control rather than a segment of the tabs row, because the tabs row is indexed by layer
     * everywhere it is read - the click that selects, the lit tab, the shortcut walk - and a cell in it
     * that is not a layer would move all three one along.
     *
     * <p>It shows a mark rather than a word: the bar it arranges is right beside it, so a label would only
     * repeat what the picture already says, and a word wide enough to read would be wider than the control
     * needs to be. Its label is therefore empty and its width comes from the image.
     *
     * <p>Nothing lit: it is a button standing in a row of tabs, not a tab, so no pick of the player's can
     * be the one showing - and the shade its mark is washed by is resolved against that same answer, so a
     * button reading as selected would wear the row's shown look with nothing to be showing. It wears the
     * row's own look otherwise, so it reads as part of the strip it stands on.
     *
     * @param hostStyle the tab style the panel's own row is drawn in
     * @return the band button the sidebar flies after its last tab
     */
    public static BandButtonSpec buildOpenerSpec(TabStyle hostStyle) {
        return new BandButtonSpec(
            new ControlSpec.Tabs(
                List.of(NO_LABEL),
                List.of(),
                ControlSpec.NO_SELECTION,
                cell -> MapLayerArrangementDialog.INSTANCE.openDialog()),
            SidebarStyles.buildBandButtonTabStyle(hostStyle, resolveIconAspect()),
            // Stating no tint of its own, so the row's own resting label shade is the whole of the mark's
            // colour and the pointer's light lands on that: the picture fills the button, so it is what
            // has to answer the pointer rather than the fill behind it, and a colour named here would be
            // one the strip could not light through.
            new ImageSpan(OPENER_ICON_PATH));
    }

    // How wide the opener's image stands per unit of height. Read from the sprite rather than written
    // down, so the button fits whatever the asset actually is and nothing has to be corrected here when
    // the game ships a differently-proportioned one.
    //
    // A square is the fallback for an asset that will not resolve, which leaves a button the size of one
    // tab height with nothing drawn in it - a control the player can still press, where a zero width would
    // be a control that had silently left the bar.
    private static float resolveIconAspect() {

        var sprite = StarsectorSprites.loadSprite(OPENER_ICON_PATH);

        return sprite == null || sprite.getHeight() <= 0f
            ? SQUARE_ICON_ASPECT
            : sprite.getWidth() / sprite.getHeight();
    }
}
