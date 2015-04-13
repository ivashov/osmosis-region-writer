package com.github.ivashov.osmregionwriter.plugin;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkManager;

public class Factory extends TaskManagerFactory {
    @Override
    protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
        String file = getStringArgument(taskConfig, "file", "output.txt");

        Task task = new Task(file);
        return new SinkManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
    }
}
