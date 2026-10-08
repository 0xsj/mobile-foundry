#include <metal_stdlib>
using namespace metal;
struct CompositeUniforms { float4 viewport, layer, mask, style, imageInfo; };
struct CompositeVertex { float4 position [[position]]; };
vertex CompositeVertex compositeVertex(uint id [[vertex_id]]) {
    float2 p[3] = {float2(-1,-1),float2(3,-1),float2(-1,3)};
    CompositeVertex out; out.position=float4(p[id],0,1); return out;
}
#define SAMPLE_CPU(tex, uv) tex.sample(sample, uv)
#define SAMPLE_TARGET(tex, uv) tex.sample(sample, uv)
float4 gaussian(texture2d<float> image, float2 uv, float2 delta) {
    constexpr sampler sample(mag_filter::linear, min_filter::linear, address::clamp_to_edge);
    float4 result = SAMPLE_TARGET(image, uv) * 0.227027;
    result += (SAMPLE_TARGET(image, uv + delta) + SAMPLE_TARGET(image, uv - delta)) * 0.1945946;
    result += (SAMPLE_TARGET(image, uv + delta * 2.0) + SAMPLE_TARGET(image, uv - delta * 2.0)) * 0.1216216;
    result += (SAMPLE_TARGET(image, uv + delta * 3.0) + SAMPLE_TARGET(image, uv - delta * 3.0)) * 0.054054;
    result += (SAMPLE_TARGET(image, uv + delta * 4.0) + SAMPLE_TARGET(image, uv - delta * 4.0)) * 0.016216;
    return result;
}
float3 displayRGB(float3 c) {
    c = clamp(c, float3(0.0), float3(1.0));
    return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}
float4 compositeShade(float2 uv, float4 viewport, float4 layer, float4 mask, float4 style, float4 imageInfo,
    texture2d<float> source0, texture2d<float> source1, texture2d<float> source2) {
    constexpr sampler sample(mag_filter::linear, min_filter::linear, address::clamp_to_edge);
    float aspect = viewport.x / viewport.y;
    if (viewport.z < 0.5) {
        float2 source = (uv - layer.xy) * float2(aspect / (imageInfo.z / imageInfo.w), 1.0) / layer.z + 0.5;
        if (source.x < 0.0 || source.x > 1.0 || source.y < 0.0 || source.y > 1.0) return float4(0.0);
        float4 color = SAMPLE_CPU(source0, source) * layer.w;
        float2 delta = (uv - mask.xy) * float2(aspect, 1.0);
        float edge = max(mask.w, 1.5 / viewport.y);
        float coverage = 1.0 - smoothstep(mask.z - edge, mask.z + edge, length(delta));
        if (style.x > 0.5) color *= coverage;
        return color;
    }
    if (viewport.z < 2.5) {
        float2 delta = viewport.z < 1.5 ? float2(viewport.w / (4.0 * viewport.x), 0.0) : float2(0.0, viewport.w / (4.0 * viewport.y));
        return gaussian(source0, uv, delta);
    }
    float imageAspect = imageInfo.x / imageInfo.y;
    float2 fit = imageAspect > aspect ? float2(1.0, aspect / imageAspect) : float2(imageAspect / aspect, 1.0);
    float2 source = (uv - 0.5) / fit + 0.5;
    float3 base = float3(0.014, 0.019, 0.028);
    if (source.x >= 0.0 && source.x <= 1.0 && source.y >= 0.0 && source.y <= 1.0) base = SAMPLE_CPU(source0, source).rgb;
    float4 sharp = SAMPLE_TARGET(source1, uv), soft = SAMPLE_TARGET(source2, uv);
    float4 overlay = viewport.w > 0.0 ? soft : sharp;
    float3 composed;
    if (style.y < 0.5) composed = base * (1.0 - overlay.a) + overlay.rgb;
    else if (style.y < 1.5) composed = base * (1.0 - overlay.a) + base * overlay.rgb;
    else composed = base + overlay.rgb * (float3(1.0) - base);
    composed += soft.rgb * style.z;
    float3 result = displayRGB(uv.x < style.w ? base : composed);
    if (style.w > 0.0 && style.w < 1.0 && abs(uv.x - style.w) < 1.5 / viewport.x) result = float3(0.98);
    return float4(result, 1.0);
}
fragment half4 compositeFragment(CompositeVertex in [[stage_in]], constant CompositeUniforms &u [[buffer(0)]],
    texture2d<float> source0 [[texture(0)]], texture2d<float> source1 [[texture(1)]], texture2d<float> source2 [[texture(2)]]) {
    return half4(compositeShade(in.position.xy/u.viewport.xy,u.viewport,u.layer,u.mask,u.style,u.imageInfo,source0,source1,source2));
}
