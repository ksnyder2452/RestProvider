package com.restprovider.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerRegistry {
    private final Map<String, BridgeController> controllers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Boolean> enabled = new ConcurrentHashMap<>();

    public void register(BridgeController controller) {
        String key = normalize(controller.getName());
        controllers.put(key, controller);
        enabled.putIfAbsent(key, Boolean.TRUE);
    }

    public BridgeController getController(String name) {
        return controllers.get(normalize(name));
    }

    public boolean isRegistered(String name) {
        return controllers.containsKey(normalize(name));
    }

    public boolean isEnabled(String name) {
        return enabled.getOrDefault(normalize(name), Boolean.FALSE);
    }

    public List<String> getControllerNames() {
        return Collections.unmodifiableList(new ArrayList<>(controllers.keySet()));
    }

    // Single control method to enable or disable any controller by name.
    public boolean setControllerEnabled(String name, boolean isEnabled) {
        String key = normalize(name);
        if (!controllers.containsKey(key)) {
            return false;
        }
        enabled.put(key, isEnabled);
        return true;
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
