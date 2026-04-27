package kmu.conditions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuConditionSpecTest {
    @Test
    void rejectsNullOrBlankIds() {
        assertThatThrownBy(() -> new KmuConditionSpec(null, "Hot", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
        assertThatThrownBy(() -> new KmuConditionSpec("   ", "Hot", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
    }

    @Test
    void fallsBackToIdWhenNameIsMissing() {
        assertThat(new KmuConditionSpec("hot", null, null, true).getName())
                .isEqualTo("hot");
        assertThat(new KmuConditionSpec("hot", "   ", null, true).getName())
                .isEqualTo("hot");
    }

    @Test
    void exposesConstructorValues() {
        KmuConditionSpec spec = new KmuConditionSpec("hot", "Hot", "graphics/icons/hot.png", true);

        assertThat(spec.getId()).isEqualTo("hot");
        assertThat(spec.getName()).isEqualTo("Hot");
        assertThat(spec.getIcon()).isEqualTo("graphics/icons/hot.png");
        assertThat(spec.isPlanetary()).isTrue();
    }

    @Test
    void comparesByValue() {
        KmuConditionSpec left = new KmuConditionSpec("hot", "Hot", "graphics/icons/hot.png", true);
        KmuConditionSpec right = new KmuConditionSpec("hot", "Hot", "graphics/icons/hot.png", true);
        KmuConditionSpec different = new KmuConditionSpec("cold", "Cold", "graphics/icons/cold.png", true);

        assertThat(left).isEqualTo(right);
        assertThat(left).hasSameHashCodeAs(right);
        assertThat(left).isNotEqualTo(different);
    }
}
