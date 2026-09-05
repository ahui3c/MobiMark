shader_type spatial;
render_mode cull_disabled;
void vertex() {
	VERTEX.y += sin(VERTEX.x*.35+TIME*.8)*.12+cos(VERTEX.z*.3+TIME)*.10;
}
void fragment() {
	vec2 uv=UV*90.0;
	float wave=sin(uv.x*2.0+TIME)*cos(uv.y*2.7+TIME*.7);
	float foam=smoothstep(.87,.98,wave);
	ALBEDO=mix(vec3(.02,.46,.58),vec3(.24,.78,.79),sin(uv.x*.2+uv.y*.3)*.5+.5)+foam*.20;
	METALLIC=.25;
	ROUGHNESS=.16;
	NORMAL_MAP=normalize(vec3(sin(uv.x+TIME)*.15,cos(uv.y+TIME)*.15,1.0))*.5+.5;
}
