#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float alpha = texture(Sampler0, texCoord0).a * vertexColor.a * ColorModulator.a;
    if (alpha < 0.001) discard;
    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, alpha);
}
