shader_type spatial;
render_mode unshaded,cull_disabled,depth_draw_never;
uniform float storm=0.0;
uniform sampler2D cloud_noise : repeat_enable, filter_linear_mipmap;
void fragment(){
	vec2 p=UV*2.0+vec2(TIME*.001,0);
	float n=texture(cloud_noise,p).r;
	ALBEDO=mix(vec3(.96,.98,1.0),vec3(.32,.40,.51),storm);
	ALPHA=smoothstep(.46-storm*.16,.72,n)*.9;
}
