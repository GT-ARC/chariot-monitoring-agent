package com.gtarc.chariot.monitoring;

import de.dailab.jiactng.agentcore.IAgent;
import de.dailab.jiactng.agentcore.SimpleAgentNode;
import de.dailab.jiactng.agentcore.lifecycle.LifecycleException;

import org.springframework.context.ApplicationContext;


public class MonitoringAgentStarter {

    public static void main(String[] args){

        // use JIAC's default log4j configuraten
        // System.setProperty("log4j.configuration", "jiactng_log4j.properties");
        //new ClassPathXmlApplicationContext("ChariotDeviceMonitoringAgentConfig.xml").start();

        startNewMonitoringAgent();

        // not intended to stop
    }

    public static void startNewMonitoringAgent() {
        ApplicationContext context = SimpleAgentNode.startAgentNode("classpath:ChariotDeviceMonitoringAgentConfig.xml", "jiactng_log4j.properties");
        SimpleAgentNode node = (SimpleAgentNode) context.getBean("MonitoringNode");
        try {
            node.start();
        } catch (LifecycleException e) {
            e.printStackTrace();
        }
        /*
        IAgent newAgent = (IAgent) context.getBean("MonitoringAgent");
        node.addAgent(newAgent);
        try {
            newAgent.init();
            // newAgent.start();
        } catch (LifecycleException e) {
            e.printStackTrace();
        }*/
    }

    public static IAgent addAgent(final String agentType, final SimpleAgentNode node) {
        ApplicationContext context = SimpleAgentNode.startAgentNode("classpath:ChariotDeviceMonitoringAgentConfig.xml", "jiactng_log4j.properties");
        IAgent newAgent = (IAgent) context.getBean(agentType);

        node.addAgent(newAgent);

        try {
            newAgent.init();
            newAgent.start();
        } catch (LifecycleException e) {
            e.printStackTrace();
        }

        return newAgent;
    }
}
