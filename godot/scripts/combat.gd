extends Node3D
## Fixed-seed, automatically played third-person workload. Native host time owns
## scene boundaries; performance never changes the amount of scheduled action.

const Fighter = preload("res://scripts/fighter.gd")
const World = preload("res://scripts/world.gd")
var world
var hero
var enemies: Array = []
var camera: Camera3D
var env: Environment
var sun: DirectionalLight3D
var rain: GPUParticles3D
var clouds: MeshInstance3D
var hud: Control
var info: Label
var weapon_label: Label
var status: Label
var crosshair: Control
var elapsed := 0.0
var local_clock := 0.0
var scene_index := -1
var last_shot := -1.0
var last_log := -1
var rng := RandomNumberGenerator.new()
var effects: Array = []
var plugin: Object
var capture_dir := ""
var capture_times: Array[float] = [8,20,68,80,128,142,178]
var capture_index := 0
var auto_quit := 0.0
var time_offset := 0.0
var muzzle_light: OmniLight3D
var shot_sound: AudioStreamPlayer3D
var sfx: Array[AudioStreamWAV] = []

func _ready():
	# Preserve the app's high-refresh workload policy; VSync and GPU speed
	# determine achieved FPS, not a silent 60 FPS software cap.
	Engine.max_fps=0
	rng.seed=930517
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--capture-dir="): capture_dir=arg.trim_prefix("--capture-dir=")
		if arg.begins_with("--auto-quit="): auto_quit=float(arg.trim_prefix("--auto-quit="))
		if arg.begins_with("--time-offset="): time_offset=float(arg.trim_prefix("--time-offset="))
	if Engine.has_singleton("AhuimarkBenchmark"): plugin=Engine.get_singleton("AhuimarkBenchmark")
	world=World.new()
	add_child(world)
	setup_lighting()
	hero=Fighter.new()
	add_child(hero)
	hero.configure(true,0,world)
	hero.position=Vector3(-1.6,0,2)
	for i in range(16):
		var enemy=Fighter.new()
		add_child(enemy)
		enemy.configure(false,i%5,world)
		enemy.respawn(spawn_point(i))
		enemies.append(enemy)
	camera=Camera3D.new()
	add_child(camera)
	camera.current=true
	camera.fov=64
	camera.near=.08
	camera.far=180
	camera.position=Vector3(.0,3.1,7.5)
	camera.look_at(Vector3(0,1.6,-15))
	muzzle_light=OmniLight3D.new()
	muzzle_light.light_color=Color("ffcb84")
	muzzle_light.omni_range=6
	add_child(muzzle_light)
	setup_audio()
	setup_hud()
	change_scene(0)
	if plugin: plugin.scene_ready()
	print("PRISM_READY engine=Godot renderer=",RenderingServer.get_current_rendering_method()," enemies=",enemies.size())

