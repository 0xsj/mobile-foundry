#include <metal_stdlib>
using namespace metal;
// Two aligned float4 values: pixel width/height, time, strength; top-left point, effect, step budget.
struct Uniforms { float4 viewport; float4 interaction; };
struct VertexOut { float4 position [[position]]; };
vertex VertexOut effectVertex(uint id [[vertex_id]]) {
    float2 positions[3] = { float2(-1.0, -1.0), float2(3.0, -1.0), float2(-1.0, 3.0) };
    VertexOut result; result.position = float4(positions[id], 0.0, 1.0); return result;
}

float2 turn(float2 p, float a) {
    float c = cos(a), s = sin(a);
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}
float scene(float3 p, float time, float2 point, float strength) {
    float3 q = p;
    q.xz = turn(q.xz, time * 0.45 + (point.x - 0.5) * 3.14);
    q.xy = turn(q.xy, 0.65 + (point.y - 0.5) * 2.4);
    float sphere = length(p) - 0.55 - 0.025 * strength * sin(p.x * 12.0 + time) * sin(p.y * 12.0 - time);
    float ring = length(float2(length(q.xz) - 0.90, q.y)) - 0.095;
    return min(sphere, ring);
}
float3 shade(float2 pixel, float4 viewport, float4 interaction) {
    float2 res = viewport.xy;
    float time = viewport.z, strength = viewport.w;
    float2 uv = (pixel - 0.5 * res) / res.y;
    uv.y = -uv.y;
    float3 navy = float3(0.025, 0.045, 0.09);
    float3 cobalt = float3(0.16, 0.32, 0.95);
    float3 sky = float3(0.18, 0.85, 1.0);
    float3 color = navy + cobalt * 0.08 * exp(-2.0 * length(uv));
    if (interaction.z < 0.5) {
        float2 center = (interaction.xy * res - 0.5 * res) / res.y;
        center.y = -center.y;
        float r = length(uv - center);
        float rings = pow(0.5 + 0.5 * sin(34.0 * r - 4.0 * time), 7.0) * exp(-1.8 * r);
        float flow = 0.5 + 0.5 * sin(uv.x * 7.0 + sin(uv.y * 8.0 + time) + time);
        float2 grid = abs(fract(uv * 10.0) - 0.5);
        float lines = 1.0 - smoothstep(0.015, 0.035, min(grid.x, grid.y));
        color += cobalt * rings * (0.25 + strength) + sky * flow * 0.16 + sky * lines * 0.035;
        color += sky * exp(-80.0 * r * r) * 0.4;
    } else {
        float3 ro = float3(0.0, 0.0, 3.0);
        float3 rd = normalize(float3(uv, -1.8));
        float distance = 0.0;
        float d = 1.0;
        for (int i = 0; i < 64; i++) {
            if (float(i) >= interaction.w) break;
            d = scene(ro + rd * distance, time, interaction.xy, strength);
            if (d < 0.002 || distance > 6.0) break;
            distance += d * 0.85;
        }
        if (d < 0.002 && distance < 6.0) {
            float3 p = ro + rd * distance;
            float2 e = float2(0.003, 0.0);
            float3 n = normalize(float3(
                scene(p + e.xyy, time, interaction.xy, strength) - scene(p - e.xyy, time, interaction.xy, strength),
                scene(p + e.yxy, time, interaction.xy, strength) - scene(p - e.yxy, time, interaction.xy, strength),
                scene(p + e.yyx, time, interaction.xy, strength) - scene(p - e.yyx, time, interaction.xy, strength)));
            float3 light = normalize(float3(-0.5, 0.8, 1.0));
            float diffuse = max(dot(n, light), 0.0);
            float specular = pow(max(dot(n, normalize(light - rd)), 0.0), 48.0);
            float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
            float3 base = mix(cobalt, sky, 0.5 + 0.5 * sin(p.y * 8.0 + time));
            color = base * (0.15 + diffuse * 0.75) + float3(0.85, 0.94, 1.0) * specular * (0.3 + strength) + sky * rim * 0.45;
        }
    }
    return pow(clamp(color, float3(0.0), float3(1.0)), float3(0.85));
}

fragment half4 effectFragment(VertexOut in [[stage_in]], constant Uniforms &u [[buffer(0)]]) {
    return half4(half3(shade(in.position.xy, u.viewport, u.interaction)), 1.0h);
}
