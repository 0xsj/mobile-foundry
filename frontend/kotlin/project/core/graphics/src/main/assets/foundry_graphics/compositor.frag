#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec4 viewport;
uniform vec4 layer;
uniform vec4 mask;
uniform vec4 style;
uniform vec4 imageInfo;
uniform sampler2D source0;
uniform sampler2D source1;
uniform sampler2D source2;
// CPU images are uploaded top row first. FBO textures have GL's bottom-left origin.
#define SAMPLE_CPU(tex, uv) texture2D(tex, uv)
#define SAMPLE_TARGET(tex, uv) texture2D(tex, vec2((uv).x, 1.0 - (uv).y))

vec4 gaussian(sampler2D image, vec2 uv, vec2 delta) {
    vec4 result = SAMPLE_TARGET(image, uv) * 0.227027;
    result += (SAMPLE_TARGET(image, uv + delta) + SAMPLE_TARGET(image, uv - delta)) * 0.1945946;
    result += (SAMPLE_TARGET(image, uv + delta * 2.0) + SAMPLE_TARGET(image, uv - delta * 2.0)) * 0.1216216;
    result += (SAMPLE_TARGET(image, uv + delta * 3.0) + SAMPLE_TARGET(image, uv - delta * 3.0)) * 0.054054;
    result += (SAMPLE_TARGET(image, uv + delta * 4.0) + SAMPLE_TARGET(image, uv - delta * 4.0)) * 0.016216;
    return result;
}
vec3 displayRGB(vec3 c) {
    c = clamp(c, vec3(0.0), vec3(1.0));
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
vec4 compositeShade(vec2 uv) {
    float aspect = viewport.x / viewport.y;
    if (viewport.z < 0.5) {
        vec2 source = (uv - layer.xy) * vec2(aspect / (imageInfo.z / imageInfo.w), 1.0) / layer.z + 0.5;
        if (source.x < 0.0 || source.x > 1.0 || source.y < 0.0 || source.y > 1.0) return vec4(0.0);
        vec4 color = SAMPLE_CPU(source0, source) * layer.w;
        vec2 delta = (uv - mask.xy) * vec2(aspect, 1.0);
        float edge = max(mask.w, 1.5 / viewport.y);
        float coverage = 1.0 - smoothstep(mask.z - edge, mask.z + edge, length(delta));
        if (style.x > 0.5) color *= coverage;
        return color;
    }
    if (viewport.z < 2.5) {
        vec2 delta = viewport.z < 1.5 ? vec2(viewport.w / (4.0 * viewport.x), 0.0) : vec2(0.0, viewport.w / (4.0 * viewport.y));
        return gaussian(source0, uv, delta);
    }
    float imageAspect = imageInfo.x / imageInfo.y;
    vec2 fit = imageAspect > aspect ? vec2(1.0, aspect / imageAspect) : vec2(imageAspect / aspect, 1.0);
    vec2 source = (uv - 0.5) / fit + 0.5;
    vec3 base = vec3(0.014, 0.019, 0.028);
    if (source.x >= 0.0 && source.x <= 1.0 && source.y >= 0.0 && source.y <= 1.0) base = SAMPLE_CPU(source0, source).rgb;
    vec4 sharp = SAMPLE_TARGET(source1, uv), soft = SAMPLE_TARGET(source2, uv);
    vec4 overlay = viewport.w > 0.0 ? soft : sharp;
    vec3 composed;
    if (style.y < 0.5) composed = base * (1.0 - overlay.a) + overlay.rgb;
    else if (style.y < 1.5) composed = base * (1.0 - overlay.a) + base * overlay.rgb;
    else composed = base + overlay.rgb * (vec3(1.0) - base);
    composed += soft.rgb * style.z;
    vec3 result = displayRGB(uv.x < style.w ? base : composed);
    if (style.w > 0.0 && style.w < 1.0 && abs(uv.x - style.w) < 1.5 / viewport.x) result = vec3(0.98);
    return vec4(result, 1.0);
}
void main() {
    gl_FragColor = compositeShade(vec2(gl_FragCoord.x, viewport.y - gl_FragCoord.y) / viewport.xy);
}
