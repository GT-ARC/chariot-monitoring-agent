package com.gtarc.chariot.devicebean;

import de.dailab.jiactng.agentcore.action.AbstractMethodExposingBean;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class DeviceMonitoringExposingBean extends AbstractMethodExposingBean {

    private static String deviceID;

    public static final String ACTION_GET_DEVICE_ID
            = "com.gtarc.chariot.DeviceMonitoringExposingBean#getDeviceID";

    @Override
    public void doStart() throws Exception {
        byte[] array = new byte[16];
        new Random().nextBytes(array);
        deviceID = new String(array, StandardCharsets.UTF_8);
    }

    @Expose(name = ACTION_GET_DEVICE_ID, scope = ActionScope.GLOBAL)
    public String getDeviceID(){
        return deviceID;
    }

}