func setup_lighting():
	var we:=WorldEnvironment.new()
	add_child(we)
	env=Environment.new()
	we.environment=env
	env.background_mode=Environment.BG_SKY
	var sky:=Sky.new()
	var atmosphere:=ProceduralSkyMaterial.new()
	atmosphere.sky_top_color=Color("2582d2")
	atmosphere.sky_horizon_color=Color("ceeaf5")
	atmosphere.ground_bottom_color=Color("526c68")
	atmosphere.ground_horizon_color=Color("c6d9d8")
	atmosphere.sun_angle_max=4
	sky.sky_material=atmosphere
	env.sky=sky
	env.ambient_light_source=Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_energy=.42
	env.tonemap_mode=Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure=.92
	env.glow_enabled=true
	env.glow_intensity=.35
	env.glow_bloom=0
	env.fog_enabled=true
	env.fog_light_color=Color("abd5e5")
	env.fog_density=.0015
	env.fog_sky_affect=.15
	sun=DirectionalLight3D.new()
	sun.rotation_degrees=Vector3(-48,-32,0)
	sun.light_color=Color("fff0ce")
	sun.light_energy=1.65
	sun.shadow_enabled=true
	sun.directional_shadow_mode=DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	sun.directional_shadow_max_distance=75
	sun.shadow_bias=.03
	sun.shadow_normal_bias=1.0
	add_child(sun)
	var fill:=DirectionalLight3D.new()
	fill.rotation_degrees=Vector3(-22,145,0)
	fill.light_color=Color("70c9ff")
	fill.light_energy=.34
	add_child(fill)
	var plane:=PlaneMesh.new()
	plane.size=Vector2(240,240)
	var cm:=ShaderMaterial.new()
	cm.shader=load("res://shaders/clouds.gdshader")
	var noise := FastNoiseLite.new()
	noise.seed=930517
	noise.frequency=.012
	var cloud_texture := NoiseTexture2D.new()
	cloud_texture.width=512
	cloud_texture.height=512
	cloud_texture.seamless=true
	cloud_texture.noise=noise
	cm.set_shader_parameter("cloud_noise",cloud_texture)
	clouds=world.mesh(self,plane,Vector3(0,42,-40),cm)
	clouds.cast_shadow=GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	rain=GPUParticles3D.new()
	rain.amount=2200
	rain.lifetime=1.1
	rain.visibility_aabb=AABB(Vector3(-30,-10,-50),Vector3(60,40,80))
	var process:=ParticleProcessMaterial.new()
	process.emission_shape=ParticleProcessMaterial.EMISSION_SHAPE_BOX
	process.emission_box_extents=Vector3(18,0,25)
	process.direction=Vector3(.12,-1,.06)
	process.spread=3
	process.initial_velocity_min=23
	process.initial_velocity_max=29
	process.gravity=Vector3(0,-5,0)
	process.scale_min=.7
	process.scale_max=1.2
	rain.process_material=process
	var drop:=BoxMesh.new()
	drop.size=Vector3(.012,.45,.012)
	drop.material=world.emissive(Color("aecfde"),.15)
	rain.draw_pass_1=drop
	rain.position=Vector3(0,15,-12)
	rain.cast_shadow=GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(rain)

func setup_audio():
	# Original deterministic synthesis: no unlicensed commercial game audio.
	for mode in range(3):
		var wav:=AudioStreamWAV.new()
		wav.format=AudioStreamWAV.FORMAT_16_BITS
		wav.mix_rate=22050
		var data:=PackedByteArray()
		var frames:=6600 if mode==1 else 3300
		data.resize(frames*2)
		for i in range(frames):
			var t:=float(i)/22050
			var decay:=exp(-t*(15 if mode==1 else 35))
			var sample:float=(sin(t*(210 if mode==1 else 720)*TAU)*.35+rng.randf_range(-.5,.5))*decay
			if mode==2: sample=sin(t*(900-t*1700)*TAU)*decay*.55
			data.encode_s16(i*2,int(sample*14000))
		wav.data=data
		sfx.append(wav)
	shot_sound=AudioStreamPlayer3D.new()
	shot_sound.volume_db=-17
	shot_sound.max_polyphony=6
	add_child(shot_sound)

