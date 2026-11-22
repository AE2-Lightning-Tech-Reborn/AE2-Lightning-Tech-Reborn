package com.moakiee.ae2lt.client.core;

import java.nio.ByteOrder;
import java.util.function.Consumer;

import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

final class CoreEffectMesh {
    private static final int SPHERE_LONGITUDES = 24;
    private static final int SPHERE_LATITUDES = 12;
    private static final int RING_SEGMENTS = 48;

    private CoreEffectMesh() {
    }

    static void sphere(PoseStack stack, VertexConsumer consumer, float radius,
                       float r, float g, float b, float alpha) {
        var pose = stack.last();
        for (var point : UnitSphere.POINTS) {
            consumer.addVertex(pose.pose(), point.x * radius, point.y * radius, point.z * radius)
                    .setColor(r, g, b, alpha)
                    .setNormal(pose, point.x, point.y, point.z);
        }
    }

    private static final class UnitSphere {
        private static final Vector3f[] POINTS = buildPoints();

        private static Vector3f[] buildPoints() {
            var points = new Vector3f[SPHERE_LATITUDES * SPHERE_LONGITUDES * 6];
            int index = 0;
            for (int latitude = 0; latitude < SPHERE_LATITUDES; latitude++) {
                double theta0 = -Math.PI * 0.5D + Math.PI * latitude / SPHERE_LATITUDES;
                double theta1 = -Math.PI * 0.5D + Math.PI * (latitude + 1) / SPHERE_LATITUDES;
                for (int longitude = 0; longitude < SPHERE_LONGITUDES; longitude++) {
                    double phi0 = Math.PI * 2.0D * longitude / SPHERE_LONGITUDES;
                    double phi1 = Math.PI * 2.0D * (longitude + 1) / SPHERE_LONGITUDES;
                    var a = normal(theta0, phi0);
                    var b = normal(theta1, phi0);
                    var c = normal(theta1, phi1);
                    var d = normal(theta0, phi1);
                    points[index++] = a;
                    points[index++] = b;
                    points[index++] = c;
                    points[index++] = a;
                    points[index++] = c;
                    points[index++] = d;
                }
            }
            return points;
        }

        private static Vector3f normal(double theta, double phi) {
            // Preserve the native angle precision and each original float rounding point.
            float cosTheta = (float) Math.cos(theta);
            return new Vector3f(cosTheta * (float) Math.cos(phi), (float) Math.sin(theta),
                    cosTheta * (float) Math.sin(phi));
        }
    }

    private static Vector3f[] capturePositions(int count, Consumer<BufferBuilder> build) {
        var format = DefaultVertexFormat.POSITION_COLOR;
        int stride = format.getVertexSize();
        int position = format.getOffset(VertexFormatElement.POSITION);
        try (var buffer = new ByteBufferBuilder(count * stride)) {
            var builder = new BufferBuilder(buffer, VertexFormat.Mode.TRIANGLES, format);
            build.accept(builder);
            try (var mesh = builder.buildOrThrow()) {
                var vertices = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                var points = new Vector3f[mesh.drawState().vertexCount()];
                for (int i = 0; i < points.length; i++) {
                    int start = i * stride + position;
                    points[i] = new Vector3f(vertices.getFloat(start),
                            vertices.getFloat(start + Float.BYTES), vertices.getFloat(start + 2 * Float.BYTES));
                }
                return points;
            }
        }
    }

    static void ringBand(PoseStack stack, VertexConsumer consumer,
                         float innerRadius, float outerRadius, float halfThickness,
                         float r, float g, float b, float alpha) {
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double angle0 = segment / (double) RING_SEGMENTS * Math.PI * 2.0D;
            double angle1 = (segment + 1) / (double) RING_SEGMENTS * Math.PI * 2.0D;
            float cos0 = (float) Math.cos(angle0);
            float sin0 = (float) Math.sin(angle0);
            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);

