#version 330
#extension GL_ARB_separate_shader_objects : require

// The portal view is rendered into a texture with the same size as the screen.
// Fetch the texel under this fragment. Using the framebuffer coordinate on both
// the writing and the reading side keeps it correct on both OpenGL and Vulkan.
//
// Mirror views are rendered horizontally flipped (so that the triangle winding stays
// the same and back-face culling still works), and flipped back here.
uniform sampler2D Sampler0;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
#ifdef IMM_PTL_FLIP_X
    p.x = textureSize(Sampler0, 0).x - 1 - p.x;
#endif
    fragColor = vec4(texelFetch(Sampler0, p, 0).rgb, 1.0);
}
