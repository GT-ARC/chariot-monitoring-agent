package com.gtarc.chariot.monitoring.couchdbobjects;

import org.ektorp.support.CouchDbDocument;

/**
 * Object to store in the couchdb database
 */
public class LoadBalancerStore extends CouchDbDocument {
    public long timeStamp;

    public LoadBalancerStore(){}

    public LoadBalancerStore(long timeStamp){
        this.timeStamp = timeStamp;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
}
