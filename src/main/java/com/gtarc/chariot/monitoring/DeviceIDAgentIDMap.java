package com.gtarc.chariot.monitoring;

import com.gtarc.chariot.util.HttpClient;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.HashMap;

public class DeviceIDAgentIDMap {

    private HashMap<String, String> deviceIDtoAgentIDMap = new HashMap<>();
    private HashMap<String, String> agentIDtoKMSUrl = new HashMap<>();
    private HashMap<String, Long> lastDeviceAvailability = new HashMap<>();

    private HttpClient httpClient = new HttpClient();

    public void addNewMapping(String agentID, String deviceID) {
        if (!deviceIDtoAgentIDMap.containsKey(agentID)) {
            this.deviceIDtoAgentIDMap.put(agentID, deviceID);
            try {
                String updateURL = httpClient.addNewDevice(deviceID, agentID);
                agentIDtoKMSUrl.put(agentID, updateURL);
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    public void removeMapping(String agentID) {
        if (deviceIDtoAgentIDMap.containsKey(agentID)) {
            try {
                httpClient.removeDevice(agentIDtoKMSUrl.get(agentID));
            } catch (IOException e) {
                e.printStackTrace();
            }

            deviceIDtoAgentIDMap.remove(agentID);
            agentIDtoKMSUrl.remove(agentID);
        }
    }

    public void updateAvailability(String agentID) {
        this.lastDeviceAvailability.put(agentID, System.currentTimeMillis());
    }

    public long getLastAvailability(String agentID) {
        return this.lastDeviceAvailability.get(agentID);
    }
}
