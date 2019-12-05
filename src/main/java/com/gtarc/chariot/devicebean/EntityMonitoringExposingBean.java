package com.gtarc.chariot.devicebean;

import java.security.SecureRandom;

import de.dailab.jiactng.agentcore.action.AbstractMethodExposingBean;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;

public class EntityMonitoringExposingBean extends AbstractMethodExposingBean {

    private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHAR_UPPER = CHAR_LOWER.toUpperCase();
    private static final String NUMBER = "0123456789";

    private static final String DATA_FOR_RANDOM_STRING = CHAR_LOWER + CHAR_UPPER + NUMBER;
    private static SecureRandom random = new SecureRandom();


    private String entityID = null;
    private static final String PROPERTY_ACTION = "de.gtarc.chariot.handlePropertyAction";

    public static final String ACTION_GET_ENTITY_ID
            = "com.gtarc.chariot.EntityMonitoringExposingBean#getEntityID";
    
    @Override
    public void doStart() throws Exception {
        entityID = generateRandomString(10);
    }
        

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
