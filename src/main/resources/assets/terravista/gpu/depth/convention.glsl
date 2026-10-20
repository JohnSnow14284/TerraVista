// 定义投影转换与深度比较约定。
#ifndef TERRAVISTA_DEPTH_CONVENTION
#define TERRAVISTA_DEPTH_CONVENTION

#ifdef USE_REVERSE_Z
#define REDUCTION min
#define REDUCTION2 max
#define NEAR 1.0f
#define FAR 0.0f
#define CLOSER_SIGN 1.0f
#define DEPTH_SCALAR_COMPARE(a,b) ((a)>(b))
#define DEPTH_SCALAR_COMPARE_EQUAL(a,b) ((a)>=(b))
#else
#define REDUCTION max
#define REDUCTION2 min
#define NEAR 0.0f
#define FAR 1.0f
#define CLOSER_SIGN -1.0f
#define DEPTH_SCALAR_COMPARE(a,b) ((a)<(b))
#define DEPTH_SCALAR_COMPARE_EQUAL(a,b) ((a)<=(b))
#endif

#ifdef USE_ZERO_ONE_DEPTH
vec3 NDC2SCREEN(vec3 val) {
    return vec3(val.xy*0.5f+0.5f, val.z);
}
vec3 SCREEN2NDC(vec3 val) {
    return vec3(val.xy*2.0f-1.0f, val.z);
}
float NDC2SCREEN_DEPTH(float val) {
    return val;
}
float SCREEN2NDC_DEPTH(float val) {
    return val;
}
#else
vec3 NDC2SCREEN(vec3 val) {
    return val*0.5f+0.5f;
}
vec3 SCREEN2NDC(vec3 val) {
    return val*2.0f-1.0f;
}
float NDC2SCREEN_DEPTH(float val) {
    return val*0.5f+0.5f;
}
float SCREEN2NDC_DEPTH(float val) {
    return val*2.0f-1.0f;
}
#endif

#endif
