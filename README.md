# Chariot Device - Monitoring

The registration of an IoT entity in CHARIOT middleware is finalized with an agent search process
initiated by the monitoring agent (MA). Each IoT entity contains two special identifiers, namely, UUID and agent-Id. 
UUID is known by all components in CHARIOT middleware, whereas agent-Id is only known by the agent layer. 
MA can provide these two de IDs to the requester such as Proxy Agent, so that the requester can access to the agent with its ID, 
and then call the methods under it.

The process in MA is not automatically triggered by the end of the registration, rather its agent search is executed in a given period by the developer.
The search process considers only the agent that extends IoTExposingBean class, all others will be ignored. For each IoT entity, a new MA will be started .

MA runs based on the XML configuration file locating under `resources/ChartiotEntityMonitoringAgentConfig.xml` file and it points out the gateway agent,
which is designed for the inter-networking communications. For the sake of the simplicity, the gateway agent configuration is kept and the local IP address is assigned. 
If you later decide to deploy monitoring agent and gateway agent on other network component, the gateway configuration should be accordingly adapted.

## Usage

MA can be easily started by executing the `startMonitoringAgent`,  which builds and runs the project in its own screen or just build the project via maven and use the starter file in
`target/appassembler/bin/` according to your OS.

As the execution of MA requires Gateway Agent (GA), please look at the GA and be sure it is started./ 


## Contacts

The following persons can answer your questions: 

- Frederic Abraham: [mail@fabraham.dev](mailto://mail@fabraham.dev)
- Cem Akpolat: [akpolatcem@gmail.com](mailto://akpolatcem@gmail.com)

 
