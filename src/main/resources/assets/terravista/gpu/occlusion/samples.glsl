#ifndef SSAO_STEPS
#define SSAO_STEPS 12
#endif

const float tau = 6.2831853;

const float phi1 = 1.6180339887; // 黄金比，满足 x^2 = x + 1。
const float phi2 = 1.3247179572; // 塑性常数，满足 x^3 = x + 1。
const float phi3 = 1.2207440846; // 满足 x^4 = x + 1 的常数。

float r1(int n, float seed) {
    const float alpha = 1.0 / phi1;
    return fract(seed + n * alpha);
}

float r1(int n) { return r1(n, 0.5); }

vec2 get_ssao_sample_offset(int step_index, vec2 dither) {
    float a = (float(step_index) + dither.x) * (1.0f/(float(SSAO_STEPS)));

    float r = sqrt(r1(step_index, dither.y));
    float theta = a * tau;

    return r * vec2(cos(theta), sin(theta));
}
#define SSAO_MAX_RADIUS_SCREEN 0.1
#define SSAO_RADIUS 1.5f

vec2 r2(int n, vec2 seed) {
    const vec2 alpha = 1.0 / vec2(phi2, phi2 * phi2);
    return fract(seed + n * alpha);
}

vec2 r2(int n) { return r2(n, vec2(0.5)); }

