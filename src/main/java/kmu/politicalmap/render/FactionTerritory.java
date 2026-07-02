package kmu.politicalmap.render;

import java.awt.Color;
import java.util.List;

/**
 * One owned faction's fill and national border, both the same GLU-resolved region
 * of the border rings so they match exactly. {@code fillTriangles} is that region as
 * a GL_TRIANGLES soup ([x, y, x, y, ...], empty when the fill is "No color");
 * {@code borderLoops} is its boundary as GL_LINE_LOOP runs (empty when the border is
 * "No color") - one loop per disjoint cluster and per enclave, with any narrow-neck
 * self-crossing resolved away. Colors are null for a "No color" choice, which skips
 * that element.
 */
record FactionTerritory(float[] fillTriangles, Color fillColor, float fillAlpha,
        List<float[]> borderLoops, Color borderColor, float borderAlpha, float borderWidth) {
}
