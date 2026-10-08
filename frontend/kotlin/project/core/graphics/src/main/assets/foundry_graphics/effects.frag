#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec4 viewport;
uniform vec4 interaction;
uniform vec4 content;
uniform vec4 samples[12];

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
vec3 shade(vec2 pixel, vec4 viewport, vec4 interaction, vec4 content) {
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
    } else if (interaction.z < 1.5) {
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
    } else if (interaction.z < 2.5) {
        // Smooth ribbons: a quiet hero backdrop rather than a simulated fluid.
        vec2 p = uv + (interaction.xy - 0.5) * vec2(-0.35, 0.35);
        float drift = time * 0.22;
        float warp = sin(p.y * 3.0 + drift) * 0.22;
        float ribbon = sin((p.x + warp) * 5.0 + drift);
        float fold = sin(p.y * 4.0 - drift + ribbon * 1.4);
        float light = pow(0.5 + 0.5 * fold, 3.0);
        color = mix(navy, vec3(0.22, 0.27, 0.62), 0.5 + 0.35 * ribbon);
        color += mix(vec3(0.22, 0.50, 0.95), vec3(0.95, 0.55, 0.38), 0.5 + 0.5 * ribbon) * light * (0.2 + 0.65 * strength);
        color += vec3(0.4, 0.62, 0.9) * exp(-7.0 * dot(p, p)) * 0.16;
    } else if (interaction.z < 3.5) {
        // Bounded rounded card; drag steers its apparent tilt and reflection.
        vec2 tilt = (interaction.xy - 0.5) * vec2(1.0, -1.0);
        vec2 p = turn(uv, tilt.x * 0.16);
        p.x *= 1.0 + abs(tilt.x) * 0.25;
        p.y *= 1.0 + abs(tilt.y) * 0.25;
        vec2 q = abs(p) - vec2(0.43, 0.25);
        float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - 0.045;
        float aa = 1.5 / res.y;
        float mask = 1.0 - smoothstep(0.0, aa, distance);
        float reflection = p.x + p.y * 0.7 - tilt.x * 0.8 + tilt.y * 0.5 + sin(time * 0.4) * 0.06;
        float sheen = exp(-24.0 * reflection * reflection);
        vec3 iridescence = 0.5 + 0.5 * cos(vec3(0.0, 2.1, 4.2) + (p.x - p.y + tilt.x) * 5.0);
        vec3 card = mix(vec3(0.07, 0.09, 0.16), iridescence * 0.45, strength);
        card += vec3(0.75, 0.84, 1.0) * sheen * (0.15 + strength * 0.55);
        float edge = 1.0 - smoothstep(0.0, 0.009, abs(distance + 0.009));
        card += vec3(0.6, 0.72, 0.94) * edge * 0.55;
        // Embossed bands suggest a membership surface without baking in product copy.
        float bands = (1.0 - smoothstep(0.003, 0.006, abs(p.y + 0.14))) * (1.0 - smoothstep(0.2, 0.32, abs(p.x)));
        card += vec3(0.4, 0.5, 0.65) * bands * 0.35;
        color += vec3(0.12, 0.2, 0.4) * exp(-5.0 * length(uv));
        color = mix(color, card, mask);
    } else if (interaction.z < 4.5) {
        float radius = 0.33, r = length(uv);
        float aa = 1.5 / res.y;
        float mask = 1.0 - smoothstep(radius - aa, radius, r);
        float progress = content.x;
        float wave = sin(uv.x * 17.0 + time * 2.0) * 0.018 + sin(uv.x * 29.0 - time * 1.3) * 0.009;
        float level = -radius + 2.0 * radius * progress + wave * sin(progress * 3.14159265) * strength;
        float filled = 1.0 - smoothstep(level - aa, level + aa, uv.y);
        if (progress <= 0.0) filled = 0.0;
        if (progress >= 1.0) filled = 1.0;
        vec3 liquid = mix(cobalt, sky, clamp((uv.y + radius) / (2.0 * radius), 0.0, 1.0));
        liquid += vec3(0.4, 0.7, 0.9) * exp(-500.0 * (uv.y - level) * (uv.y - level)) * strength * sin(progress * 3.14159265);
        color = mix(color, mix(vec3(0.06, 0.09, 0.15), liquid, filled), mask);
        float rim = 1.0 - smoothstep(0.002, 0.006, abs(r - radius - 0.014));
        color += vec3(0.27, 0.39, 0.6) * rim;
    } else if (interaction.z < 5.5) {
        // No renderer time: caller supplies a finite, scrubbable event playhead.
        float phase = content.x, t = phase * 2.0;
        vec2 origin = (interaction.xy * res - 0.5 * res) / res.y;
        origin.y = -origin.y;
        float visible = smoothstep(0.0, 0.035, phase) * (1.0 - smoothstep(0.75, 1.0, phase));
        for (int i = 0; i < 48; i++) {
            float index = float(i);
            // Small integer-derived seeds are stable on mediump ES 2 implementations.
            float seed = fract(index * 0.618034 + 0.13);
            float seed2 = fract(index * 0.381966 + 0.37);
            vec2 velocity = vec2((seed - 0.5) * 1.2, 0.55 + seed2 * 0.85);
            vec2 center = origin + velocity * t + vec2(0.0, -0.65 * t * t);
            vec2 p = turn(uv - center, index + t * (seed - 0.5) * 8.0);
            vec2 size = vec2(0.007 + seed2 * 0.005, 0.014 + seed * 0.01);
            float shape = 1.0 - smoothstep(0.0, 1.5 / res.y, max(abs(p.x) - size.x, abs(p.y) - size.y));
            vec3 tint = mix(sky, vec3(1.0, 0.57, 0.32), seed);
            color += tint * shape * visible * (0.3 + strength * 0.7);
        }
    } else {
        // Supplied scalar density, with contours. Empty input is a neutral grid.
        vec2 grid = abs(fract(uv * 10.0) - 0.5);
        float lines = 1.0 - smoothstep(0.012, 0.028, min(grid.x, grid.y));
        color = navy + vec3(0.12, 0.17, 0.24) * lines * 0.2;
        float density = 0.0;
        for (int i = 0; i < 12; i++) {
            if (float(i) >= content.y) break;
            vec4 datum = samples[i];
            vec2 center = (datum.xy * res - 0.5 * res) / res.y;
            center.y = -center.y;
            vec2 delta = (uv - center) / max(0.03, datum.w);
            density += datum.z * exp(-2.0 * dot(delta, delta));
        }
        float heat = 1.0 - exp(-density * (0.5 + 2.0 * strength));
        vec3 tint = mix(cobalt, sky, smoothstep(0.0, 0.5, heat));
        tint = mix(tint, vec3(1.0, 0.58, 0.30), smoothstep(0.45, 0.95, heat));
        color = mix(color, tint, heat);
        float contour = 1.0 - smoothstep(0.018, 0.045, abs(fract(density * 5.0) - 0.5));
        color += vec3(0.35, 0.48, 0.6) * contour * smoothstep(0.02, 0.1, density) * 0.24;
    }
    return pow(clamp(color, vec3(0.0), vec3(1.0)), vec3(0.85));
}

void main() {
    // OpenGL's fragment origin is bottom-left; the public interaction contract is top-left.
    gl_FragColor = vec4(shade(vec2(gl_FragCoord.x, viewport.y - gl_FragCoord.y), viewport, interaction, content), 1.0);
}