func setup_hud():
	var canvas:=CanvasLayer.new()
	add_child(canvas)
	hud=Control.new()
	hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	hud.mouse_filter=Control.MOUSE_FILTER_IGNORE
	canvas.add_child(hud)
	info=label("PRISM FRONT",Vector2(52,42),27,Color("ffffff"))
	status=label("",Vector2(52,80),17,Color("abdfec"))
	var credits:=Button.new()
	credits.text="CREDITS"
	credits.position=Vector2(1770,44)
	credits.flat=true
	hud.add_child(credits)
	credits.pressed.connect(func():
		var dialog:=AcceptDialog.new()
		dialog.title="Asset credits and licenses"
		var body:=TextEdit.new()
		body.editable=false
		body.custom_minimum_size=Vector2(1000,650)
		body.text=FileAccess.get_file_as_string("res://CREDITS.txt")+"\n"+FileAccess.get_file_as_string("res://GODOT_LICENSE.txt")
		dialog.add_child(body)
		hud.add_child(dialog)
		dialog.confirmed.connect(dialog.queue_free)
		dialog.canceled.connect(dialog.queue_free)
		dialog.popup_centered()
	)
	weapon_label=label("",Vector2(1510,928),28,Color.WHITE)
	var bar:=ColorRect.new()
	bar.position=Vector2(52,984)
	bar.size=Vector2(240,7)
	bar.color=Color("4cedc8")
	hud.add_child(bar)
	label("VANGUARD   /   250",Vector2(52,940),24,Color.WHITE)
	label("AUTONOMOUS COMBAT  ·  1080p / VSYNC",Vector2(52,1006),14,Color("c2dce7"))
	crosshair=Control.new()
	crosshair.position=Vector2(1040,490)
	hud.add_child(crosshair)
	for i in range(4):
		var line:=ColorRect.new()
		var vertical:=i>1
		line.size=Vector2(2,10) if vertical else Vector2(10,2)
		line.position=[Vector2(-20,-1),Vector2(10,-1),Vector2(-1,-20),Vector2(-1,10)][i]
		line.color=Color(1,1,1,.85)
		crosshair.add_child(line)

func label(text: String,pos:Vector2,size:int,color:Color)->Label:
	var l:=Label.new()
	l.text=text
	l.position=pos
	l.add_theme_font_size_override("font_size",size)
	l.add_theme_color_override("font_color",color)
	l.add_theme_color_override("font_shadow_color",Color(0,0,0,.5))
	l.add_theme_constant_override("shadow_offset_x",1)
	l.add_theme_constant_override("shadow_offset_y",2)
	hud.add_child(l)
	return l

func spawn_point(index:int)->Vector3:
	var lane:float=[-4.5,-1.8,2.0,4.8][index%4]
	return Vector3(lane+sin(index*2.4)*1.4,0,-9-(index/4)*9-rng.randf_range(0,3))

func change_scene(index:int):
	scene_index=index
	rng.seed=930517+index
	world.set_zone(index)
	for i in range(enemies.size()): enemies[i].respawn(spawn_point(i))
	info.text=["VERDANT SANCTUARY","PRISM CITY","AZURE COAST"][index]
	print("PRISM_SCENE ",index," clock=",elapsed)

