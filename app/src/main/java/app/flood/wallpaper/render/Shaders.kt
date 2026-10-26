package app.flood.wallpaper.render

internal const val VERTEX_SHADER = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

// The plate is a heavily blurred album. A low-frequency warp moves those
// color pools. Fluid scale raises that frequency. It does not zoom the plate.
// A third octave, or a feedback buffer, turns the pools into ribbons or a dark smear.
internal const val FIELD_SHADER = """
precision highp float;
varying vec2 vUv;
uniform sampler2D uPlate;
uniform sampler2D uOld;
uniform highp float uTime;
uniform float uScale;
uniform float uPulse;
uniform float uEnergy;
uniform float uStrength;
uniform float uFade;
uniform float uPan;
uniform float uMode;
uniform float uDim;
uniform float uBlur;
uniform float uKick;
uniform vec2 uResolution;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, float turb) {
    float v = noise(p) * mix(0.62, 0.46, turb);
    v += noise(p * mix(2.05, 2.8, turb) + vec2(3.1, 1.7)) * mix(0.38, 0.54, turb);
    return v;
}

void main() {
    float scale = clamp(uScale, 0.2, 3.0);
    // 0 at the calm end of the slider (0.4), 1 at the turbulent end (2).
    // At 0 this is the same pool as before: frequency 0.85, push 0.62, weights 0.62 and 0.38.
    float turb = clamp((scale - 0.4) / 1.6, 0.0, 1.0);
    vec2 p = vUv * mix(0.85, 2.1, turb);

    float beat = clamp(uPulse, 0.0, 1.0) * clamp(uStrength, 0.0, 2.0);
    // The beat speeds this clock on the CPU. Adding it here and letting the
    // pulse fall would drag the field backward.
    float t = uTime * 0.34;
    vec2 q = vec2(fbm(p + vec2(t, 0.2), turb), fbm(p + vec2(2.8, t * 0.7), turb));
    float push = mix(0.62, 1.1, turb);
    // A short kick in the warp. It is not added to the clock or the sample position,
    // so the fade does not drag the field backward, and it does not brighten the plate.
    push *= 1.0 + clamp(uKick, 0.0, 1.0) * 0.12;
    vec2 warp = vec2(
        fbm(p + q * push + vec2(t * 0.85, 1.3), turb),
        fbm(p + q * push + vec2(5.1, t * 0.55), turb)
    );

    // Fixed on purpose. A scale-driven frame zooms the plate instead of stirring it.
    float frame = 1.45;
    vec2 sampleUv = (warp - 0.5) * frame + 0.5;
    sampleUv.x += uPan * 0.04;

    vec3 nowCol = texture2D(uPlate, sampleUv).rgb;
    if (uBlur > 0.001) {
        float spread = uBlur * 0.018;
        nowCol += texture2D(uPlate, sampleUv + vec2(spread, 0.0)).rgb;
        nowCol += texture2D(uPlate, sampleUv - vec2(spread, 0.0)).rgb;
        nowCol += texture2D(uPlate, sampleUv + vec2(0.0, spread)).rgb;
        nowCol += texture2D(uPlate, sampleUv - vec2(0.0, spread)).rgb;
        nowCol *= 0.2;
    }

    vec3 prevCol = texture2D(uOld, sampleUv).rgb;
    vec3 col = mix(prevCol, nowCol, clamp(uFade, 0.0, 1.0));
    if (uMode > 1.5) {
        float gray = dot(col, vec3(0.299, 0.587, 0.114));
        col = vec3(gray);
    }
    col *= 1.0 - clamp(uDim, 0.0, 0.75);
    // Brightness and color only. Moving the sample with a falling beat slides the field backward.
    float shaped = clamp(beat / 2.0, 0.0, 1.0);
    float tone = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(tone), col, 1.0 + shaped * 1.1);
    col *= 1.0 + shaped * 2.4;
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

// Full-screen copy of the small field buffer onto the window. Linear filtering is set on the texture.
internal const val BLIT_SHADER = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uImage;
void main() {
    gl_FragColor = texture2D(uImage, vUv);
}
"""
