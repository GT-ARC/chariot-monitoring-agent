package com.gtarc.chariot.monitoring;

import com.gtarc.chariot.util.HttpClient;
import de.dailab.jiactng.agentcore.IAgent;
import de.dailab.jiactng.agentcore.SimpleAgentNode;
import de.dailab.jiactng.agentcore.action.AbstractMethodExposingBean;
import de.dailab.jiactng.agentcore.action.Action;
import de.dailab.jiactng.agentcore.action.ActionResult;
import de.dailab.jiactng.agentcore.action.scope.ActionScope;
import de.dailab.jiactng.agentcore.environment.ResultReceiver;
import de.dailab.jiactng.agentcore.execution.AbstractExecutionCycle;
import de.dailab.jiactng.agentcore.lifecycle.LifecycleException;
import de.dailab.jiactng.agentcore.ontology.AgentDescription;
import de.dailab.jiactng.agentcore.ontology.IActionDescription;
import de.dailab.jiactng.agentcore.ontology.IAgentDescription;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class EntityMonitoringAgent extends AbstractMethodExposingBean implements ResultReceiver {

    // Loadbalancer stuff START
    private static final String loadBalancerDocID = "loadBalancerDocID"; // The couchDB document id to check for loadbalancer status

    private boolean isLoadBalancer = false; // Field if the Monitoring-Agent is current loadbalancer
    private String loadBalancerAgentID;     // Active loadbalancer

    private boolean startable = true;            // Flag if a new loadbalancer ist startable
    private long agentResponseTimestamp = -1;    // Timestamp when a new agent is started
    private int agentResponseTimeout;            // Duration how long the loadbalancer is waiting. XML-
    private int agentAmount = 1;                 // Current active agents
    private String lastStartedAgent = null;      // agentID of the last Started agent
    private ArrayList<String> timeoutedAgents = new ArrayList<>();  // List of timeouted agents

    private String lastPrint = "";
    private int compactTimer = 0;

    // List of Device-Agent ids which are not send to the current loadbalancer
    private ArrayList<String> timeOutSendFailed = new ArrayList<>();

    private static final String actionNameGetDeviceID = "com.gtarc.chariot.DeviceMonitoringExposingBean#getEntityID";

    // Loadbalancer stuff END

    public enum States {START, RUNNING, END} // Monitoring-Agent states

    private States state = States.START;    // Current Monitoring-Agent state

    private boolean allFound = false;       // Flag if all Monitoring-Agents are found in the START state
    private int receivedMonitoringAgentAmount = -1; // Temp field that stores the received agent amount
    private HashMap<String, ArrayList<String>> monitoringAgentDeviceMap;  // Maps all existing agents to there monitored services
    private HashMap<String, IActionDescription> deviceList;  // Keeps track of the currently monitored devices
    private HashMap<String, Long> deviceAgentTimeout;          // Map that stores agentTimeouts

    private int servicesPerAgent;                           // How many services a agent can hold

    private HashMap<String, Long> monitoringAgentTimeout;          // Map that stores agentTimeouts
    private ArrayList<String> timeoutedServices;

    private int timeout;                // ServiceTimeout
    private int availabilityInterval;

    private EntityIDAgentIDMap mapper = new EntityIDAgentIDMap();
    private HttpClient httpClient = new HttpClient();

    /**
     * Init monitoring-Bean and init DB connection.
     *
     * @throws Exception
     */
    @Override
    public void doStart() throws Exception {
        super.doStart();
        log.info("DeviceAgentBean - starting....");
        log.info("DeviceAgentBean - my ID: " + this.thisAgent.getAgentId());
        log.info("DeviceAgentBean - my Name: " + this.thisAgent.getAgentName());
        log.info("DeviceAgentBean - my Node: " + this.thisAgent.getAgentNode().getName());

        // Init Hashmaps
        this.monitoringAgentDeviceMap = new HashMap<>();
        this.monitoringAgentTimeout = new HashMap<>();
        this.deviceAgentTimeout = new HashMap<>();
        this.timeoutedServices = new ArrayList<>();
        this.deviceList = new HashMap<>();

        httpClient.establishConnection();

        JSONObject dataBaseState = httpClient.getAllMappings();
        if (dataBaseState.containsKey("loadbalancer")) {
            long dbLoadbalancer = (long) ((JSONObject) dataBaseState.get("loadbalancer")).get("timestamp");
            if (new Date().getTime() - dbLoadbalancer > this.getExecutionInterval() * 3) {
                this.isLoadBalancer = true;
                httpClient.updateLoadBalancer();
                httpClient.removeAllEntities();
            }
        } else {
            this.isLoadBalancer = true;
            httpClient.updateLoadBalancer();
        }

        // TODO write db mapper stuff

        log.info("MonitoringAgentBean - is load balancer: " + this.isLoadBalancer);
    }

    /**
     * Logic for the Monitoring-Agent
     */
    @Override
    public synchronized void execute() {
        switch (state) {
            case START:
                // if the started agent is loadbalancer no starting procedure is needed
                if (this.isLoadBalancer) {
                    this.loadBalancerAgentID = thisAgent.getAgentId();
                    this.state = States.RUNNING;
                } else {
                    // check if all ready received answer of current loadbalancer if so search for Agents
                    if (this.receivedMonitoringAgentAmount == -1) {

                        List<IActionDescription> actionsDescGetID = thisAgent.searchAllActions(new Action("ACTION_GET_AGENT_AMOUNT"));

                        // Iterate through found actions and send to all except own action
                        for (IActionDescription actionDesc : actionsDescGetID) {
                            if (!actionDesc.getProviderDescription().getAid().equals(thisAgent.getAgentId())) {

                                // Wait for answer
                                ActionResult amount = invokeAndWaitForResult(actionDesc, null);
                                if (amount != null && amount.getResults() != null && amount.getResults()[0] instanceof Integer) {
                                    int monitoringAgentAmount = (int) amount.getResults()[0];
                                    log.info("Monitoring-Agent: received Agent Amount" + monitoringAgentAmount);
                                    // if the answer came from unauthorised load balancer continue with next action description
                                    if (monitoringAgentAmount == -1)
                                        continue;

                                    // if the current loadbalancer send monitoring agent amount set necessary
                                    this.loadBalancerAgentID = actionDesc.getProviderDescription().getAid();
                                    this.receivedMonitoringAgentAmount = monitoringAgentAmount;
                                    this.agentAmount = monitoringAgentAmount;
                                    break;
                                }
                            }
                        }
                    } else {
                        // Get all Services of the monitoring agents that are currently active
                        if (!this.allFound) {
                            // Get all monitored services of each Monitoring Agent
                            this.allFound = fillMonitoringAgentServiceMap(this.receivedMonitoringAgentAmount);
                        } else {
                            // Symbolize the current loadbalancer that he can start the next monitoring Agent if necessary
                            List<IActionDescription> actionsDescGetID = thisAgent.searchAllActions(new Action("ACTION_SET_STARTABLE"));
                            for (IActionDescription actionDesc : actionsDescGetID) {
                                if (actionDesc.getProviderDescription().getAid().equals(this.loadBalancerAgentID)) {
                                    ActionResult loadBalancerResult = invokeAndWaitForResult(actionDesc, new Serializable[]{thisAgent.getAgentId()});
                                    if (loadBalancerResult != null && loadBalancerResult.getResults() != null
                                            && loadBalancerResult.getResults()[0] instanceof Boolean
                                            && ((Boolean) loadBalancerResult.getResults()[0])) {

                                        log.info("Monitoring-Agent: Change STATE to RUNNING");
                                        // Change to state running only if send to correct loadbalancer and received answer
                                        state = States.RUNNING;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            case RUNNING:
                // get the action list of a new Service if the serviceMap isn't full
                if (deviceList.size() < servicesPerAgent)
                    receiveNewDevices();

                // Invoke saved actions
                invokeAvailabilityAction();

                // Check for service timeouts.
                // Timeout time is specified in the Chariot_Device_Monitoring.XML
                checkLocalDevicesForTimeout();

                // Send alive message to other Device Agents and check for timeouts
                setNewTimeStamps();
                checkMonitoringAgentsForTimeout();

                // Resend all failed timeout messages to the load balancer
                if (this.timeOutSendFailed.size() != 0) {
                    String[] timeOutedAgents = new String[this.timeOutSendFailed.size()];
                    int counter = 0;
                    for (String agent : this.timeOutSendFailed) {
                        timeOutedAgents[counter] = this.timeOutSendFailed.get(counter);
                        counter++;
                    }

                    for (String timeoutedAgent : timeOutedAgents) {
                        sendTimeoutMessage(timeoutedAgent);
                    }
                }

                // do loadbalancer stuff
                if (this.isLoadBalancer) {
                    int serviceAmount = thisAgent.searchAllActions(new Action(actionNameGetDeviceID)).size();
                    httpClient.updateLoadBalancer();

                    String printString = "Agent amount: " + this.agentAmount + " Service amount: " + serviceAmount;
                    if (lastPrint.isEmpty() || !lastPrint.equals(printString)) {
                        this.lastPrint = printString;
                        log.info("Loadbalancer - " + printString);
                    }

                    // Check if started agent is timeouted
                    if (!this.startable && (System.currentTimeMillis() - this.agentResponseTimestamp) > this.agentResponseTimeout) {
                        log.info("Started Agent timeouted in starting process");
                        this.startable = true;
                        this.agentAmount -= 1;
                        timeoutedAgents.add(lastStartedAgent);

                    } else if (startable && (this.agentAmount <= 1 || (double) serviceAmount / (double) this.agentAmount > this.servicesPerAgent)) {
                        log.info("Loadbalancer - New Monitoring Agent started with " + printString);
                        this.agentAmount += 1;
                        this.startable = false;
                        this.agentResponseTimestamp = System.currentTimeMillis();

                        String lastStartedAgentid = null;
                        try {
                            lastStartedAgentid = startNewAgent();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (lastStartedAgentid == null) {
                            log.error("Couldn't start a new Agent");
                        } else {
                            this.sendNewAgentAmount(this.agentAmount);
                            this.lastStartedAgent = lastStartedAgentid;
                        }
                    }
                }
                break;
            case END:
                try {
                    sendTimeoutMessage(thisAgent.getAgentId());
                    thisAgent.stop();
                    thisAgent.getAgentNode().removeAgent(thisAgent);

                    //SimpleAgentNode agentNode = (SimpleAgentNode) thisAgent.getAgentNode();
                    //agentNode.shutdown();
                } catch (LifecycleException e) {
                }
                break;
        }
    }

    /**
     * Startes new Agent and new Node
     */
    private String startNewAgent() throws IOException {
        //Runtime.getRuntime().exec("java -jar ");

        IAgent newAgent = MonitoringAgentStarter.addAgent("MonitoringAgent", (SimpleAgentNode) thisAgent.getAgentNode());
        return newAgent.getAgentId();
    }

    /**
     * Search for all active Monitoring Agent to receive their service list
     *
     * @param monitoringAgentAmount amount of current active agents received from the current loadbalancer
     * @return <b>true</b> if all Agents found and received answer <br>
     * <b>false</b> if not all actions found
     */
    private boolean fillMonitoringAgentServiceMap(int monitoringAgentAmount) {
        List<IActionDescription> actionDescriptons = thisAgent.searchAllActions(new Action("ACTION_GET_MONITORED_DEVICES"));
        // System.out.println(thisAgent.getAgentId() + ": Found MonitoringAgent: " + actionDescriptons.size() + " of " + monitoringAgentAmount + " agents.                                                      ");

        // Check if all actions found
        if (actionDescriptons.size() < monitoringAgentAmount)
            return false;

        for (IActionDescription action : actionDescriptons) {

            String ownAgentID;
            // Check if the found action is your own
            if (!action.getProviderDescription().getAid().equals((ownAgentID = thisAgent.getAgentId()))) {

                // Invoke action with agentID to instantiate new List of serviceList
                ActionResult actionResult = invokeAndWaitForResult(action, new Serializable[]{ownAgentID});

                // Check for correct result
                if (actionResult != null && actionResult.getResults() != null
                        && actionResult.getResults()[0] instanceof String
                        && actionResult.getResults()[1] instanceof String[]) {

                    // Parse actionresults
                    String agentID = (String) actionResult.getResults()[0];
                    String[] tempServiceIDs = (String[]) actionResult.getResults()[1];

                    // Get agentIDs out of received results
                    ArrayList<String> serviceIDList = new ArrayList<String>();
                    for (String serviceID : tempServiceIDs)
                        if (serviceID != null)
                            serviceIDList.add(serviceID);

                    // Log te received string
                    StringBuilder printString = new StringBuilder("Monitoring-Agent: Agent " + agentID + " monitores: ");
                    for (String s : tempServiceIDs)
                        printString.append(s).append(", ");
                    printString.append("\n");
                    log.info(printString.toString());

                    // Store service list
                    this.monitoringAgentDeviceMap.put(agentID, serviceIDList);
                } else {
                    // if one action result is faulty return false
                    return false;
                }
            }
        }

        // All actions found and received service lists
        return true;
    }

    /**
     * Invokes the PingAction and measures the answer time
     */
    private void receiveNewDevices() {
        // Get all available actions provider
        List<IActionDescription> actionDescriptons = thisAgent.searchAllActions(new Action(actionNameGetDeviceID));
        ArrayList<String> foundServices = new ArrayList<>();

        for (IActionDescription action : actionDescriptons) {

            // Get provider service ID
            String deviceAgentID = action.getProviderDescription().getAid();
            foundServices.add(deviceAgentID);
            // Check if agent is timeouted and if continue (it takes time until a timeouted service isn't visible anymore)
            if (this.timeoutedServices.contains(deviceAgentID))
                continue;

            if (!this.deviceList.containsKey(deviceAgentID) && this.deviceList.size() < this.servicesPerAgent
                    && !this.checkIfServiceIsMonitored(deviceAgentID)) {

                // Create new local service entry and send other agents the new service monitored
                mapper.updateAvailability(deviceAgentID);
                this.deviceList.put(deviceAgentID, action);
                sendAlteredMonitoredService(false, deviceAgentID);
                removeState(action);
                log.info("Monitoring-Agent - added new Device: " + deviceAgentID + " monitoring " + this.deviceList.size() + " devices. ");

                // Invoke action with serviceID and this object as callback
                invoke(action, null, this);
            }
        }


        // Check if timeouted service can still be found and if not remove from timeout list
        ArrayList<String> removableServices = new ArrayList<>();
        for (String serviceID : this.timeoutedServices) {
            if (!foundServices.contains(serviceID)) {
                // send altered information
                sendAlteredMonitoredService(true, serviceID);
                removableServices.add(serviceID);
            }
        }
        // Del service from list
        for (String removableSerive : removableServices)
            this.timeoutedServices.remove(removableSerive);

    }

    /**
     * Removes the state out of the action description
     *
     * @param actionDescription Action description in which the state shall be removed
     */
    private void removeState(IActionDescription actionDescription) {
        IAgentDescription prevAgentDesc = actionDescription.getProviderDescription();
        AgentDescription agentDescription = new AgentDescription();
        agentDescription.setAid(prevAgentDesc.getAid());
        agentDescription.setAgentNodeUUID(prevAgentDesc.getAgentNodeUUID());
        agentDescription.setMessageBoxAddress(prevAgentDesc.getMessageBoxAddress());
        agentDescription.setName(prevAgentDesc.getName());

        actionDescription.setProviderDescription(agentDescription);
    }

    /**
     * Invoke the saved monitoring actions in each saved service obj
     */
    private void invokeAvailabilityAction() {
        // Iterate through devices in local device map
        for (Map.Entry<String, IActionDescription> localMonitoredDevice : deviceList.entrySet()) {
            if (System.currentTimeMillis() - mapper.getLastAvailability(localMonitoredDevice.getKey()) > availabilityInterval) {
                mapper.updateAvailability(localMonitoredDevice.getKey());
                removeState(localMonitoredDevice.getValue());
                invoke(localMonitoredDevice.getValue(), null, this);
            }
        }
    }

    /**
     * Cycle through stored agents and checks for timeouted agents updates timeouted agent in DB
     * Sends altered service information to Monitoring-Agents
     */
    private void checkLocalDevicesForTimeout() {
        ArrayList<String> toBeDeleted = new ArrayList<>();
        for (Map.Entry<String, Long> elements : deviceAgentTimeout.entrySet()) {
            long currentTimeMillis = System.currentTimeMillis();
            // Check if agent is currently available
            if (currentTimeMillis - elements.getValue() > this.timeout) {

                log.info("Service timeout detected: " + elements.getKey());
                sendUpdateToProxyAgent(elements.getKey(), mapper.getDeviceID(elements.getKey()), true);
                mapper.removeMapping(elements.getKey());
                this.timeoutedServices.add(elements.getKey());

                // mark service to be deleted
                toBeDeleted.add(elements.getKey());

            }
        }

        // Delete the object from the Hashmap
        for (String deletableObject : toBeDeleted) {
            this.deviceList.remove(deletableObject);
            this.deviceAgentTimeout.remove(deletableObject);
        }

        // Check if there are monitored services left
        if (toBeDeleted.size() != 0 && this.deviceList.size() == 0 && this.agentAmount > 2 && !this.isLoadBalancer) {
            this.state = States.END;
            log.info("No Services to monitor. Change State to End");
        }
    }

    /**
     * Functions that checks received timestamps for timeouts and sends detected
     * timeouts to the current load balancer
     */
    private void checkMonitoringAgentsForTimeout() {

        ArrayList<String> toBeDeleted = new ArrayList<>();
        Set<String> monitoringAgentIDs = monitoringAgentTimeout.keySet();
        for (String monitoringAgentID : monitoringAgentIDs) {
            long currentTimeMillis = System.currentTimeMillis();

            // System.out.println("Agent: " + monitoringAgentIDs + " last update: " + (currentTimeMillis - this.monitoringAgentTimeout.get(monitoringAgentID)));

            // Check if agent is currently available
            if (currentTimeMillis - this.monitoringAgentTimeout.get(monitoringAgentID) > this.timeout) {
                log.info("Monitoring-Agent - detected timeout of " + monitoringAgentID + " | " + this.loadBalancerAgentID.equals(monitoringAgentID));
                toBeDeleted.add(monitoringAgentID);

                // If it fails to send the current loadbalancer the message because of a not found action
                // save the id and retry in the next cycle
                sendTimeoutMessage(monitoringAgentID);
            }
        }

        // Cycle through to be deleted objects
        for (String delObj : toBeDeleted) {
            this.monitoringAgentDeviceMap.remove(delObj);
            this.monitoringAgentTimeout.remove(delObj);

            // Check if current load balancer timeouted and if so set the new loadbalancer id to the smallest
            if (delObj.equals(this.loadBalancerAgentID) && (this.loadBalancerAgentID = getSmallestMonitoringAgentID()).equals(thisAgent.getAgentId())) {
                this.isLoadBalancer = true;
                this.timeoutedAgents.add(delObj);   // Add the deleted obj to the timeouted agent list so it isn't reported twice
                this.agentAmount -= 1;
                log.info("Monitoring-Agent - new load balancer selected");
            }
        }
    }

    /**
     * Searches for the smallest monitoring agent id in terms of lexicographic order
     *
     * @return the smallest monitoring agent id
     */
    private String getSmallestMonitoringAgentID() {
        String smallst = thisAgent.getAgentId();
        for (String monitoringAgentID : this.monitoringAgentDeviceMap.keySet()) {
            if (monitoringAgentID.compareTo(smallst) < 0)
                smallst = monitoringAgentID;
        }
        return smallst;
    }

    /**
     * Calls the timeout action of the current loadbalancer checks for success
     *
     * @param monitoringAgentID the timeouted agent
     */
    private void sendTimeoutMessage(String monitoringAgentID) {
        // Send the current load balancer that a agent is timeouted
        String actionName = "ACTION_AGENT_TIMEOUT_DETECTED";
        List<IActionDescription> actionDescriptons = thisAgent.searchAllActions(new Action(actionName));
        for (IActionDescription action : actionDescriptons) {
            // Check if found action is current load balancer
            if (action.getProviderDescription().getAid().equals(this.loadBalancerAgentID)) {
                ActionResult loadBalancerResult = invokeAndWaitForResult(action, new Serializable[]{monitoringAgentID});

                // check if returned result is faulty and from the current load balancer
                if (loadBalancerResult != null && loadBalancerResult.getResults() != null
                        && loadBalancerResult.getResults()[0] instanceof Boolean
                        && ((Boolean) loadBalancerResult.getResults()[0])) {

                    // Check if the timeout message failed to send once
                    if (this.timeOutSendFailed.contains(monitoringAgentID)) {
                        // Due to succeed sending remove from list
                        this.timeOutSendFailed.remove(monitoringAgentID);
                    }

                    // Successfully send timeout message to current loadbalancer
                    return;
                }
            }
        }
        // If not successfully send add the agent to the "to be send" list
        if (!this.timeOutSendFailed.contains(monitoringAgentID))
            timeOutSendFailed.add(monitoringAgentID);
    }

    /**
     * Send all active Monitoring-Agent a alive message.
     */
    private void setNewTimeStamps() {
        List<IActionDescription> actionDescriptons = thisAgent.searchAllActions(new Action("ACTION_SET_NEW_TIMESTAMP"));
        for (IActionDescription action : actionDescriptons) {
            // Check if the found action is your own
            if (!action.getProviderDescription().getAid().equals(thisAgent.getAgentId())) {
                // Invoke action with agentID to instantiate new List of serviceIDs
                invoke(action, new Serializable[]{thisAgent.getAgentId()});
            }
        }
    }

    /**
     * Calls the <b>ACTION_ADD_MONITORED_SERVICE</b> or <b>ACTION_REMOVE_MONITORED_SERVICE</b> in all active Monitoring-Agents
     * depending on the removed flag
     *
     * @param removedFlag <b>true</b> when service is removed or timeouted
     *                    <b>false</b> when new service is added
     * @param serviceID   added serviceID
     */
    private void sendAlteredMonitoredService(boolean removedFlag, String serviceID) {
        List<IActionDescription> actionDescriptons;

        // Depending on the removal or adding of an service search for a different action
        if (!removedFlag)
            actionDescriptons = thisAgent.searchAllActions(new Action("ACTION_ADD_MONITORED_SERVICE"));
        else
            actionDescriptons = thisAgent.searchAllActions(new Action("ACTION_REMOVE_MONITORED_SERVICE"));

        for (IActionDescription action : actionDescriptons) {
            // Check if the found action is your own
            if (!action.getProviderDescription().getAid().equals(thisAgent.getAgentId())) {
                // Invoke action with agentID to instantiate new List of serviceList
                if (!removedFlag)
                    // Get answer if something went wrong
                    invoke(action, new Serializable[]{thisAgent.getAgentId(), serviceID}, this);
                else
                    invoke(action, new Serializable[]{thisAgent.getAgentId(), serviceID});
            }
        }
    }

    /**
     * Checks if the given serviceID is monitored by another Monitoring-Agent.
     *
     * @param serviceID to be checked
     * @return <b>true</b> if service is monitored <br>
     * <b>false</b> if service is not monitored
     */
    private boolean checkIfServiceIsMonitored(String serviceID) {
        // Cycle trough all monitored services and return if service is found
        for (ArrayList<String> serviceIDs : this.monitoringAgentDeviceMap.values()) {
            if (serviceIDs.contains(serviceID)) {
                return true;
            }
        }

        // No agent is found
        return false;
    }

    /**
     * Sends the new amount of running monitoring agents to all other monitoring agents
     *
     * @param agentAmount the to be send agent amount
     */
    private void sendNewAgentAmount(int agentAmount) {
        // Send new Agentamount
        List<IActionDescription> actionsDescGetID = thisAgent.searchAllActions(new Action("ACTION_SET_AGENT_AMOUNT"));
        for (IActionDescription actionDesc : actionsDescGetID) {
            if (!actionDesc.getProviderDescription().getAid().equals(thisAgent.getAgentId())) {
                invoke(actionDesc, new Serializable[]{agentAmount});
            }
        }
    }

    /**
     * Callback method for action results to receive quality of service information
     *
     * @param actionResult the result of the called monitoring action
     */
    @Override
    public synchronized void receiveResult(ActionResult actionResult) {

        if (actionResult.getFailure() != null && !(actionResult.getFailure() instanceof AbstractExecutionCycle.TimeoutException)) {
            String agentID = actionResult.getAction().getProviderDescription().getAid();
            log.info("Service " + agentID + " returned faulty message.");
            log.info(actionResult.getFailure() + " " + actionResult.getAction().getName());
            this.deviceList.remove(agentID);
        }


        if (actionResult != null && actionResult.getResults() != null) {
            // If two agents add a Service at the same time one will discover that the other trys to add the same
            // if detected send an false add in return.
            if (actionResult.getAction().getName().equals("ACTION_ADD_MONITORED_SERVICE")
                    && actionResult.getResults()[0] instanceof String
                    && actionResult.getResults()[1] instanceof Boolean) {

                String remoteMonitoringAgentID = actionResult.getAction().getProviderDescription().getAid();

                String serviceID = (String) actionResult.getResults()[0];
                boolean serviceStatus = (Boolean) actionResult.getResults()[1];

                if (!serviceStatus)
                    log.info("Monitoring-Agent - Received faulty ACTION_ADD result for service: " + serviceID);

                if (!serviceStatus && this.deviceList.containsKey(serviceID) && thisAgent.getAgentId().compareTo(remoteMonitoringAgentID) < 0) {
                    // If false returned remove service from local map and send alterd services
                    this.deviceList.remove(serviceID);
                    this.sendAlteredMonitoredService(true, serviceID);
                    if (this.deviceList.size() == 0 && this.agentAmount > 2) {
                        this.state = States.END;
                    }
                    log.info(thisAgent.getAgentId() + ": removed added service: " + serviceID);
                }
            }
            // Check if respond is from right action and results are correctly
            else {
                // Check if device is in id map
                String deviceAgentID = actionResult.getAction().getProviderDescription().getAid();
                String actionName = actionResult.getAction().getName();

                if (deviceList.containsKey(deviceAgentID) && actionName.equals(actionNameGetDeviceID)) {
                    long currentTimeStamp = System.currentTimeMillis();
                    deviceAgentTimeout.put(deviceAgentID, currentTimeStamp);

                    String deviceID = (String) actionResult.getResults()[0];
                    if (mapper.getDeviceID(deviceAgentID) == null) {
                        sendUpdateToProxyAgent(deviceAgentID, deviceID, false);
                    }
                    mapper.addNewMapping(deviceAgentID, deviceID);

                }
            }
        }
    }

    private static final String ADD_AGENT_ACTION = "com.gtarc.chariot.proxyagent#addAgent";
    private static final String REMOVE_AGENT_ACTION = "com.gtarc.chariot.proxyagent#removeAgent";

    private void sendUpdateToProxyAgent(String deviceAgentID, String deviceID, boolean remove) {
        IActionDescription proxyAgentAction = thisAgent.searchAction(
                new Action(remove ? REMOVE_AGENT_ACTION : ADD_AGENT_ACTION));
        if (proxyAgentAction != null) {
            invoke(proxyAgentAction, new Serializable[]{deviceAgentID, deviceID});
        }
    }


    /* ---- Exposed Actions ---- */

    /**
     * Action name: "ACTION_GET_MONITORED_DEVICES", ActionScope.GLOBAL, {String.class, String[].class} <br>
     * Get all monitored devices.
     *
     * @param monitoringAgentID requested monitoringAgentID
     * @return Own agent id and the monitored services
     */
    @Expose(name = "ACTION_GET_MONITORED_DEVICES", scope = ActionScope.GLOBAL, returnTypes = {String.class, String[].class})
    public Serializable[] getMonitoredServices(String monitoringAgentID) {

        // Add new Service list to the agent service map
        if (!this.monitoringAgentDeviceMap.containsKey(monitoringAgentID))
            this.monitoringAgentDeviceMap.put(monitoringAgentID, new ArrayList<>());

        // Return current monitored services
        String[] deviceIDs = new String[this.servicesPerAgent];
        int counter = 0;
        for (String deviceID : this.deviceList.keySet()) {
            deviceIDs[counter++] = deviceID;
        }
        return new Serializable[]{thisAgent.getAgentId(), deviceIDs};
    }

    /**
     * Action name: ACTION_ADD_MONITORED_SERVICE, ActionScope.GLOBAL, {String.class, Boolean.class} <br>
     * Adds the given serviceAgentID to the monitoringAgentServiceMap of the given monitoringAgentID
     * Checks if the service is all ready monitored by this agent
     *
     * @param monitoringAgentID Monitoring-Agent ID of the agent who tries to add the service
     * @param deviceAgentID     The Service ID of the new monitored service
     * @return A <b>true</b> message if correctly added <br>
     * A <b>false</b> message if given Monitoring-Agent is not allowed to add given service
     */
    @Expose(name = "ACTION_ADD_MONITORED_SERVICE", scope = ActionScope.GLOBAL, returnTypes = {String.class, Boolean.class})
    public Serializable[] addMonitoredService(String monitoringAgentID, String deviceAgentID) {

        if (this.state != States.RUNNING)
            return new Serializable[]{deviceAgentID, true};

        // Added successful flag if true the service is added correctly
        boolean addSucc = false;

        // Check if agent is known.
        if (!this.monitoringAgentDeviceMap.containsKey(monitoringAgentID)) {
            this.monitoringAgentDeviceMap.put(monitoringAgentID, new ArrayList<String>());
        }

        // Check if service is in local service map
        if (this.deviceList.containsKey(deviceAgentID)) {
            // Check if this agent is bigger in lexicographical order
            if (thisAgent.getAgentId().compareTo(monitoringAgentID) > 0) {

                // Other agent is not allowed to monitor the agent
                log.info("Monitoring-Agent - Agent: " + monitoringAgentID + " tried to add monitored service " + deviceAgentID);
                return new Serializable[]{deviceAgentID, false};
            }
            // Agent is allowed to add
            else {
                // Check if his service list is full
                if (this.monitoringAgentDeviceMap.get(monitoringAgentID).size() > this.servicesPerAgent) {
                    log.info("Monitoring-Agent - " + monitoringAgentID + " tryed to add device " + deviceAgentID);
                    return new Serializable[]{deviceAgentID, false};
                } else {
                    this.deviceList.remove(deviceAgentID);
                }
            }
        }

        // Nothing wrong detected so adding was successfully
        this.monitoringAgentDeviceMap.get(monitoringAgentID).add(deviceAgentID);
        return new Serializable[]{deviceAgentID, true};
    }

    /**
     * Action name: ACTION_REMOVE_MONITORED_SERVICE, ActionScope.GLOBAL <br>
     * Removes monitored service from monitoringAgentServiceMap
     *
     * @param monitoringAgentID Monitoring-Agent ID of the agent who tries to remove the service
     * @param deviceAgentID     The Device Agent ID of the removed monitored service
     */
    @Expose(name = "ACTION_REMOVE_MONITORED_SERVICE", scope = ActionScope.GLOBAL)
    public void removeMonitoredService(String monitoringAgentID, String deviceAgentID) {
        if (this.state != States.RUNNING)
            return;

        // check if the given monitoringAgentID is in local agent service map and if service id is in monitored services
        if (this.monitoringAgentDeviceMap.containsKey(monitoringAgentID)) {
            if (this.monitoringAgentDeviceMap.get(monitoringAgentID).contains(deviceAgentID)) {
                this.monitoringAgentDeviceMap.get(monitoringAgentID).remove(deviceAgentID);
            } else {
                log.info("Monitoring-Agent - Agent: " + monitoringAgentID + " tried to remove a non existent service.");
            }
        } else {
            log.info("Monitoring-Agent - Agent: " + monitoringAgentID + " tried to remove an Device but agentDeviceMap does not contain the agentID.");
        }
    }

    /**
     * Action name: ACTION_SET_NEW_TIMESTAMP, ActionScope.GLOBAL <br>
     * Sets new timestamp to the given Monitoring-Agent ID
     *
     * @param monitoringAgentID the Monitoring-Agent ID which send the timeout
     */
    @Expose(name = "ACTION_SET_NEW_TIMESTAMP", scope = ActionScope.GLOBAL)
    public void setNewTimestamp(String monitoringAgentID) {
        if (this.monitoringAgentDeviceMap.containsKey(monitoringAgentID)) {
            this.monitoringAgentTimeout.put(monitoringAgentID, System.currentTimeMillis());
        }
    }

    /**
     * Action name: ACTION_SET_AGENT_AMOUNT, ActionScope.GLOBAL <br>
     * Sets a new agent amount
     *
     * @param agentAmount the new agent amount
     */
    @Expose(name = "ACTION_SET_AGENT_AMOUNT", scope = ActionScope.GLOBAL)
    public void setAgentAmount(int agentAmount) {
        this.agentAmount = agentAmount;
    }


    /* ------ LOADBALANCER ACTIONS ------ */

    /**
     * Action name: ACTION_SET_STARTABLE, ActionScope.GLOBAL <br>
     * If the agent is at the moment of the call the loadbalancer set the startable state to true
     *
     * @return <b>true</b> if this agent is loadbalancer <b>false</b> if not
     */
    @Expose(name = "ACTION_SET_STARTABLE", scope = ActionScope.GLOBAL)
    public boolean setStartable(String remoteAgentId) {
        if (this.isLoadBalancer) {
            this.startable = true;

            // Check if the agent that started is marked as timeouted
            if (this.timeoutedAgents.contains(remoteAgentId)) {
                this.agentAmount += 1;
                this.sendNewAgentAmount(this.agentAmount);
            }
            return true;
        }
        return false;
    }

    /**
     * Action name: ACTION_GET_AGENT_AMOUNT, ActionScope.GLOBAL <br>
     * Returns the current agent amount if this agent is the loadbalancer
     *
     * @return int n > 0 if this agent is loadbalancer else -1
     */
    @Expose(name = "ACTION_GET_AGENT_AMOUNT", scope = ActionScope.GLOBAL)
    public int getAgentAmount() {
        if (this.isLoadBalancer)
            return this.agentAmount;
        return -1;
    }

    /**
     * Action name: ACTION_AGENT_TIMEOUT_DETECTED, ActionScope.GLOBAL <br>
     * Reduces the agent amount by one if the detected timeouted agent is not known
     *
     * @param timeoutedAgentID the Monitoring-Agent ID of the timeouted agent
     * @return <b>true</b> if this agent is loadbalancer <b>false</b> if not
     */
    @Expose(name = "ACTION_AGENT_TIMEOUT_DETECTED", scope = ActionScope.GLOBAL)
    public boolean agentTimeoutDetected(String timeoutedAgentID) {
        if (this.isLoadBalancer) {
            // Check if timeout is all ready reported
            if (!this.timeoutedAgents.contains(timeoutedAgentID)) {
                this.timeoutedAgents.add(timeoutedAgentID);
                this.agentAmount -= 1;
            }
            return true;
        }
        return false;
    }


    /* ----- Getter and Setter for xml values ------ */

    /**
     * Setter for the timeout which is set in the ChariotEntityMonitoringAgentConfig.xml
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }


    /**
     * Setter for the service per agent field
     *
     * @param servicesPerAgent new service Per Agent
     */
    public void setServicesPerAgent(int servicesPerAgent) {
        this.servicesPerAgent = servicesPerAgent;
    }

    public void setDeviceMonitoringStartResponseTimeout(int monitoringStartResponseTimeout) {
        this.agentResponseTimeout = monitoringStartResponseTimeout;
    }

    public void setAvailabilityInterval(int availabilityInterval) {
        this.availabilityInterval = availabilityInterval;
    }

}
