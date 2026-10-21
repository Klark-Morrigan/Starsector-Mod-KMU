package kmu.maplayers.ownermap.render.labels;

import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;
import kmu.maplayers.ownermap.render.style.StandInCategoryFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the name-style lookup: a category the layer styled answers its own style, and one it gave no
 * style answers the style that draws nothing rather than borrowing another category's.
 */
final class OwnerNameStylesTests {

    private static final ElementStyle OWNED_NAME_STYLE =
        new ElementStyle(OwnerPaletteSlot.PRIMARY, 0.25);

    @Nested
    class ResolveNameStyleOf {

        @Test
        void answersTheStyleTheLayerGaveTheCategory() {

            var nameStyles = new OwnerNameStyles(Map.of(StandInCategoryFake.OWNED, OWNED_NAME_STYLE));

            assertThat(nameStyles.resolveNameStyleOf(StandInCategoryFake.OWNED))
                .isEqualTo(new ElementStyle(OwnerPaletteSlot.PRIMARY, 0.25));
        }

        @Test
        void answersNotDrawnForACategoryTheLayerGaveNoStyle() {

            var nameStyles = new OwnerNameStyles(Map.of(StandInCategoryFake.OWNED, OWNED_NAME_STYLE));

            assertThat(nameStyles.resolveNameStyleOf(StandInCategoryFake.RECEDED))
                .isEqualTo(ElementStyle.NOT_DRAWN);
        }
    }
}
