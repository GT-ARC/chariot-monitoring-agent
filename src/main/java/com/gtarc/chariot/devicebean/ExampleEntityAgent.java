package com.gtarc.chariot.devicebean;

import de.dailab.jiactng.agentcore.SimpleAgentNode;
import de.dailab.jiactng.agentcore.lifecycle.LifecycleException;
import org.springframework.context.ApplicationContext;

public class ExampleEntityAgent extends EntityMonitoringExposingBean {

    @Override
    public void doStart() throws Exception {
        super.doStart();

        log.info("Example Device Agent started - " + thisAgent.getAgentId());
    }

    public static void main(String[] args) {
        startNewEntityAgent();
    }

    private static void startNewEntityAgent() {
        ApplicationContext context = SimpleAgentNode.startAgentNode("classpath:ExampleEntityAgentConfig.xml", "jiactng_log4j.properties");
        SimpleAgentNode node = (SimpleAgentNode) context.getBean("DeviceNode");
        try {
            node.start();
        } catch (LifecycleException e) {
            e.printStackTrace();
        }
    }

}
