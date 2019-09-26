package com.gtarc.chariot.devicebean;

import de.dailab.jiactng.agentcore.action.AbstractMethodExposingBean;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;

public class DeviceMonitoringExposingBean extends AbstractMethodExposingBean {

    private static String deviceID;

    private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHAR_UPPER = CHAR_LOWER.toUpperCase();
    private static final String NUMBER = "0123456789";

    private static final String DATA_FOR_RANDOM_STRING = CHAR_LOWER + CHAR_UPPER + NUMBER;
    private static SecureRandom random = new SecureRandom();

    public static final String ACTION_GET_DEVICE_ID
            = "com.gtarc.chariot.DeviceMonitoringExposingBean#getDeviceID";

    @Override
    public void doStart() throws Exception {
        deviceID = generateRandomString(10);
    }

    @Expose(name = ACTION_GET_DEVICE_ID, scope = ActionScope.GLOBAL)
    public String getDeviceID(){
        return deviceID;
    }


    public static String generateRandomString(int length) {
        if (length < 1) throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {

            // 0-62 (exclusive), random returns 0-61
            int rndCharAt = random.nextInt(DATA_FOR_RANDOM_STRING.length());
            char rndChar = DATA_FOR_RANDOM_STRING.charAt(rndCharAt);

            sb.append(rndChar);

        }

        return "d-"+sb.toString();

    }
}
