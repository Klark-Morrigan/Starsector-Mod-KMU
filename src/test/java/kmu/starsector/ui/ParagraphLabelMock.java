package kmu.starsector.ui;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The label a paragraph's highlights are set on, for a surface that is standing in for a tooltip.
 *
 * <p>The engine hands a label back from {@code addPara} and the highlighted runs are routed through
 * it rather than through the surface, so a stand-in answering with nothing has nothing to tint - and
 * a paragraph added to one fails on the way in. That is a fact about the engine's shape rather than
 * about any one subject, which is why it is answered once here.
 */
public final class ParagraphLabelMock {

    private ParagraphLabelMock() {
    }

    /**
     * Stubs {@code tooltipMock} to answer paragraph additions with a label.
     *
     * @param tooltipMock the surface standing in for a tooltip
     * @return the label its paragraphs are tinted through
     */
    public static LabelAPI mockLabelOn(TooltipMakerAPI tooltipMock) {

        var labelMock = mock(LabelAPI.class);

        when(tooltipMock.addPara(anyString(), any(Color.class), anyFloat()))
            .thenReturn(labelMock);

        return labelMock;
    }
}