func _process(delta:float):
	local_clock+=delta
	var new_time:=local_clock+time_offset
	if plugin: new_time=float(plugin.elapsed_millis())/1000.0
	if new_time < elapsed-.5:
		last_shot=-1
		last_log=-1
		scene_index=-1
		hero.position=Vector3(-1.6,0,2)
		hero.rotation=Vector3.ZERO
		hero.recoil=0
		if hero.animation: hero.animation.seek(0,true)
		for effect in effects: effect.node.queue_free()
		effects.clear()
	elapsed=new_time
	var index:=int(elapsed/60.0)%3
	if index!=scene_index: change_scene(index)
	var time:=fmod(elapsed,180.0)
	var mode:=int(time/10.0)%3
	if mode!=hero.weapon_mode: hero.set_weapon(mode)
	var dodge_phase:=fmod(time,8.0)
	var dodge:=sin((dodge_phase-5.7)*PI/1.0) if dodge_phase>5.7 and dodge_phase<6.7 else 0.0
	var side:=1.0 if int(time/8)%2==0 else -1.0
	var next_pos:=Vector3(-1.6+sin(time*.36)*2.5+side*dodge*1.25,0,1.0+sin(time*.22)*2.4)
	if mode==2: next_pos.z-=2.4*(.5+.5*sin(time*.85))
	hero.position=hero.position.lerp(next_pos,1-exp(-delta*7))
	hero.lean=side*dodge
	hero.move_blend=.8+dodge*.35
	hero.model.rotation.z=-side*dodge*.16
	var target=select_target()
	if target:
		hero.aim_target=target.position+Vector3(0,target.body_height*.58,0)
		var aim_dir:Vector3=hero.aim_target-hero.position
		hero.rotation.y=lerp_angle(hero.rotation.y,atan2(-aim_dir.x,-aim_dir.z),1-exp(-delta*4))
	hero.tick(delta,time)
	for i in range(enemies.size()):
		var enemy=enemies[i]
		var was_dead:bool=enemy.dead_timer>0
		enemy.tick(delta,time+i*.47)
		if was_dead:
			if enemy.dead_timer<=0: enemy.respawn(Vector3(rng.randf_range(-6,6),0,-30-rng.randf_range(0,18)))
			continue
		var dist:float=enemy.position.distance_to(hero.position)
		var orbit:=Vector3(sin(time*.53+i*2.4)*3.2,0,0)
		var destination:Vector3=hero.position+Vector3((i%3-1)*2.9,0,-4.4-float(i%4)*2)+orbit
		var dir:Vector3=destination-enemy.position
		if dist>4.7:
			enemy.position+=dir.normalized()*delta*(1.0+(i%4)*.27)
			if enemy.hit_cooldown<=0 and enemy.attack_timer<=0: enemy.play_clip("Run")
		# Soft separation stops crowd overlap while leaving broad flanking lanes.
		for other in enemies:
			if other==enemy: continue
			var separation:Vector3=enemy.position-other.position
			separation.y=0
			if separation.length_squared()<3.5 and separation.length_squared()>.01:
				enemy.position+=separation.normalized()*delta*1.9
		enemy.position.x=clampf(enemy.position.x,-6.2,6.2)
		enemy.position.z=minf(enemy.position.z,hero.position.z-3.5)
		var facing:Vector3=hero.position-enemy.position
		enemy.rotation.y=lerp_angle(enemy.rotation.y,atan2(facing.x,facing.z),1-exp(-delta*4))
		if enemy.attack_timer<=0 and dist<20 and fmod(time+i*.83,3.9)<delta:
			enemy.play_clip("Weapon" if i%2==0 else "Punch")
			enemy.attack_timer=.9
			spawn_projectile(enemy.position+Vector3(0,enemy.body_height*.6,0),hero.position+Vector3(side*dodge*2,1.4,0),Color("e969ff"),false,.5)
	if target and time-last_shot>([.19,1.1,.62][mode]) and dodge<.65:
		last_shot=time
		hero.recoil=1
		var start:Vector3=hero.muzzle.global_position
		var end:Vector3=hero.aim_target
		if mode==2:
			burst(hero.weapon_rig.to_global(Vector3(0,0,-1.3)),Color("40edff"),8)
			if hero.position.distance_to(target.position)<8: target.hit(48)
		else:
			spawn_projectile(start,end,Color("ffca63") if mode==0 else Color("5fe9ff"),true,.08 if mode==0 else .55,target,mode)
		muzzle_light.position=start
		muzzle_light.light_energy=2.0
		shot_sound.position=start
		shot_sound.stream=sfx[mode]
		shot_sound.play()
	muzzle_light.light_energy=move_toward(muzzle_light.light_energy,0,delta*18)
	update_effects(delta)
	update_weather(time)
	# Shoulder camera: hero reads large on the left, encounters stay ahead.
	var camera_pos:Vector3=hero.position+Vector3(1.45,2.6,4.55)
	camera.position=camera.position.lerp(camera_pos,1-exp(-delta*5))
	camera.look_at(hero.position+Vector3(.55,1.65,-10))
	if target:
		var screen:=camera.unproject_position(hero.aim_target)
		crosshair.position=crosshair.position.lerp(screen,1-exp(-delta*9))
	weapon_label.text=["AR-7  /  PULSE RIFLE","R-90  /  ROCKET","ARC  /  PLASMA BLADE"][mode]
	status.text="%s   ·   %02d:%02d   ·   %d FPS" % [["SUNLIT / WIND","RAIN / REFLECTIONS","COASTAL STORM"][weather_index(time)],int(time)/60,int(time)%60,Engine.get_frames_per_second()]
	var log_step:=int(elapsed/15)
	if log_step!=last_log:
		last_log=log_step
		print("PRISM_TICK time=",elapsed," scene=",scene_index," weapon=",mode," hero=",hero.position," fps=",Engine.get_frames_per_second())
	if not capture_dir.is_empty() and capture_index<capture_times.size() and local_clock>=capture_times[capture_index]:
		capture_index+=1
		capture_frame.call_deferred()
	if auto_quit>0 and local_clock>auto_quit: get_tree().quit()

