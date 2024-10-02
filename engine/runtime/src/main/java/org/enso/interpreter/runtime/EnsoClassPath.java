package org.enso.interpreter.runtime;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Representation of an Enso library class path. */
public final class EnsoClassPath {
  static final EnsoClassPath EMPTY = new EnsoClassPath(null, null, null);
  private final ModuleLayer.Controller cntrl;
  private final ModuleLayer layer;
  final ClassLoader loader;

  private EnsoClassPath(ModuleLayer.Controller cntrl, ModuleLayer layer, ClassLoader loader) {
    this.cntrl = cntrl;
    this.layer = layer;
    this.loader = loader;
  }

  static EnsoClassPath create(Path file, List<EnsoClassPath> parents) {
    var locations = new ArrayList<Path>();
    var moduleNames = new ArrayList<String>();
    MODULE_LOOP:
    for (var mod : ModuleFinder.of(file).findAll()) {
      if (ModuleLayer.boot().findModule(mod.descriptor().name()).isPresent()) {
        continue;
      }
      for (var p : parents) {
        if (p.layer.findModule(mod.descriptor().name()).isPresent()) {
          continue MODULE_LOOP;
        }
      }
      moduleNames.add(mod.descriptor().name());
      var uri = mod.location().get();
      locations.add(Path.of(uri));
    }
    if (moduleNames.isEmpty() && parents.isEmpty()) {
      return EMPTY;
    } else {
      var finder = ModuleFinder.of(locations.toArray(new Path[0]));
      ModuleLayer.Controller cntrl;
      if (parents.isEmpty()) {
        var parent = ModuleLayer.boot();
        var parentLoader = parent.findLoader("java.base");
        var parentCfgs = Collections.singletonList(parent.configuration());
        var parentModules = Collections.singletonList(parent);
        var cfg =
            Configuration.resolveAndBind(finder, parentCfgs, ModuleFinder.ofSystem(), moduleNames);
        cntrl = ModuleLayer.defineModulesWithOneLoader(cfg, parentModules, parentLoader);
      } else {
        var parentCfgs = parents.stream().map(cp -> cp.layer.configuration()).toList();
        var parentLayers = parents.stream().map(cp -> cp.layer).toList();
        var parentLoader = ModuleLayer.boot().findLoader("java.base");
        var cfg =
            Configuration.resolveAndBind(finder, parentCfgs, ModuleFinder.ofSystem(), moduleNames);
        cntrl = ModuleLayer.defineModulesWithOneLoader(cfg, parentLayers, parentLoader);
      }
      var layer = cntrl.layer();
      var loader =
          !moduleNames.isEmpty() ? layer.findLoader(moduleNames.get(0)) : parents.get(0).loader;
      return new EnsoClassPath(cntrl, layer, loader);
    }
  }
}
