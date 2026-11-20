package qouteall.imm_ptl.core.render.view;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Oblique near-plane clipping (Eric Lengyel, "Modifying the Projection Matrix to Perform Oblique Near-Plane Clipping").
 * <p>
 * The portal view must not show the things between the (virtual) camera and the portal's destination surface.
 * The old renderer clipped them with gl_ClipDistance via shader patching (OpenGL only).
 * Moving the near plane onto the portal plane does the same with no shader changes,
 * so it works for vanilla shaders, Sodium and both the OpenGL and Vulkan backends.
 * <p>
 * Minecraft 26.x uses reversed-Z: the near plane maps to z_clip = w_clip and the far plane to
 * z_clip = 0 (zero-to-one depth) or z_clip = -w_clip (OpenGL depth range).
 */
public final class ObliqueProjection {
    private ObliqueProjection() {}

    /**
     * @param projection   The original reversed-Z projection matrix.
     * @param clipPlaneEye The clipping plane (a, b, c, d) in eye space.
     *                     The points satisfying a*x + b*y + c*z + d >= 0 are kept.
     * @param zZeroToOne   Whether the device uses [0, 1] clip-space depth range.
     * @return The modified projection, or null if the camera is on the kept side (oblique clipping not applicable).
     */
    public static @Nullable Matrix4f makeOblique(Matrix4fc projection, Vector4f clipPlaneEye, boolean zZeroToOne) {
        // the camera (eye-space origin) must be on the clipped side of the plane
        if (clipPlaneEye.w >= -1e-4f) {
            return null;
        }

        Matrix4f inverse = projection.invert(new Matrix4f());

        // transform the plane into clip space: C' = (P^-1)^T * C
        Vector4f clipSpacePlane = new Vector4f(clipPlaneEye);
        inverse.transformTranspose(clipSpacePlane);

        // the far-plane corner that is farthest along the plane direction
        float farZ = zZeroToOne ? 0f : -1f;
        Vector4f corner = new Vector4f(
            Math.signum(clipSpacePlane.x),
            Math.signum(clipSpacePlane.y),
            farZ,
            1f
        );
        inverse.transform(corner);

        // row 3 (w row) of the projection
        Vector4f wRow = new Vector4f(projection.m03(), projection.m13(), projection.m23(), projection.m33());

        float cDotQ = clipPlaneEye.dot(corner);
        if (Math.abs(cDotQ) < 1e-12f) {
            return null;
        }
        float wDotQ = wRow.dot(corner);

        // new z row = wRow - a * C
        // zero-to-one: the far plane z_c = 0 passes through the corner: (wRow - a C) . Q = 0
        // OpenGL range: the far plane z_c = -w_c passes through the corner: 2 wRow . Q - a C . Q = 0
        float a = zZeroToOne ? (wDotQ / cDotQ) : (2 * wDotQ / cDotQ);
        if (!(a > 0) || Float.isInfinite(a)) {
            return null;
        }

        Matrix4f result = new Matrix4f(projection);
        // JOML: mXY = column X, row Y
        result.m02(wRow.x - a * clipPlaneEye.x);
        result.m12(wRow.y - a * clipPlaneEye.y);
        result.m22(wRow.z - a * clipPlaneEye.z);
        result.m32(wRow.w - a * clipPlaneEye.w);
        return result;
    }
}
