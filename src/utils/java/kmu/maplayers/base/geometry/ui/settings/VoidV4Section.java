package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ColourRows;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import javax.swing.JPanel;

/**
 * The v4 void construction, beside v3 rather than under it.
 *
 * <p>Each construction with its own tree and its own root. What puts them side by side is that
 * they answer one question two ways: a reader judges v4 by setting v3 down and picking it up
 * again, which wants two switches at one level rather than one pick that can only ever show
 * one of them.
 */
final class VoidV4Section extends PanelSection {

    // v4's layers: the void before anything divides it, and the shore of it a straight line
    // could arrive at.
    private static final String BARE_VOID = "showBareVoid";

    private static final String LANDABLE_FRONTAGE = "showLandableFrontageV4";

    // v4's own, beside it rather than inside it. The two constructions answer the same question
    // differently, so neither is a branch of the other - and a reader comparing them wants each
    // to fold away whole, which a shared tree cannot offer.
    private static final CollapsibleSection.SectionKeys VOID_V4_KEYS =
        CollapsibleSection.SectionKeys.forSection("voidV4");

    VoidV4Section(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildSection(
            VOID_V4_KEYS,
            "Void pockets - v4",
            new CollapsibleSection.MasterSwitch(
                false, on -> settings.showVoidV4 = on, refreshes::refreshVoidV4),
            SettingRows.buildSectionBody(this::addRows));
    }

    // v4's layers, in the same shape v3's are: a tree with a roll-up at its root and the layers
    // under it. The frontage sits last, after the layer it is a diagnostic of, as v3's does.
    //
    // The colours sit beside the switches rather than with the cells' colours. Two
    // constructions are compared by their fills, so which colour each is drawn in is part of
    // using this section rather than a decision about the palette.
    private void addRows(JPanel controls) {

        controls.add(ToggleTree.buildToggleTree(
            refreshes::refreshVoidV4,
            ToggleTree.Row.ofRollUp(
                0, "allVoidV4Layers", "Every v4 layer", BARE_VOID, LANDABLE_FRONTAGE),
            ToggleTree.Row.ofSwitch(1, new ToggleTree.Switch(
                BARE_VOID,
                "Bare void",
                true,
                on -> settings.showBareVoid = on)),
            ToggleTree.Row.ofSwitch(1, new ToggleTree.Switch(
                LANDABLE_FRONTAGE,
                "Landable frontage",
                false,
                on -> settings.showLandableFrontageV4 = on))));

        controls.add(ColourRows.buildColour(
            "bareVoidColour",
            "Bare void",
            ViewerSettings.BARE_VOID_DEFAULT,
            colour -> settings.bareVoidColour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "landableFrontageV4Colour",
            "Landable frontage",
            ViewerSettings.LANDABLE_FRONTAGE_DEFAULT,
            colour -> settings.landableFrontageV4Colour = colour,
            refreshes::repaintMap));
    }
}
