package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KMU_ModPluginTest {
    @Test
    void exposesStableModIdentityConstants() {
        assertThat(KMU_ModPlugin.MOD_ID).isEqualTo("klark_morrigans_utilities");
        assertThat(KMU_ModPlugin.MOD_NAME).isEqualTo("Klark Morrigan's Utilities");
    }

    @Test
    void extendsStarsectorBaseModPlugin() {
        assertThat(new KMU_ModPlugin()).isInstanceOf(BaseModPlugin.class);
    }
}
