package kmu.maplayers.ownermap.render.labels;

import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.render.style.FactionPaletteSlot;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the name-style lookup: a category the layer styled answers its own style, and one it gave no
 * style answers the style that draws nothing rather than borrowing another category's.
 */
final class BlocNameStylesTests {

    private static final ElementStyle FACTION_NAME_STYLE =
        new ElementStyle(FactionPaletteSlot.PRIMARY, 0.25);

    @Nested
    class ResolveNameStyleOf {

        @Test
        void answersTheStyleTheLayerGaveTheCategory() {

            var nameStyles = new BlocNameStyles(Map.of(OwnerMapCategory.FACTION, FACTION_NAME_STYLE));

            assertThat(nameStyles.resolveNameStyleOf(OwnerMapCategory.FACTION))
                .isEqualTo(new ElementStyle(FactionPaletteSlot.PRIMARY, 0.25));
        }

        @Test
        void answersNotDrawnForACategoryTheLayerGaveNoStyle() {

            var nameStyles = new BlocNameStyles(Map.of(OwnerMapCategory.FACTION, FACTION_NAME_STYLE));

            assertThat(nameStyles.resolveNameStyleOf(OwnerMapCategory.INDEPENDENT))
                .isEqualTo(ElementStyle.NOT_DRAWN);
        }
    }
}
