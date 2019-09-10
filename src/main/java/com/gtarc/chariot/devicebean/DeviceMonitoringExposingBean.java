package com.gtarc.chariot.devicebean;

import de.dailab.jiactng.agentcore.action.AbstractMethodExposingBean;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;

public class DeviceMonitoringExposingBean extends AbstractMethodExposingBean {

    public static final String ACTION_CHECK_AVAILABILITY
            = "com.gtarc.chariot.DeviceMonitoringExposingBean#checkAvailability";

    @Expose(name = ACTION_CHECK_AVAILABILITY, scope = ActionScope.GLOBAL)
    public boolean checkAvailability(){
        return true;
    }
}
