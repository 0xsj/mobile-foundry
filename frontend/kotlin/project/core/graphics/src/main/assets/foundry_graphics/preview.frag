#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform mediump vec4 viewport;
uniform vec4 edit;
uniform vec4 imageInfo;
uniform mediump vec4 panCamera;
uniform mediump vec4 style;
uniform sampler2D photograph;
varying mediump vec3 world;
varying mediump vec3 surfaceNormal;
varying mediump float materialSlot;
varying mediump vec3 cameraEye;

vec3 linearRGB(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c)); }
vec3 displayRGB(vec3 c) { c = max(c, vec3(0.0)); return mix(c * 12.92, 1.055 * pow(c, vec3(1.0/2.4)) - 0.055, step(vec3(0.0031308), c)); }
vec3 background(vec2 uv) {
    vec3 top = vec3(0.89,0.91,0.94), bottom = vec3(0.73,0.77,0.82);
    vec3 c = mix(top, bottom, smoothstep(0.0,1.0,uv.y));
    float shadow = exp(-pow((uv.x-0.5)/0.23,2.0)-pow((uv.y-0.82)/0.045,2.0));
    return c * (1.0-0.24*shadow);
}
vec3 materialColor(float slot, float finish) {
    if (slot > 1.5) return vec3(1.0,0.82,0.40);
    if (slot > 0.5) return vec3(0.10,0.12,0.16);
    if (finish < 0.5) return vec3(0.87,0.85,0.78);
    if (finish < 1.5) return vec3(0.13,0.27,0.68);
    return vec3(0.65,0.38,0.16);
}
vec3 productLight(vec3 p, vec3 normal, float slot, vec3 eye, float finish) {
    if (slot > 1.5) return materialColor(slot,finish);
    vec3 n=normalize(normal), v=normalize(eye-p), l=normalize(vec3(-0.6,1.1,1.3)), h=normalize(v+l);
    float nl=max(dot(n,l),0.0), nv=max(dot(n,v),0.001), nh=max(dot(n,h),0.0), vh=max(dot(v,h),0.0);
    vec3 base=linearRGB(materialColor(slot,finish));
    float metal=slot<0.5 && finish>1.5 ? 0.8 : 0.0;
    float rough=slot>0.5 ? 0.40 : (finish>1.5 ? 0.27 : 0.34);
    float a2=pow(rough,4.0), denom=nh*nh*(a2-1.0)+1.0;
    float d=a2/(3.14159*denom*denom+0.0001), k=(rough+1.0)*(rough+1.0)/8.0;
    float g=(nl/(nl*(1.0-k)+k))*(nv/(nv*(1.0-k)+k));
    vec3 f0=mix(vec3(0.04),base,metal), f=f0+(vec3(1.0)-f0)*pow(1.0-vh,5.0);
    vec3 spec=d*g*f/(4.0*nl*nv+0.001);
    vec3 diffuse=(vec3(1.0)-f)*(1.0-metal)*base/3.14159;
    vec3 color=(diffuse+spec)*nl*3.0 + base*(0.24+0.14*max(n.y,0.0));
    color+=vec3(0.16,0.20,0.30)*pow(1.0-nv,3.0)*0.25;
    return displayRGB(color/(vec3(1.0)+color));
}
vec2 imageUV(vec2 uv, vec4 viewport, vec4 image, vec4 panCamera) {
    float viewAspect=viewport.x/viewport.y, imageAspect=image.x/image.y;
    vec2 fit=imageAspect>viewAspect ? vec2(1.0,viewAspect/imageAspect) : vec2(imageAspect/viewAspect,1.0);
    return (uv-0.5-panCamera.xy)/(fit*image.z)+0.5;
}
vec3 adjusted(vec3 color, vec2 uv, vec4 edit) {
    vec3 c=linearRGB(color)*exp2(edit.x);
    float lum=dot(c,vec3(0.2126,0.7152,0.0722));
    c=mix(vec3(lum),c,edit.y);
    float radial=smoothstep(0.15,0.72,length(uv-0.5));
    c*=1.0-edit.z*radial*0.75;
    return displayRGB(clamp(c,vec3(0.0),vec3(1.0)));
}

void main() {
    vec2 uv=vec2(gl_FragCoord.x,viewport.y-gl_FragCoord.y)/viewport.xy;
    vec3 c;
    if(viewport.w<0.5) {
        vec2 source=imageUV(uv,viewport,imageInfo,panCamera);
        c=vec3(0.12,0.14,0.18);
        if(source.x>=0.0 && source.x<=1.0 && source.y>=0.0 && source.y<=1.0) {
            vec3 original=texture2D(photograph,source).rgb;
            c=uv.x<edit.w ? original : adjusted(original,source,edit);
        }
        if(edit.w>0.0 && edit.w<1.0 && abs(uv.x-edit.w)<1.5/viewport.x) c=vec3(0.98);
    } else if(viewport.w<1.5) c=background(uv);
    else c=productLight(world,surfaceNormal,materialSlot,cameraEye,style.y);
    gl_FragColor=vec4(c,1.0);
}
