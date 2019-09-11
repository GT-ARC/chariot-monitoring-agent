package com.gtarc.chariot.devicebean;

import de.dailab.jiactng.agentcore.SimpleAgentNode;
import de.dailab.jiactng.agentcore.lifecycle.LifecycleException;
import org.springframework.context.ApplicationContext;

public class ExampleDeviceAgent extends DeviceMonitoringExposingBean {

    @Override
    public void doStart() throws Exception {
        super.doStart();

        log.info("Example Device Agent started - " + thisAgent.getAgentId());
    }

    public static void main(String[] args){
        startNewDeviceAgent();
    }

    private static void startNewDeviceAgent() {
        ApplicationContext context = SimpleAgentNode.startAgentNode("classpath:ExampleDeviceAgentConfig.xml", "jiactng_log4j.properties");
        SimpleAgentNode node = (SimpleAgentNode) context.getBean("DeviceNode");
        try {
            node.start();
        } catch (LifecycleException e) {
            e.printStackTrace();
        }
    }

}
