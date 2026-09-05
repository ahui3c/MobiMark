extends SceneTree
## Accelerated structural regression, not a visual or performance benchmark.
var failures:=0
func _initialize():
	run.call_deferred()
func run():
	var scene=load("res://scenes/combat.tscn").instantiate()
	root.add_child(scene)
	await process_frame
	Engine.max_fps=0
	var hero=scene.hero
	var min_height:=INF
	var max_height:=-INF
	var min_x:=INF
	var max_x:=-INF
	var pose_samples: Array[Vector3]=[]
	for frame in range(2160):
		# Sweep 3 minutes then reset to exercise the 80% boundary and next round.
		scene.time_offset=fmod(float(frame)/10.0,180.0)-scene.local_clock
		await process_frame
		var sk:Skeleton3D=hero.skeleton
		var head:Vector3=hero.rendered_head
		var foot:Vector3=hero.rendered_foot
		var hand:Vector3=sk.global_transform*sk.get_bone_global_pose(hero.bones.RightHand).origin
		var height:=head.y-foot.y
		min_height=minf(min_height,height)
		max_height=maxf(max_height,height)
		min_x=minf(min_x,hero.position.x)
		max_x=maxf(max_x,hero.position.x)
		if not head.is_finite() or not hand.is_finite() or height<.65 or height>3.5:
			failures+=1
			if failures<8: printerr("POSE_INVALID frame=",frame," head_to_foot=",height)
		if hero.global_basis.y.dot(Vector3.UP)<.99: failures+=1
		if frame%10==0: pose_samples.append(hero.rendered_leg.get_euler())
	var motion:=0.0
	for i in range(1,pose_samples.size()): motion+=pose_samples[i].distance_to(pose_samples[i-1])
	if motion<1: failures+=1
	print("POSE_VALIDATION frames=2160 errors=",failures," head_to_foot=",min_height,"..",max_height," leg_motion=",motion," lateral_span=",max_x-min_x)
	quit(0 if failures==0 else 1)
