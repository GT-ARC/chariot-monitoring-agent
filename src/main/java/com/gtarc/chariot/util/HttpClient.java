package com.gtarc.chariot.util;

import okhttp3.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.Date;

public class HttpClient {

    private static final String url = "http://chariot-km.dai-lab.de:8001/monitoringservice";
    private static final String postfix = "/?format=json";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.parse("text; charset=utf-8");
    private OkHttpClient client = new OkHttpClient();

    public void establishConnection() {
        Request request = new Request.Builder()
                .url(url + postfix)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.body().string().equals("[]")) {
                pushInitialData();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushInitialData() throws IOException {
        JSONObject obj = new JSONObject();
        JSONObject timeStamp = new JSONObject();
        timeStamp.put("timestamp", 0);
        obj.put("loadbalancer", timeStamp);
        obj.put("agent_list", "[]");

        RequestBody body = RequestBody.create(JSON, obj.toJSONString());

        Request request = new Request.Builder()
                .url(url + postfix)
                .put(body)
                .build();

        client.newCall(request).execute();
    }

    /**
     * Sets the current time in mills in the data base
     */
    public void updateLoadBalancer() {
        RequestBody body = RequestBody.create(TEXT, String.valueOf(new Date().getTime()));

        Request request = new Request.Builder()
                .url(url + "/loadbalancer" + postfix)
                .post(body)
                .build();

        try {
            client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a new device into the device list in the kms
     * @param deviceID the device id of the device agent
     * @param agentID the agent id
     * @return The url of the new entry
     * @throws IOException
     */
    public String addNewDevice(String deviceID, String agentID) throws IOException, ParseException {
        JSONObject obj = new JSONObject();
        obj.put("deviceID", deviceID);
        obj.put("agentID", agentID);

        RequestBody body = RequestBody.create(JSON, obj.toJSONString());

        Request request = new Request.Builder()
                .url(url + "/agent_list" + postfix)
                .post(body)
                .build();

        JSONParser parser = new JSONParser();
        try (Response response = client.newCall(request).execute()) {
            JSONObject jsonObject = (JSONObject) parser.parse(response.body().string());
            return jsonObject.get("url").toString();
        }
    }

    public String removeDevice(String deviceUrl) throws IOException {
        Request request = new Request.Builder()
                .url(deviceUrl).delete().build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public JSONObject getAllMappings() throws IOException, ParseException {
        Request request = new Request.Builder()
                .url(url + postfix)
                .get()
                .build();
        JSONParser parser = new JSONParser();
        try (Response response = client.newCall(request).execute()) {
            return (JSONObject) parser.parse(response.body().string());
        }
    }
}
