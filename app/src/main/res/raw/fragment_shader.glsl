precision highp float;

uniform vec4 vColor;

varying vec2 vTexCoord;
uniform sampler2D uTexture;

uniform highp int sampleTexture;

uniform float fftMinDB;
uniform float fftMaxDB;

uniform sampler2D waterfallSampler;

const float M_1_OVER_LN10 = 0.43429544250362636694490528016399;
const float M_2_TO_MINUS_23 = 0.00000011920929;

void drawWaterfall();

void main() {
    if (sampleTexture == 2) {
        drawWaterfall();
    } else if (sampleTexture == 1) {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    } else {
        gl_FragColor = vColor;
    }
}

void drawWaterfall() {
    float value = texture2D(uTexture, vTexCoord).a;

    // Convert to dB.
    value = 20.0 * log(value) * M_1_OVER_LN10;

    // Clamp and scale.
    value = clamp(value, fftMinDB, fftMaxDB);
    value = (value - fftMinDB) / (fftMaxDB - fftMinDB);

    gl_FragColor = texture2D(waterfallSampler, vec2(value, 0.0));
}

float rgba2float(vec4 rgba) {
    vec4 b32 = rgba * 255.0;

    float sign = mix(-1.0, 1.0, step(b32.a, 128.0));
    float exponent = floor(mod(b32.a + 0.1, 128.0)) * 2.0 + floor((b32.b + 0.1) / 128.0) - 127.0;
    float significand = b32.r + b32.g * 256.0 + floor(mod(b32.b + 0.1, 128.0)) * 256.0 * 256.0;

    return sign * (1.0 + significand * M_2_TO_MINUS_23) * pow(2.0, exponent);
}
