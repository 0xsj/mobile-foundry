#include <metal_stdlib>
using namespace metal;
struct PreviewUniforms { float4 viewport; float4 edit; float4 image; float4 panCamera; float4 style; };
struct PreviewVertex { float4 position [[position]]; float3 world; float3 normal; float slot; float3 eye; };
vertex PreviewVertex previewFull(uint id [[vertex_id]]) {
    float2 points[3]={float2(-1,-1),float2(3,-1),float2(-1,3)};
    PreviewVertex out; out.position=float4(points[id],0,1); out.world=float3(0); out.normal=float3(0,1,0); out.slot=0; out.eye=float3(0,0,4); return out;
}
vertex PreviewVertex previewMesh(uint id [[vertex_id]], const device float *data [[buffer(0)]], constant PreviewUniforms &u [[buffer(1)]]) {
    uint i=id*7; float3 p=float3(data[i],data[i+1],data[i+2]);
    float yaw=u.panCamera.z+u.viewport.z*0.25,pitch=u.panCamera.w;
    float3 eye=u.style.x*float3(sin(yaw)*cos(pitch),sin(pitch),cos(yaw)*cos(pitch));
    float3 z=normalize(eye),x=normalize(cross(float3(0,1,0),z)),y=cross(z,x),relative=p-eye;
    float depth=dot(relative,-z);
    PreviewVertex out; out.position=float4(dot(relative,x)*2.4/(u.viewport.x/u.viewport.y),dot(relative,y)*2.4,1.010101*depth-0.1010101,depth);
    out.world=p;out.normal=float3(data[i+3],data[i+4],data[i+5]);out.slot=data[i+6];out.eye=eye;return out;
}

float3 linearRGB(float3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c)); }
float3 displayRGB(float3 c) { c = max(c, float3(0.0)); return mix(c * 12.92, 1.055 * pow(c, float3(1.0/2.4)) - 0.055, step(float3(0.0031308), c)); }
float3 background(float2 uv) {
    float3 top = float3(0.89,0.91,0.94), bottom = float3(0.73,0.77,0.82);
    float3 c = mix(top, bottom, smoothstep(0.0,1.0,uv.y));
    float shadow = exp(-pow((uv.x-0.5)/0.23,2.0)-pow((uv.y-0.82)/0.045,2.0));
    return c * (1.0-0.24*shadow);
}
float3 materialColor(float slot, float finish) {
    if (slot > 1.5) return float3(1.0,0.82,0.40);
    if (slot > 0.5) return float3(0.10,0.12,0.16);
    if (finish < 0.5) return float3(0.87,0.85,0.78);
    if (finish < 1.5) return float3(0.13,0.27,0.68);
    return float3(0.65,0.38,0.16);
}
float3 productLight(float3 p, float3 normal, float slot, float3 eye, float finish) {
    if (slot > 1.5) return materialColor(slot,finish);
    float3 n=normalize(normal), v=normalize(eye-p), l=normalize(float3(-0.6,1.1,1.3)), h=normalize(v+l);
    float nl=max(dot(n,l),0.0), nv=max(dot(n,v),0.001), nh=max(dot(n,h),0.0), vh=max(dot(v,h),0.0);
    float3 base=linearRGB(materialColor(slot,finish));
    float metal=slot<0.5 && finish>1.5 ? 0.8 : 0.0;
    float rough=slot>0.5 ? 0.40 : (finish>1.5 ? 0.27 : 0.34);
    float a2=pow(rough,4.0), denom=nh*nh*(a2-1.0)+1.0;
    float d=a2/(3.14159*denom*denom+0.0001), k=(rough+1.0)*(rough+1.0)/8.0;
    float g=(nl/(nl*(1.0-k)+k))*(nv/(nv*(1.0-k)+k));
    float3 f0=mix(float3(0.04),base,metal), f=f0+(float3(1.0)-f0)*pow(1.0-vh,5.0);
    float3 spec=d*g*f/(4.0*nl*nv+0.001);
    float3 diffuse=(float3(1.0)-f)*(1.0-metal)*base/3.14159;
    float3 color=(diffuse+spec)*nl*3.0 + base*(0.24+0.14*max(n.y,0.0));
    color+=float3(0.16,0.20,0.30)*pow(1.0-nv,3.0)*0.25;
    return displayRGB(color/(float3(1.0)+color));
}
float2 imageUV(float2 uv, float4 viewport, float4 image, float4 panCamera) {
    float viewAspect=viewport.x/viewport.y, imageAspect=image.x/image.y;
    float2 fit=imageAspect>viewAspect ? float2(1.0,viewAspect/imageAspect) : float2(imageAspect/viewAspect,1.0);
    return (uv-0.5-panCamera.xy)/(fit*image.z)+0.5;
}
float3 adjusted(float3 color, float2 uv, float4 edit) {
    float3 c=linearRGB(color)*exp2(edit.x);
    float lum=dot(c,float3(0.2126,0.7152,0.0722));
    c=mix(float3(lum),c,edit.y);
    float radial=smoothstep(0.15,0.72,length(uv-0.5));
    c*=1.0-edit.z*radial*0.75;
    return displayRGB(clamp(c,float3(0.0),float3(1.0)));
}

fragment half4 previewFragment(PreviewVertex in [[stage_in]], constant PreviewUniforms &u [[buffer(1)]], texture2d<float> photograph [[texture(0)]]) {
    float2 uv=in.position.xy/u.viewport.xy; float3 c;
    if(u.viewport.w<0.5) {
        float2 source=imageUV(uv,u.viewport,u.image,u.panCamera);c=float3(0.12,0.14,0.18);
        if(source.x>=0 && source.x<=1 && source.y>=0 && source.y<=1) {
            constexpr sampler sample(mag_filter::linear,min_filter::linear,address::clamp_to_edge);
            float3 original=photograph.sample(sample,source).rgb;
            c=uv.x<u.edit.w ? original : adjusted(original,source,u.edit);
        }
        if(u.edit.w>0 && u.edit.w<1 && abs(uv.x-u.edit.w)<1.5/u.viewport.x) c=float3(0.98);
    } else if(u.viewport.w<1.5) c=background(uv);
    else c=productLight(in.world,in.normal,in.slot,in.eye,u.style.y);
    return half4(half3(c),1);
}