            quad(stack, consumer,
                    ringVertex(angle0, innerRadius, halfThickness, 0, 1, 0),
                    ringVertex(angle0, outerRadius, halfThickness, 0, 1, 0),
                    ringVertex(angle1, outerRadius, halfThickness, 0, 1, 0),
                    ringVertex(angle1, innerRadius, halfThickness, 0, 1, 0),
                    r, g, b, alpha);
            quad(stack, consumer,
                    ringVertex(angle1, innerRadius, -halfThickness, 0, -1, 0),
                    ringVertex(angle1, outerRadius, -halfThickness, 0, -1, 0),
                    ringVertex(angle0, outerRadius, -halfThickness, 0, -1, 0),
                    ringVertex(angle0, innerRadius, -halfThickness, 0, -1, 0),
                    r, g, b, alpha);
            quad(stack, consumer,
                    ringVertex(angle0, outerRadius, -halfThickness, cos0, 0, sin0),
                    ringVertex(angle1, outerRadius, -halfThickness, cos1, 0, sin1),
                    ringVertex(angle1, outerRadius, halfThickness, cos1, 0, sin1),
                    ringVertex(angle0, outerRadius, halfThickness, cos0, 0, sin0),
                    r, g, b, alpha);
            quad(stack, consumer,
                    ringVertex(angle1, innerRadius, -halfThickness, -cos1, 0, -sin1),
                    ringVertex(angle0, innerRadius, -halfThickness, -cos0, 0, -sin0),
                    ringVertex(angle0, innerRadius, halfThickness, -cos0, 0, -sin0),
                    ringVertex(angle1, innerRadius, halfThickness, -cos1, 0, -sin1),
                    r, g, b, alpha);
        }
    }

    static void octahedron(PoseStack stack, VertexConsumer consumer, float radius,
                           float r, float g, float b, float alpha) {
        Vertex top = new Vertex(0.0F, radius, 0.0F, 0.0F, 1.0F, 0.0F);
        Vertex bottom = new Vertex(0.0F, -radius, 0.0F, 0.0F, -1.0F, 0.0F);
        Vertex east = new Vertex(radius, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        Vertex west = new Vertex(-radius, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F);
        Vertex south = new Vertex(0.0F, 0.0F, radius, 0.0F, 0.0F, 1.0F);
        Vertex north = new Vertex(0.0F, 0.0F, -radius, 0.0F, 0.0F, -1.0F);

        triangle(stack, consumer, top, south, east, r, g, b, alpha);
        triangle(stack, consumer, top, west, south, r, g, b, alpha);
        triangle(stack, consumer, top, north, west, r, g, b, alpha);
        triangle(stack, consumer, top, east, north, r, g, b, alpha);
        triangle(stack, consumer, bottom, east, south, r, g, b, alpha);
        triangle(stack, consumer, bottom, south, west, r, g, b, alpha);
        triangle(stack, consumer, bottom, west, north, r, g, b, alpha);
        triangle(stack, consumer, bottom, north, east, r, g, b, alpha);
    }

    static void cube(PoseStack stack, VertexConsumer consumer, float halfSize,
                     float r, float g, float b, float alpha) {
        var pose = stack.last();
        for (var vertex : UnitCube.VERTICES) {
            consumer.addVertex(pose.pose(), vertex.x * halfSize, vertex.y * halfSize, vertex.z * halfSize)
                    .setColor(r, g, b, alpha)
                    .setNormal(pose, vertex.nx, vertex.ny, vertex.nz);
        }
    }

    private static final class UnitCube {
        private static final Vertex[] VERTICES = buildVertices();

        private static Vertex[] buildVertices() {
            var points = capturePositions(36, builder -> RenderBufferUtils.shapeCube(
                    new PoseStack(), builder, -1, -1, -1, 1, 1, 1, 1, 1, 1, 1));
            // Retain the original face order and triangle diagonals for the translucent core pass.
            var faces = new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.EAST,
                    Direction.WEST, Direction.UP, Direction.DOWN};
            int[] libraryFace = {2, 0, 1, 3, 4, 5};
            int[] sideCorners = {1, 0, 4, 1, 4, 2};
            int[] capCorners = {4, 2, 1, 4, 1, 0};
            var vertices = new Vertex[36];
            for (int face = 0; face < faces.length; face++) {
                var normal = faces[face];
                var corners = normal.getAxis() == Direction.Axis.Y ? capCorners : sideCorners;
                for (int i = 0; i < corners.length; i++) {
                    var point = points[libraryFace[face] * 6 + corners[i]];
                    vertices[face * 6 + i] = new Vertex(point.x, point.y, point.z,
                            normal.getStepX(), normal.getStepY(), normal.getStepZ());
                }
            }
            return vertices;
        }
    }

    private static Vertex ringVertex(double angle, float radius, float y,
                                     float nx, float ny, float nz) {
        return new Vertex(
                radius * (float) Math.cos(angle),
                y,
                radius * (float) Math.sin(angle),
                nx, ny, nz);
    }

    private static void triangle(PoseStack stack, VertexConsumer consumer,
                                 Vertex a, Vertex b, Vertex c,
                                 float r, float g, float blue, float alpha) {
        vertex(stack, consumer, a, r, g, blue, alpha);
        vertex(stack, consumer, b, r, g, blue, alpha);
        vertex(stack, consumer, c, r, g, blue, alpha);
    }

    private static void quad(PoseStack stack, VertexConsumer consumer,
                             Vertex a, Vertex b, Vertex c, Vertex d,
                             float r, float g, float blue, float alpha) {
        triangle(stack, consumer, a, b, c, r, g, blue, alpha);
        triangle(stack, consumer, a, c, d, r, g, blue, alpha);
    }

    private static void vertex(PoseStack stack, VertexConsumer consumer, Vertex vertex,
                               float r, float g, float b, float alpha) {
        var pose = stack.last();
        consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, vertex.nx, vertex.ny, vertex.nz);
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {
    }
}
