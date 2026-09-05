extends Node3D
## Three authored combat arenas, one deterministic route and shared lighting.

var rng := RandomNumberGenerator.new()
var zones: Array[Node3D] = []
var water: MeshInstance3D
var palette := [Color("4a941f"), Color("247b36"), Color("79a832"), Color("246943")]
var concrete: StandardMaterial3D
var stone: StandardMaterial3D
var bark: StandardMaterial3D
var metal: StandardMaterial3D
var sand: StandardMaterial3D
var ground: StandardMaterial3D
var leaf_material: ShaderMaterial

func material(color: Color, roughness := 0.75, metallic := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.roughness = roughness
	m.metallic = metallic
	return m

func textured(prefix: String, color: Color, uv := 1.0) -> StandardMaterial3D:
	var m := material(color)
	m.albedo_texture = load("res://textures/" + prefix + ".jpg")
	m.normal_enabled = true
	m.normal_texture = load("res://textures/" + prefix + "_normal.jpg")
	m.normal_scale = .45
	m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	m.uv1_scale = Vector3(uv, uv, uv)
	return m

func _ready():
	rng.seed = 930517
	concrete = textured("concrete", Color("dedcd2"), 2.0)
	stone = textured("concrete", Color("96a5ab"), 1.2)
	bark = textured("tree_bark", Color("d7b18c"), 2.0)
	metal = material(Color("315469"), 0.28, 0.72)
	sand = textured("forest_ground", Color("eed7a7"), 20.0)
	ground = textured("forest_ground", Color("a8bc7b"), 18.0)
	leaf_material = ShaderMaterial.new()
	leaf_material.shader = load("res://shaders/leaves.gdshader")
	for i in range(3):
		var zone := Node3D.new()
		zone.name = ["VerdantSanctuary", "PrismCity", "AzureCoast"][i]
		add_child(zone)
		zones.append(zone)
		build_zone(zone, i)
	set_zone(0)

func set_zone(index: int):
	for i in range(zones.size()):
		zones[i].visible = i == index
		zones[i].process_mode = Node.PROCESS_MODE_INHERIT if i == index else Node.PROCESS_MODE_DISABLED

func mesh(parent: Node3D, geometry: Mesh, pos: Vector3, mat: Material) -> MeshInstance3D:
	var n := MeshInstance3D.new()
	n.mesh = geometry
	n.material_override = mat
	n.position = pos
	parent.add_child(n)
	return n

func box(parent: Node3D, pos: Vector3, size: Vector3, mat: Material) -> MeshInstance3D:
	var shape := BoxMesh.new()
	shape.size = size
	return mesh(parent, shape, pos, mat)

func sphere(parent: Node3D, pos: Vector3, size: Vector3, mat: Material) -> MeshInstance3D:
	var shape := SphereMesh.new()
	shape.radial_segments = 32
	shape.rings = 16
	var n := mesh(parent, shape, pos, mat)
	n.scale = size
	return n

func tube(parent: Node3D, start: Vector3, end: Vector3, radius: float, mat: Material, top := -1.0) -> MeshInstance3D:
	var shape := CylinderMesh.new()
	shape.top_radius = top if top >= 0 else radius
	shape.bottom_radius = radius
	shape.height = start.distance_to(end)
	shape.radial_segments = 20
	var n := mesh(parent, shape, (start + end) * 0.5, mat)
	var dir := (end - start).normalized()
	n.quaternion = Quaternion(Vector3.UP, dir)
	return n

func build_zone(zone: Node3D, index: int):
	var floor_mat: StandardMaterial3D = [ground, concrete, sand][index]
	box(zone, Vector3(0, -0.32, -20), Vector3(100, 0.6, 130), floor_mat)
	# The central avenue is kept free for combat, flanking scenery provides parallax.
	if index == 0:
		box(zone, Vector3(0, -0.005, -20), Vector3(9, 0.05, 100), textured("forest_ground", Color("cab393"), 16.0))
		for i in range(64):
			var side := -1.0 if i % 2 == 0 else 1.0
			var p := Vector3(side * rng.randf_range(7, 35), 0, rng.randf_range(-72, 15))
			tree(zone, p, rng.randf_range(0.85, 1.6), i)
		for i in range(36):
			rock(zone, Vector3(rng.randf_range(6,28)*(-1 if i%2==0 else 1), 0, rng.randf_range(-65,10)), rng.randf_range(0.7,2.2))
		for side in [-1, 1]:
			for i in range(5):
				pillar(zone, Vector3(side * 6.5, 0, -12 - i * 9), 4.0 + i * 0.4)
		arch(zone, Vector3(0, 0, -43), 11, 8)
		arch(zone, Vector3(0, 0, -61), 14, 10)
		plants(zone, 1150, 0)
	elif index == 1:
		box(zone, Vector3(0, 0.025, -22), Vector3(11, 0.06, 100), material(Color("647f94"), .42))
		for side in [-1, 1]:
			box(zone, Vector3(side * 6.6, .12, -22), Vector3(2, .22, 95), concrete)
			for i in range(8):
				building(zone, Vector3(side * (11 + i % 2 * 2), 0, 9-i*11), i, side)
				if i % 2 == 0:
					tree(zone, Vector3(side * 6.5, .2, 5-i*11), .63, i)
					tube(zone, Vector3(side*5.8,0, -i*12), Vector3(side*5.8,5.7,-i*12), .09, metal)
					box(zone, Vector3(side*5.8,5.7,-i*12), Vector3(.6,.2,.5), emissive(Color("ffcc75"), 1.5))
		for i in range(22):
			box(zone, Vector3(0,.065,10-i*4), Vector3(.15,.025,1.8), material(Color("f6db73")))
		for i in range(6):
			box(zone, Vector3(-4.5 + (i%2)*9, .75, -6-i*9), Vector3(1.5,1.5,1.8), metal)
		arch(zone, Vector3(0,0,-52), 13, 11)
		plants(zone, 200, 1)
	else:
		var water_mesh := PlaneMesh.new()
		water_mesh.size = Vector2(180, 180)
		var water_mat := ShaderMaterial.new()
		water_mat.shader = load("res://shaders/water.gdshader")
		water = mesh(zone, water_mesh, Vector3(48,-.08,-45), water_mat)
		box(zone, Vector3(-11,.0,-25),Vector3(28,.4,125),sand)
		for i in range(26):
			palm(zone,Vector3(rng.randf_range(-24,-6),0,rng.randf_range(-70,14)), rng.randf_range(.85,1.4))
		for i in range(20):
			rock(zone,Vector3(rng.randf_range(6,14),-.4,-i*5.2),rng.randf_range(1.1,2.6))
		for i in range(5):
			building(zone,Vector3(-18,0,-8-i*13),i,-1)
		for i in range(28):
			box(zone,Vector3(4,.22,-30-i*.55),Vector3(9,.16,.5),bark)
		for i in range(6):
			tube(zone,Vector3(8,-1,-30-i*2.5),Vector3(8,1,-30-i*2.5),.14,bark)
		lighthouse(zone,Vector3(16,0,-70))
		plants(zone, 440, 2)
	# Silhouetted distant mountain islands are continuous in all three arenas.
	for i in range(11):
		var m := sphere(zone,Vector3(-75+i*15,4,-98-rng.randf_range(0,15)),Vector3(22,17+rng.randf_range(0,14),17),material(Color("648e9f")))
		m.rotation.z = rng.randf_range(-.3,.3)

func emissive(color: Color, energy: float) -> StandardMaterial3D:
	var m := material(color, .3, .3)
	m.emission_enabled = true
	m.emission = color
	m.emission_energy_multiplier = energy
	return m

func rock(parent: Node3D, pos: Vector3, size: float):
	var n := sphere(parent,pos+Vector3(0,size*.4,0),Vector3(size*1.7,size,size*1.4),stone)
	n.rotation = Vector3(rng.randf_range(-.4,.4),rng.randf_range(0,TAU),rng.randf_range(-.4,.4))
	box(parent,pos+Vector3(0,size*.6,0),Vector3(size*.8,size*.3,size*.7),material(Color("65894e")))

func tree(parent: Node3D, pos: Vector3, size: float, index: int):
	var t := Node3D.new()
	parent.add_child(t)
	t.position = pos
	t.scale = Vector3.ONE * size
	tube(t,Vector3.ZERO,Vector3(.3,7,0),.42,bark,.16)
	for branch in range(5):
		var angle := branch * 2.4
		var end := Vector3(cos(angle)*2.4,5+branch*.5,sin(angle)*2.4)
		tube(t,Vector3(.2,3+branch*.6,0),end,.17,bark,.06)
	# Hundreds of curved leaf cards per crown, not opaque green ellipsoids.
	var leaves := MultiMesh.new()
	leaves.transform_format = MultiMesh.TRANSFORM_3D
	leaves.use_colors = true
	var card := QuadMesh.new()
	card.size = Vector2(.65,.34)
	leaves.mesh = card
	leaves.instance_count = 260
	for j in range(260):
		var az := rng.randf_range(0,TAU)
		var radial := sqrt(rng.randf())*3.2
		var p := Vector3(cos(az)*radial,6.2+rng.randf_range(-1.1,1.4)-radial*.1,sin(az)*radial)
		var basis := Basis.from_euler(Vector3(rng.randf_range(-1.3,1.3),az,rng.randf_range(-.6,.6)))
		leaves.set_instance_transform(j,Transform3D(basis,p))
		leaves.set_instance_color(j,palette[(j+index)%palette.size()])
	var crown := MultiMeshInstance3D.new()
	crown.multimesh = leaves
	crown.material_override = leaf_material
	t.add_child(crown)

func plants(parent: Node3D, count: int, index: int):
	var multi := MultiMesh.new()
	multi.transform_format = MultiMesh.TRANSFORM_3D
	multi.use_colors = true
	var blade := QuadMesh.new()
	blade.size = Vector2(.42,.85)
	multi.mesh = blade
	multi.instance_count = count
	for i in range(count):
		var x := rng.randf_range(5.5,24)*(-1 if i%2==0 else 1)
		if index == 2: x = -absf(x)
		var p := Vector3(x,.32,rng.randf_range(-74,14))
		var b := Basis.from_euler(Vector3(0,rng.randf_range(0,TAU),rng.randf_range(-.2,.2)))
		multi.set_instance_transform(i,Transform3D(b.scaled(Vector3.ONE*rng.randf_range(.5,1.3)),p))
		multi.set_instance_color(i,Color("daca61") if index==2 else palette[i%4])
	var grass := MultiMeshInstance3D.new()
	grass.multimesh = multi
	grass.material_override = leaf_material
	parent.add_child(grass)
	if index == 0:
		for i in range(70):
			var x := rng.randf_range(5.5,15)*(-1 if i%2==0 else 1)
			var p := Vector3(x,.32,rng.randf_range(-55,8))
			var flower := emissive([Color("ff9bdd"),Color("ffd85c"),Color("99dfff")][i%3],.2)
			for petal in range(5):
				var a := petal*TAU/5
				sphere(parent,p+Vector3(cos(a)*.13,0,sin(a)*.13),Vector3(.19,.08,.19),flower)

func pillar(parent: Node3D, p: Vector3, height: float):
	box(parent,p+Vector3(0,.25,0),Vector3(1.7,.5,1.7),stone)
	box(parent,p+Vector3(0,height*.5,0),Vector3(1,height,1),concrete)
	for i in range(int(height/.6)):
		box(parent,p+Vector3(0,i*.6,0),Vector3(1.04,.045,1.04),stone)
	box(parent,p+Vector3(0,height,0),Vector3(1.5,.35,1.5),stone)

func arch(parent: Node3D, p: Vector3, width: float, height: float):
	pillar(parent,p+Vector3(-width*.5,0,0),height)
	pillar(parent,p+Vector3(width*.5,0,0),height)
	for i in range(13):
		var a := i*PI/12
		var n := box(parent,p+Vector3(cos(a)*width*.5,height+sin(a)*width*.26,0),Vector3(1.4,1.0,1.7),concrete)
		n.rotation.z = a-PI*.5
	box(parent,p+Vector3(0,height+1.7,0),Vector3(1.0,1.4,1.9),emissive(Color("3bddd8"),.4))

func building(parent: Node3D,p:Vector3,index:int,side:int):
	var colors := [Color("fae2bd"),Color("a5d4d5"),Color("ffb89a"),Color("b7c1e1")]
	var wall := material(colors[index%4],.75)
	var h := 7.0 + (index%3)*3.0
	box(parent,p+Vector3(0,h*.5,0),Vector3(7,h,8),wall)
	box(parent,p+Vector3(0,h+.1,0),Vector3(7.6,.35,8.6),concrete)
	for floor_index in range(int(h/2.8)):
		box(parent,p+Vector3(0,floor_index*2.8+.3,0),Vector3(7.2,.2,8.2),concrete)
		for w in range(3):
			var wp := p+Vector3(-side*3.52,1.55+floor_index*2.8,-2.4+w*2.4)
			box(parent,wp,Vector3(.09,1.6,1.3),metal)
			box(parent,wp+Vector3(-side*.055,0,0),Vector3(.04,1.36,1.06),emissive(Color("8fe8ef") if floor_index%2==0 else Color("ffe4a0"),.18))
			box(parent,wp+Vector3(-side*.16,-.92,0),Vector3(.5,.12,1.6),concrete)
	var awning := box(parent,p+Vector3(-side*4.1,3.0,0),Vector3(1.4,.16,5.8),material([Color("e77559"),Color("327d9a"),Color("b381d2")][index%3]))
	awning.rotation.z = side*.14
	for z in [-2.6,2.6]:
		tube(parent,p+Vector3(-side*4.6,0,z),p+Vector3(-side*4.6,2.8,z),.07,metal)
	box(parent,p+Vector3(-side*3.6,4.2,0),Vector3(.18,.7,3.1),emissive(Color("ffac77"),.3))
	for i in range(3):
		box(parent,p+Vector3(-side*3.72,4.2,-.85+i*.85),Vector3(.06,.27,.5),metal)

func palm(parent:Node3D,p:Vector3,size:float):
	var top := p+Vector3(1.0,7*size,0)
	tube(parent,p,top,.30*size,bark,.14)
	for i in range(11):
		var angle := i*TAU/11
		for segment in range(5):
			var a := float(segment)/5
			var cp := top+Vector3(cos(angle)*a*4, sin(a*PI)*1.1-a*a*1.8,sin(angle)*a*4)*size
			var leaf := box(parent,cp,Vector3(.5*(1-a)+.08,.04,1.1)*size,material(palette[i%4]))
			leaf.rotation = Vector3(a*.65, -angle+PI*.5,.1)

func lighthouse(parent:Node3D,p:Vector3):
	for i in range(6):
		tube(parent,p+Vector3(0,i*2,0),p+Vector3(0,(i+1)*2,0),2-i*.16,material(Color("f5e9d5") if i%2==0 else Color("f08060")),1.84-i*.16)
	tube(parent,p+Vector3(0,12,0),p+Vector3(0,13.5,0),1.4,emissive(Color("ffdc9c"),.7))
	tube(parent,p+Vector3(0,13.5,0),p+Vector3(0,15,0),2,metal,0)
