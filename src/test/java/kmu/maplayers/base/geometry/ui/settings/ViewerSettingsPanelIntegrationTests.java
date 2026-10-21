package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.SavedValues;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the viewer's knob panel over a real {@link SavedValues} file: building it asks the viewer
 * for nothing and leaves every setting where its knob opens it, and the knob list the settings are
 * read through without a window holds every knob a section declares.
 */
final class ViewerSettingsPanelIntegrationTests {

    // The sections, each of which declares its knobs as constants and lists them.
    private static final List<Class<?>> SECTIONS = List.of(
        GlobalGeometrySection.class,
        CellGeometrySection.class,
        CellAppearanceSection.class,
        VoidV3Section.class,
        VoidV4Section.class);

    @TempDir
    private Path folder;

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(folder.resolve("viewer.json"));
    }

    @Nested
    class BuildRows {

        // A row built and a refresh fired with it is a refresh per row the panel lays out, all
        // of them before the viewer has a sector to refresh.
        @Test
        void asksTheViewerForNothing() {

            var refreshes = new RecordingViewerRefreshes();

            new ViewerSettingsPanel(new ViewerSettings(), refreshes).buildRows();

            assertThat(refreshes.listAsked())
                .isEmpty();
        }

        // The radio lists "By ownership" first, and opened on its first option it set the void's
        // inset to the cells' rule rather than the one the settings declare.
        @Test
        void opensTheVoidInsetOnNowhere() {

            var settings = new ViewerSettings();

            new ViewerSettingsPanel(settings, new RecordingViewerRefreshes()).buildRows();

            assertThat(settings.voidInsetRule)
                .isEqualTo(EdgeInsetRule.NOWHERE);
        }
    }

    @Nested
    class ListEveryKnob {

        @Test
        void holdsEveryKnobASectionDeclares()
                throws IllegalAccessException {

            var declared = new ArrayList<Object>();

            for (var section : SECTIONS) {
                for (var field : section.getDeclaredFields()) {

                    if (Modifier.isStatic(field.getModifiers())
                            && ViewerKnob.class.isAssignableFrom(field.getType())) {

                        field.setAccessible(true);
                        declared.add(field.get(null));
                    }
                }
            }

            assertThat(ViewerSettingsPanel.listEveryKnob())
                .containsExactlyInAnyOrderElementsOf(declared.stream().map(ViewerKnob.class::cast).toList());
        }
    }
}
