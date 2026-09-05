extends Node3D

var model: Node3D
var animation: AnimationPlayer
var skeleton: Skeleton3D
var bones := {}
var weapon_rig: Node3D
var weapons: Array[Node3D] = []
var muzzle: Marker3D
var weapon_mode := 0
var clock := 0.0
var move_blend := 1.0
var lean := 0.0
var recoil := 0.0
var aim_target := Vector3(0,1.8,-18)
var hero := false
var variant := 0
var health := 100.0
var hit_cooldown := 0.0
var dead_timer := 0.0
var attack_timer := 0.0
var last_clip := ""
var body_height := 2.5
var rig_helper
var armor_mats: Array[StandardMaterial3D] = []
var pose_modifier: SkeletonModifier3D
var rendered_head:=Vector3.ZERO
var rendered_foot:=Vector3.ZERO
var rendered_leg:=Quaternion.IDENTITY

func configure(is_hero: bool, kind: int, helper):
	hero = is_hero
	variant = kind
	rig_helper = helper
	var files := ["BlueDemon.gltf","Orc.gltf","Demon.gltf","Dino.gltf","Cyclops.glb"]
	model = load("res://assets/"+("Hero.glb" if hero else files[kind%5])).instantiate()
	add_child(model)
	var mesh_scale: float = .74 if hero else [.96,1.03,.98,1.32,.14][kind%5]
	model.scale = Vector3.ONE*mesh_scale
	model.rotation.y = PI if hero else 0.0
	body_height = 2.35 if hero else [2.7,3.2,3.1,4.2,3.7][kind%5]
	var anims := model.find_children("*","AnimationPlayer",true,false)
	if not anims.is_empty(): animation = anims[0]
	var skeletons := model.find_children("*","Skeleton3D",true,false)
	if not skeletons.is_empty(): skeleton = skeletons[0]
	for node in model.find_children("*","MeshInstance3D",true,false):
		node.extra_cull_margin = 12.0
		for surface in range(node.mesh.get_surface_count()):
			var mat = node.get_active_material(surface)
			if mat is StandardMaterial3D:
				var copy: StandardMaterial3D = mat.duplicate()
				copy.shading_mode = BaseMaterial3D.SHADING_MODE_PER_PIXEL
				copy.roughness = .45 if hero else .7
				if not hero:
					copy.albedo_color *= [Color("b7eafa"),Color("d6ff96"),Color("f9b3df"),Color("b9cbff"),Color("ffd19a")][kind%5]
				node.set_surface_override_material(surface,copy)
				armor_mats.append(copy)
	if hero:
		for key in ["LeftUpLeg","RightUpLeg","LeftLeg","RightLeg","LeftFoot","RightFoot","Spine2","RightArm","LeftArm","RightForeArm","LeftForeArm","RightHand","LeftHand","Head"]:
			for i in range(skeleton.get_bone_count()):
				if skeleton.get_bone_name(i).begins_with("mixamorig_"+key+"_"):
					bones[key] = i
		var modifier = load("res://scripts/warrior_pose.gd").new()
		pose_modifier=modifier
		modifier.actor = self
		skeleton.add_child(modifier)
		build_weapons()
		modifier.modification_processed.connect(update_weapon_pose)
		play_clip("mixamo_com")
	else:
		play_clip("Run")

