shader_type spatial;
render_mode cull_disabled;
void vertex() {
	vec3 world = (MODEL_MATRIX * vec4(VERTEX,1.0)).xyz;
	VERTEX.x += sin(TIME*1.7+world.x*.7+world.z*.4)*.08*UV.y;
}
void fragment() {
	vec2 p = UV*2.0-1.0;
	if (dot(p,p)>1.0 || abs(p.x) > (1.0-abs(p.y))*.85+.15) discard;
	ALBEDO = COLOR.rgb * (0.82 + 0.18*UV.y);
	ROUGHNESS = .83;
	BACKLIGHT = COLOR.rgb*.08;
}
