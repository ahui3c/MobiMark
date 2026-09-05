extends SkeletonModifier3D
## Additive locomotion after the imported animation, in each bone parent's space.
## The scene root and root bone are never rotated by locomotion or a dodge.
var actor: Node3D

func _process_modification_with_delta(_delta:float):
	if not is_instance_valid(actor): return
	var sk := get_skeleton()
	var t: float = actor.clock
	var gait := sin(t*10.5)
	var speed: float = actor.move_blend
	rotate_global(sk,"LeftUpLeg",Vector3.RIGHT,gait*.55*speed)
	rotate_global(sk,"RightUpLeg",Vector3.RIGHT,-gait*.55*speed)
	rotate_global(sk,"LeftLeg",Vector3.RIGHT,maxf(0,-gait)*.75*speed)
	rotate_global(sk,"RightLeg",Vector3.RIGHT,maxf(0,gait)*.75*speed)
	rotate_global(sk,"LeftFoot",Vector3.RIGHT,-gait*.14*speed)
	rotate_global(sk,"RightFoot",Vector3.RIGHT,gait*.14*speed)
	rotate_global(sk,"Spine2",Vector3.FORWARD,actor.lean*.10)
	if actor.weapon_mode == 2:
		rotate_global(sk,"RightArm",Vector3.FORWARD,sin(t*5.5)*.65-.35)
		rotate_global(sk,"RightForeArm",Vector3.RIGHT,sin(t*5.5+.4)*.50)
		rotate_global(sk,"LeftArm",Vector3.RIGHT,-gait*.22)
	else:
		rotate_global(sk,"RightArm",Vector3.RIGHT,actor.recoil*.12)
		rotate_global(sk,"LeftArm",Vector3.RIGHT,actor.recoil*.07)
		var right_target:=Vector3(.44,1.49,-.43)
		var left_target:=Vector3(.29,1.49,-.86)
		if actor.weapon_mode==1:
			right_target=Vector3(.43,1.75,-.20)
			left_target=Vector3(.35,1.70,-.68)
		solve_arm(sk,"Right",sk.to_local(actor.to_global(right_target)))
		solve_arm(sk,"Left",sk.to_local(actor.to_global(left_target)))

func solve_arm(sk:Skeleton3D,side:String,target:Vector3):
	var hand:int=actor.bones.get(side+"Hand",-1)
	if hand<0: return
	for iteration in range(4):
		for key in [side+"ForeArm",side+"Arm"]:
			var index:int=actor.bones.get(key,-1)
			if index<0: continue
			var joint:=sk.get_bone_global_pose(index).origin
			var from:=sk.get_bone_global_pose(hand).origin-joint
			var to:=target-joint
			if from.length_squared()<.001 or to.length_squared()<.001: continue
			var delta:=Quaternion(from.normalized(),to.normalized())
			var parent:=sk.get_bone_parent(index)
			var pb:=sk.get_bone_global_pose(parent).basis.orthonormalized()
			var local_delta:=pb.get_rotation_quaternion().inverse()*delta*pb.get_rotation_quaternion()
			sk.set_bone_pose_rotation(index,(local_delta*sk.get_bone_pose_rotation(index)).normalized())

func rotate_global(sk: Skeleton3D, key: String, axis: Vector3, angle: float):
	var index: int = actor.bones.get(key,-1)
	if index < 0: return
	var parent := sk.get_bone_parent(index)
	var parent_basis := Basis.IDENTITY if parent < 0 else sk.get_bone_global_pose(parent).basis.orthonormalized()
	var local_axis := (parent_basis.inverse()*axis).normalized()
	sk.set_bone_pose_rotation(index,Quaternion(local_axis,angle)*sk.get_bone_pose_rotation(index))
