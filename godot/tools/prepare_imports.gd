extends SceneTree
func _initialize():
	for folder in ["res://textures","res://assets"]:
		for file in DirAccess.get_files_at(folder):
			if not file.ends_with(".import"): continue
			var path=folder.path_join(file)
			var cfg:=ConfigFile.new()
			if cfg.load(path)!=OK: continue
			if cfg.get_value("remap","importer","")=="texture":
				cfg.set_value("params","compress/mode",2)
				cfg.set_value("params","mipmaps/generate",true)
				cfg.set_value("params","process/size_limit",2048)
				cfg.save(path)
	quit()
