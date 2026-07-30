package kmu.maplayers.base.theme;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the theme's per-category tier is keyed on the open {@link MapStyleCategory} rather
 * than on any one layer's names: a layer whose ground divides some way the political map never
 * heard of populates a theme and reads bundles back out of it exactly as the political map does.
 *
 * <p>Worth pinning because nothing else in the tree can show it. Every other caller of
 * {@link RenderStyle#categoryStyle} passes a political category, so a tier quietly narrowed back
 * to that one enum would still satisfy all of them and only fail the second layer that arrives.
 */
final class RenderStyleTest {
    private static final double MARKED_WIDTH = 3;
    private static final double OTHER_MARKED_WIDTH = 7;

    // A bundle told apart from its neighbours by its outer-border width alone, so an assertion
    // names the one component it is reading rather than a whole style. Written with the type
    // qualified: the @Nested group below is named for the method under test, which shadows the
    // simple name throughout this class.
    private static kmu.maplayers.base.theme.CategoryStyle styleMarkedBy(double outerWidth) {
        return new kmu.maplayers.base.theme.CategoryStyle(
                ElementStyle.NOT_DRAWN,
                ElementStyle.NOT_DRAWN,
                outerWidth,
                ElementStyle.NOT_DRAWN,
                0);
    }

    @Nested
    class CategoryStyle {
        @Test
        void a_layer_reads_back_the_bundle_it_stored_under_its_own_category() {
            Map<MapStyleCategory, kmu.maplayers.base.theme.CategoryStyle> categories =
                    new LinkedHashMap<>();
            categories.put(HazardCategory.IRRADIATED, styleMarkedBy(MARKED_WIDTH));
            categories.put(HazardCategory.BENIGN, styleMarkedBy(OTHER_MARKED_WIDTH));
            var theme = new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categories);

            assertThat(theme.categoryStyle(HazardCategory.IRRADIATED).outerWidth())
                    .isEqualTo(MARKED_WIDTH);
            assertThat(theme.categoryStyle(HazardCategory.BENIGN).outerWidth())
                    .isEqualTo(OTHER_MARKED_WIDTH);
        }

        @Test
        void a_category_that_is_not_an_enum_resolves_by_its_own_equality() {
            // The lookup is a map read, so a key that is equal to the stored one finds the
            // bundle even though it is a different object - which is what lets a layer resolve
            // its categories fresh per draw rather than holding the exact constants it seeded.
            Map<MapStyleCategory, kmu.maplayers.base.theme.CategoryStyle> categories =
                    new LinkedHashMap<>();
            categories.put(new NamedCategory("irradiated"), styleMarkedBy(MARKED_WIDTH));
            var theme = new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categories);

            assertThat(theme.categoryStyle(new NamedCategory("irradiated")).outerWidth())
                    .isEqualTo(MARKED_WIDTH);
        }

        @Test
        void a_category_the_theme_was_never_given_resolves_to_no_bundle() {
            // Nothing in the theme privileges any one layer's set, so an unseeded category is
            // simply absent rather than falling back to some default bundle a caller would then
            // paint without noticing.
            Map<MapStyleCategory, kmu.maplayers.base.theme.CategoryStyle> categories =
                    new LinkedHashMap<>();
            categories.put(HazardCategory.IRRADIATED, styleMarkedBy(MARKED_WIDTH));
            var theme = new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categories);

            assertThat(theme.categoryStyle(HazardCategory.BENIGN)).isNull();
        }
    }

    // A set of categories no part of the political map declares - the stand-in for whatever a
    // second layer divides the sector by. An enum, since that is the shape a layer is expected to
    // reach for.
    private enum HazardCategory implements MapStyleCategory {
        IRRADIATED,
        BENIGN
    }

    // A category carrying its identity in a component rather than in a constant, which is the
    // other shape the key allows: anything whose equals and hashCode agree between the rebuild
    // that fills the theme and the draws that read it.
    private record NamedCategory(String id) implements MapStyleCategory {
    }
}
