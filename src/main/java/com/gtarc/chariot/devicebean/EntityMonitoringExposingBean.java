package com.gtarc.chariot.devicebean;

import de.dailab.jiactng.agentcore.action.AbstractMethodExposingBean;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;

public class EntityMonitoringExposingBean extends AbstractMethodExposingBean {

    private String entityID = null;
    private static final String PROPERTY_ACTION = "de.gtarc.chariot.handlePropertyAction";

    public static final String ACTION_GET_ENTITY_ID
            = "com.gtarc.chariot.EntityMonitoringExposingBean#getEntityID";
    
    /**
     * Getter for the entity id.
     * Exposed as global action and used by the monitoring agent
     *
     * @return The entity id
     */
    @Expose(name = ACTION_GET_ENTITY_ID, scope = ActionScope.GLOBAL)
    public String getEntityID(){
        return entityID;
    }

    /**
     * Setter for the entity id
     *
     * @param entityID the entity
     */
    public void setEntityID(String entityID) {
        this.entityID = entityID;
    }
}
