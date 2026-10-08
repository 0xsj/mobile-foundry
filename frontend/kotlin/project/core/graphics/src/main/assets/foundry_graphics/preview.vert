attribute vec3 position;
attribute vec3 normal;
attribute float slot;
uniform mediump vec4 viewport;
uniform mediump vec4 panCamera;
uniform mediump vec4 style;
varying mediump vec3 world;
varying mediump vec3 surfaceNormal;
varying mediump float materialSlot;
varying mediump vec3 cameraEye;
void main() {
    world=position; surfaceNormal=normal; materialSlot=slot;
    float yaw=panCamera.z+viewport.z*0.25, pitch=panCamera.w;
    vec3 eye=style.x*vec3(sin(yaw)*cos(pitch),sin(pitch),cos(yaw)*cos(pitch));
    cameraEye=eye;
    if(viewport.w<1.5) { gl_Position=vec4(position.xy,0.0,1.0); return; }
    vec3 z=normalize(eye), x=normalize(cross(vec3(0.0,1.0,0.0),z)), y=cross(z,x), relative=position-eye;
    float depth=dot(relative,-z);
    gl_Position=vec4(dot(relative,x)*2.4/(viewport.x/viewport.y),dot(relative,y)*2.4,1.020202*depth-0.2020202,depth);
}
