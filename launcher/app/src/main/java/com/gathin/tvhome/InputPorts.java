package com.gathin.tvhome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An HDMI-CEC device is listed by the framework as an input of its own whose parent is its HDMI port, so a
 * PlayStation on HDMI 1 would get two rows (seen on Sony). The Inputs menu folds the first device of each
 * port into the port's row and switches to the device, the way the TV's own input panel does.
 */
final class InputPorts {
    private InputPorts() { }

    /**
     * Port id to the id of the first device input listed under it. {@code ids} and {@code parentIds} run in
     * parallel, a null parent marking a port or an AV input; a device whose port is not listed keeps its row.
     */
    static Map<String, String> deviceByPort(List<String> ids, List<String> parentIds) {
        Set<String> listed = new HashSet<>(ids);
        Map<String, String> devices = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String port = parentIds.get(i);
            if (port != null && listed.contains(port)) devices.putIfAbsent(port, ids.get(i));
        }
        return devices;
    }
}
