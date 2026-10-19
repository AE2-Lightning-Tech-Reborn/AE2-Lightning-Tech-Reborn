package com.moakiee.ae2lt.client.compat;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;

public final class LegacyMath {
    private LegacyMath() {}
    public static Vector3f transformPosition(Matrix4f matrix, Vector3f point) {
        var result = new Vector4f(point.x(), point.y(), point.z(), 1);
        result.transform(matrix);
        point.set(result.x(), result.y(), result.z());
        return point;
    }
}