func select_target():
	var best=null
	var score:=INF
	for enemy in enemies:
		if enemy.dead_timer>0: continue
		var d:float=hero.position.distance_squared_to(enemy.position)
		if d<score: score=d; best=enemy
	return best

func weather_index(time:float)->int:
	return int(fmod(time,60)/20)

func update_weather(time:float):
	var weather:=weather_index(time)
	rain.emitting=weather>0
	rain.amount_ratio=.50 if weather==1 else 1.0
	rain.position.x=hero.position.x
	var flash:=0.0
	if weather==2:
		var phase:=fmod(time,7.2)
		if phase<.10 or (phase>.20 and phase<.28): flash=2.5
	sun.light_energy=([1.65,1.05,.9][weather])+flash
	env.fog_density=[.0015,.004,.006][weather]
	clouds.material_override.set_shader_parameter("storm",float(weather)*.35)

func spawn_projectile(start:Vector3,end:Vector3,color:Color,friendly:bool,duration:float,victim=null,mode:=0):
	var n: MeshInstance3D=world.sphere(self,start,Vector3(.06,.06,.6) if mode==0 else Vector3(.16,.16,.45),world.emissive(color,4))
	if start.distance_to(end)>.01: n.look_at(end)
	effects.append({"node":n,"from":start,"to":end,"age":0.0,"life":duration,"victim":victim,"mode":mode,"friendly":friendly,"color":color,"projectile":true})

func burst(pos:Vector3,color:Color,count:int):
	for i in range(count):
		var dir:=Vector3(rng.randf_range(-1,1),rng.randf_range(.1,1.2),rng.randf_range(-1,1)).normalized()
		var n: MeshInstance3D=world.sphere(self,pos,Vector3.ONE*.075,world.emissive(color,3))
		effects.append({"node":n,"from":pos,"to":pos+dir*rng.randf_range(.7,2.0),"age":0.0,"life":.30+rng.randf()*.2,"projectile":false})

func update_effects(delta:float):
	var expired:Array=[]
	for e in effects:
		e.age+=delta
		var progress:float=clampf(e.age/e.life,0,1)
		e.node.position=e.from.lerp(e.to,progress)
		if not e.projectile: e.node.scale=Vector3.ONE*.075*(1-progress)
		if progress>=1: expired.append(e)
	for e in expired:
		effects.erase(e)
		e.node.queue_free()
		if e.projectile:
			burst(e.to,e.color,18 if e.mode==1 else 5)
			if is_instance_valid(e.victim): e.victim.hit(72 if e.mode==1 else 16)
	while effects.size()>220:
		var e=effects.pop_front()
		e.node.queue_free()

func capture_frame():
	await RenderingServer.frame_post_draw
	DirAccess.make_dir_recursive_absolute(capture_dir)
	var image:=get_viewport().get_texture().get_image()
	image.save_png(capture_dir.path_join("prism_%03d.png" % int(elapsed)))
	print("PRISM_CAPTURE ",elapsed)
