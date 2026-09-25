package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FumoHeadSpinTest {
    private static final float EPSILON = 0.00001F;

    @Test
    void centerStaysAboveTheHeadThroughoutAFullRevolution() {
        for (int angle = 0; angle <= 360; angle += 6) {
            var state = headModel(SpinningFumoBakedModel.spinningTransform(new Matrix4f(), angle));

            List<Vector3f> points = positions(state);
            assertPosition(points.getFirst(), 0.0F, 14.0F / 16.0F, 0.0F);
            assertEquals(0.25F, points.get(1).distance(points.getFirst()), EPSILON);
        }
    }

    @Test
    void quarterTurnRotatesTheFrontWithoutMovingTheCenter() {
        var state = headModel(SpinningFumoBakedModel.spinningTransform(new Matrix4f(), 90.0F));

        assertPosition(positions(state).get(1), -0.25F, 14.0F / 16.0F, 0.0F);
    }

    @Test
    void existingLocalTransformIsAppliedBeforeTheSpin() {
        Matrix4f local = new Matrix4f().translation(0.125F, 0.2F, -0.1F);
        var state = headModel(SpinningFumoBakedModel.spinningTransform(local, 90.0F));

        assertPosition(positions(state).getFirst(), -0.1F, 14.0F / 16.0F + 0.2F, -0.125F);
        assertPosition(local.getTranslation(new Vector3f()), 0.125F, 0.2F, -0.1F);
    }

    private static ItemStackRenderState headModel(Matrix4f local) {
        var state = new ItemStackRenderState();
        addHeadLayer(state, local);
        return state;
    }

    private static void addHeadLayer(ItemStackRenderState state, Matrix4f local) {
        var layer = state.newLayer();
        layer.setItemTransform(new ItemTransform(
                new Vector3f(), new Vector3f(0, 14.0F / 16.0F, 0), new Vector3f(1)));
        layer.setLocalTransform(local);
        layer.setExtents(() -> new Vector3fc[] {
                new Vector3f(0.5F, 0.5F, 0.5F), new Vector3f(0.5F, 0.5F, 0.25F)
        });
    }

    private static List<Vector3f> positions(ItemStackRenderState state) {
        var points = new ArrayList<Vector3f>();
        state.visitExtents(point -> points.add(new Vector3f(point)));
        return points;
    }

    private static void assertPosition(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, EPSILON, "x");
        assertEquals(y, actual.y, EPSILON, "y");
        assertEquals(z, actual.z, EPSILON, "z");
    }
}
