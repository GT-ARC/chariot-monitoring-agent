package com.gtarc.chariot.monitoring.couchdbobjects;

import com.gtarc.chariot.utils.Pair;
import org.ektorp.support.CouchDbDocument;

import java.util.ArrayList;
import java.util.HashMap;

public class DeviceIdToAgentIDStoreObject extends CouchDbDocument {

    private Long lastUpdated;
    private HashMap<String, String> deviceIdToAgentIdMap;

    public DeviceIdToAgentIDStoreObject(){ }

    public Long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public HashMap<String, String> getDeviceIdToAgentIdMap() {
        return deviceIdToAgentIdMap;
    }

    public void setDeviceIdToAgentIdMap(HashMap<String, String> deviceIdToAgentIdMap) {
        this.deviceIdToAgentIdMap = deviceIdToAgentIdMap;
    }
}
