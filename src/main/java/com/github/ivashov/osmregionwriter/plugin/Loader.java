package com.github.ivashov.osmregionwriter.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.plugin.PluginLoader;

import java.util.HashMap;
import java.util.Map;

public class Loader implements PluginLoader {
    @Override
    public Map<String, TaskManagerFactory> loadTaskFactories() {
        Factory factory = new Factory();

        return new HashMap<String, TaskManagerFactory>() {{
            put("write-region-dir", factory);
            put("wrd", factory);
        }};
    }
}
