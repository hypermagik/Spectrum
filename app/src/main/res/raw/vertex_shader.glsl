attribute vec4 vPosition;

attribute vec2 aTexCoord;
varying vec2 vTexCoord;

uniform int sampleTexture;

uniform int fftSize;
uniform float fftXscale;
uniform float fftXtranslate;
uniform float fftMinY;
uniform float fftMaxY;
uniform float fftMinDB;
uniform float fftMaxDB;

uniform int waterfallOffset;
uniform int waterfallHeight;

const float M_1_OVER_LN10 = 0.43429544250362636694490528016399;

void drawFFT();
void drawWaterfall();

void main() {
    if (sampleTexture == 2) {
        drawWaterfall();
        return;
    }
    if (vPosition.z > 0.0) {
        drawFFT();
    } else {
        gl_Position = vPosition;
    }
    vTexCoord = vec2(aTexCoord.s, aTexCoord.t);
}

const float zFFT = 1.0;
const float zFill = 2.0;
const float zPeaks = 3.0;

void drawFFT() {
    float x = vPosition.x;
    float y = vPosition.y;
    float z = vPosition.z;
    float w = vPosition.w;

    if (vPosition.z == zFFT) {
        // Convert sample index to coord.
        x = x / (float(fftSize) - 1.0);
    }

    // Scale and translate to view.
    x = x * fftXscale - fftXtranslate;

    if (vPosition.z != zFill) {
        // Convert to dB.
        float value = 20.0 * log(y) * M_1_OVER_LN10;

        // Clamp and scale value.
        if (vPosition.z != zPeaks) {
            value = clamp(value, fftMinDB, fftMaxDB);
        }
        value = (value - fftMinDB) / (fftMaxDB - fftMinDB);

        // Scale and translate into view.
        y = fftMinY + value * (fftMaxY - fftMinY);
    }

    gl_Position = vec4(x, y, 0.0, w);
}

void drawWaterfall() {
    float x = vPosition.x;
    float y = vPosition.y;
    float z = vPosition.z;
    float w = vPosition.w;

    // Scale and translate to view.
    x = x * fftXscale - fftXtranslate;

    gl_Position = vec4(x, y, z, w);

    // Start sampling from the last set of samples and wrap around.
    float s = aTexCoord.s;
    float t = aTexCoord.t - float(waterfallOffset) / float(waterfallHeight);

    vTexCoord = vec2(s, t);
}
