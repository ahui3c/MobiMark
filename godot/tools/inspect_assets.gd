extends SceneTree

func _initialize():
	call_deferred("inspect_all")

func inspect_all():
	for file in ["Hero.glb", "Rifle.glb", "Rocket.glb", "Cyclops.glb", "Demon.gltf", "Dino.gltf", "BlueDemon.gltf", "Orc.gltf"]:
		var packed = load("res://assets/" + file)
		if not packed:
			continue
		var model = packed.instantiate()
		root.add_child(model)
		print("ASSET ", file)
		walk(model)
		model.queue_free()
	quit()

func walk(node):
	if node is AnimationPlayer:
		for anim in node.get_animation_list():
			print("ANIMATION ", anim, " length=", node.get_animation(anim).length)
	if node is Skeleton3D:
		print("SKELETON ", node.get_path(), " global=", node.global_transform)
		for i in range(node.get_bone_count()):
			print("BONE ", i, " ", node.get_bone_name(i), " rest=", node.get_bone_rest(i).origin)
	if node is MeshInstance3D:
		print("MESH ", node.name, " aabb=", node.get_aabb(), " global=", node.global_transform)
	for child in node.get_children():
		walk(child)
