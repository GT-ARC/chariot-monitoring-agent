# Chariot Device - Monitoring

The Chariot-Monitoring component searches for all Devices in its discovery group
which implement the Device Monitoring Exposing bean and stores the availability into
 CouchDB for further processing. To uphold a significant amount of devices each monitoring
agent can function as a loadbalancer. Starting new monitoring agents
according to the device amount.

## Usage

To build the monitoring component you need the correct
configurations in your maven settings file otherwise you can't download the
needed dependencies.

Configure the couchdb.properties file according to your needs.  
    Eg. set the CouchDB ip-adress.  

To start the monitoring component you either can use
the `startMonitoringAgent` which builds and runs the project in its
own screen or just build the project via maven and use the starter file in
`target/appassembler/bin/` according to your OS.

## Monitoring component internals

In the following section is explained how the components of the monitoring
component function.

## Contact

By questions contact Frederic Abraham: [Frederic-Marvin.Abraham@gt-arc.com](mailto://Frederic-Marvin.Abraham@gt-arc.com) 
