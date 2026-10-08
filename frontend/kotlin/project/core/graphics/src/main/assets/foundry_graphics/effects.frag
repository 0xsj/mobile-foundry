#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec4 viewport;
uniform vec4 interaction;

vec2 turn(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}
float scene(vec3 p, float time, vec2 point, float strength) {
    vec3 q = p;
    q.xz = turn(q.xz, time * 0.45 + (point.x - 0.5) * 3.14);
    q.xy = turn(q.xy, 0.65 + (point.y - 0.5) * 2.4);
    float sphere = length(p) - 0.55 - 0.025 * strength * sin(p.x * 12.0 + time) * sin(p.y * 12.0 - time);
    float ring = length(vec2(length(q.xz) - 0.90, q.y)) - 0.095;
    return min(sphere, ring);
}
vec3 shade(vec2 pixel, vec4 viewport, vec4 interaction) {
    vec2 res = viewport.xy;
    float time = viewport.z, strength = viewport.w;
    vec2 uv = (pixel - 0.5 * res) / res.y;
    uv.y = -uv.y;
    vec3 navy = vec3(0.025, 0.045, 0.09);
    vec3 cobalt = vec3(0.16, 0.32, 0.95);
    vec3 sky = vec3(0.18, 0.85, 1.0);
    vec3 color = navy + cobalt * 0.08 * exp(-2.0 * length(uv));
    if (interaction.z < 0.5) {
        vec2 center = (interaction.xy * res - 0.5 * res) / res.y;
        center.y = -center.y;
        float r = length(uv - center);
        float rings = pow(0.5 + 0.5 * sin(34.0 * r - 4.0 * time), 7.0) * exp(-1.8 * r);
        float flow = 0.5 + 0.5 * sin(uv.x * 7.0 + sin(uv.y * 8.0 + time) + time);
        vec2 grid = abs(fract(uv * 10.0) - 0.5);
        float lines = 1.0 - smoothstep(0.015, 0.035, min(grid.x, grid.y));
        color += cobalt * rings * (0.25 + strength) + sky * flow * 0.16 + sky * lines * 0.035;
        color += sky * exp(-80.0 * r * r) * 0.4;
    } else {
        vec3 ro = vec3(0.0, 0.0, 3.0);
        vec3 rd = normalize(vec3(uv, -1.8));
        float distance = 0.0;
        float d = 1.0;
        for (int i = 0; i < 64; i++) {
            if (float(i) >= interaction.w) break;
            d = scene(ro + rd * distance, time, interaction.xy, strength);
            if (d < 0.002 || distance > 6.0) break;
            distance += d * 0.85;
        }
        if (d < 0.002 && distance < 6.0) {
            vec3 p = ro + rd * distance;
            vec2 e = vec2(0.003, 0.0);
            vec3 n = normalize(vec3(
                scene(p + e.xyy, time, interaction.xy, strength) - scene(p - e.xyy, time, interaction.xy, strength),
                scene(p + e.yxy, time, interaction.xy, strength) - scene(p - e.yxy, time, interaction.xy, strength),
                scene(p + e.yyx, time, interaction.xy, strength) - scene(p - e.yyx, time, interaction.xy, strength)));
            vec3 light = normalize(vec3(-0.5, 0.8, 1.0));
            float diffuse = max(dot(n, light), 0.0);
            float specular = pow(max(dot(n, normalize(light - rd)), 0.0), 48.0);
            float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
            vec3 base = mix(cobalt, sky, 0.5 + 0.5 * sin(p.y * 8.0 + time));
            color = base * (0.15 + diffuse * 0.75) + vec3(0.85, 0.94, 1.0) * specular * (0.3 + strength) + sky * rim * 0.45;
        }
    }
    return pow(clamp(color, vec3(0.0), vec3(1.0)), vec3(0.85));
}

void main() {
    // OpenGL's fragment origin is bottom-left; the public interaction contract is top-left.
    gl_FragColor = vec4(shade(vec2(gl_FragCoord.x, viewport.y - gl_FragCoord.y), viewport, interaction), 1.0);
}
