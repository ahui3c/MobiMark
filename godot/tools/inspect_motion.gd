extends SceneTree
func _initialize():
	call_deferred("inspect")
func inspect():
	var model = load("res://assets/Hero.glb").instantiate()
	root.add_child(model)
	var player = model.find_children("*", "AnimationPlayer", true, false)[0]
	var a = player.get_animation("mixamo_com")
	for i in range(a.get_track_count()):
		var path = str(a.track_get_path(i))
		if "rootJoint" in path or "Hips" in path or "RightArm" in path or "LeftUpLeg" in path:
			print(path, " type=", a.track_get_type(i))
			for t in [0,.4,.8,1.2,1.6]:
				var k = a.track_find_key(i,t)
				print("   ",t," ",a.track_get_key_value(i,maxi(k,0)))
	quit()
