package kmu.ui.geometry;

// Width and height of a UI element, without position.
public final class KmuUiSize {
    private final float width;
    private final float height;

    public KmuUiSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}
