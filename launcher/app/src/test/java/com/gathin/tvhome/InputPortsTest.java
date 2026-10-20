package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InputPortsTest {
    private static final String HDMI1 = "com.sony.dtv.tvinput.external/.ExternalTvInputService/HW2";
    private static final String HDMI2 = "com.sony.dtv.tvinput.external/.ExternalTvInputService/HW3";
    private static final String PS5 = "com.sony.dtv.tvinput.external/.ExternalTvInputService/HDMI100004";
    private static final String SOUNDBAR = "com.sony.dtv.tvinput.external/.ExternalTvInputService/HDMI100005";

    @Test public void aCecDeviceIsFoldedIntoItsPort() {
        Map<String, String> devices = InputPorts.deviceByPort(
                Arrays.asList(HDMI1, HDMI2, PS5), Arrays.asList(null, null, HDMI1));

        assertEquals(Collections.singletonMap(HDMI1, PS5), devices);
    }

    @Test public void portsWithoutDevicesFoldNothing() {
        assertTrue(InputPorts.deviceByPort(Arrays.asList(HDMI1, HDMI2), Arrays.asList(null, null)).isEmpty());
    }

    @Test public void aDeviceWhosePortIsNotListedKeepsItsOwnRow() {
        assertTrue(InputPorts.deviceByPort(Arrays.asList(HDMI2, PS5), Arrays.asList(null, HDMI1)).isEmpty());
    }

    @Test public void onlyTheFirstDeviceOnAPortIsFolded() {
        Map<String, String> devices = InputPorts.deviceByPort(
                Arrays.asList(HDMI1, PS5, SOUNDBAR), Arrays.asList(null, HDMI1, HDMI1));

        assertEquals(Collections.singletonMap(HDMI1, PS5), devices);
    }
}