func build_weapons():
	weapon_rig = Node3D.new()
	add_child(weapon_rig)
	weapon_rig.top_level = true
	for kind in range(3):
		var holder := Node3D.new()
		weapon_rig.add_child(holder)
		weapons.append(holder)
		if kind < 2:
			var weapon = load("res://assets/"+("Rifle.glb" if kind==0 else "Rocket.glb")).instantiate()
			holder.add_child(weapon)
			weapon.scale = Vector3.ONE*(.43 if kind==0 else .34)
			weapon.rotation.y = PI*.5
			weapon.position = Vector3(0,.09,-.32)
			if kind==0:
				rig_helper.box(holder,Vector3(0,.26,-.12),Vector3(.13,.10,.28),rig_helper.metal)
				rig_helper.box(holder,Vector3(0,.27,-.28),Vector3(.08,.07,.02),rig_helper.emissive(Color("47e4ff"),2))
		else:
			rig_helper.tube(holder,Vector3(0,0,.15),Vector3(0,0,-.18),.055,rig_helper.metal)
			rig_helper.tube(holder,Vector3(0,0,-.18),Vector3(0,0,-1.65),.027,rig_helper.emissive(Color("8cffff"),6))
			rig_helper.tube(holder,Vector3(0,0,-.16),Vector3(0,0,-.22),.085,rig_helper.emissive(Color("30c9ff"),3))
			var glow := OmniLight3D.new()
			glow.light_color=Color("40dfff")
			glow.light_energy=1.4
			glow.omni_range=3.0
			glow.position=Vector3(0,0,-.8)
			holder.add_child(glow)
	muzzle=Marker3D.new()
	weapon_rig.add_child(muzzle)
	set_weapon(0)

func set_weapon(mode: int):
	weapon_mode=mode
	for i in range(weapons.size()): weapons[i].visible = i==mode
	if muzzle: muzzle.position=Vector3(0,.09,-1.10 if mode==0 else -1.4)

func play_clip(key: String):
	if animation == null: return
	var found := ""
	for clip in animation.get_animation_list():
		if str(clip).to_lower().ends_with(key.to_lower()): found=clip
	if found.is_empty():
		for clip in animation.get_animation_list():
			if "walk" in str(clip).to_lower(): found=clip
	if found.is_empty() or found==last_clip: return
	var a := animation.get_animation(found)
	a.loop_mode=Animation.LOOP_LINEAR if key in ["Run","Walk","Idle","mixamo_com"] else Animation.LOOP_NONE
	animation.play(found,.18)
	last_clip=found

func tick(delta: float, time: float):
	clock=time
	recoil=move_toward(recoil,0,delta*7)
	hit_cooldown=maxf(0,hit_cooldown-delta)
	attack_timer=maxf(0,attack_timer-delta)
	if not hero:
		if dead_timer>0:
			dead_timer-=delta
			model.position.y=-maxf(0,1.0-dead_timer)*1.3
		for mat in armor_mats:
			mat.emission_enabled=hit_cooldown>.08
			mat.emission=Color("ffd6a0")
			mat.emission_energy_multiplier=.5

func update_weapon_pose():
	if hero and is_instance_valid(weapon_rig):
		rendered_head=skeleton.global_transform*skeleton.get_bone_global_pose(bones.Head).origin
		rendered_foot=skeleton.global_transform*skeleton.get_bone_global_pose(bones.LeftFoot).origin
		rendered_leg=skeleton.get_bone_pose_rotation(bones.LeftUpLeg)
		# Only translation is inherited from the animated right palm. Weapon aim is
		# solved in world coordinates; no source bone scale leaks into the gun.
		var hand_index: int = bones.get("RightHand",-1)
		if hand_index>=0:
			weapon_rig.global_position=skeleton.global_transform*skeleton.get_bone_global_pose(hand_index).origin
			var aim: Vector3=aim_target
			if weapon_mode==2:
				aim=weapon_rig.global_position+global_basis*Vector3(sin(clock*5.5)*1.4,.5+cos(clock*5.5)*.6,-1.1)
			weapon_rig.look_at(aim,Vector3.UP)
			weapon_rig.rotate_object_local(Vector3.RIGHT,recoil*.04)

func hit(amount: float):
	if dead_timer>0: return
	health-=amount
	hit_cooldown=.22
	if health<=0:
		dead_timer=1.4
		play_clip("Death")
	else:
		play_clip("HitReact")

func respawn(p:Vector3):
	position=p
	health=160 if variant%5==3 or variant%5==4 else 85
	dead_timer=0
	hit_cooldown=0
	attack_timer=0
	last_clip=""
	model.position.y=0
	play_clip("Run")
