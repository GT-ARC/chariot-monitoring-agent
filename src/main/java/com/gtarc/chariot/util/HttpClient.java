package com.gtarc.chariot.util;

import okhttp3.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.Date;

public class HttpClient {

    private static String currentUrl = "";
    private static String loadbalancerUrl = "";
    private static String mappingsURL = "";
    private static final String startUrl = "http://chariot-km.dai-lab.de:8001/monitoringservice/";
    private static final String postfix = "?format=json";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.parse("text; charset=utf-8");

    private OkHttpClient client = new OkHttpClient();

    public void establishConnection() {
        Request request = new Request.Builder()
                .url(startUrl + postfix)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                pushInitialData();
                establishConnection();
            } else {
                JSONParser parser = new JSONParser();
                Object receivedO = parser.parse(response.body().string());

                JSONObject monService;

                if (receivedO instanceof JSONArray) monService = ((JSONObject) ((JSONArray) receivedO).get(0));
                else monService = ((JSONObject) receivedO);

                loadbalancerUrl = (String) ((JSONObject) monService.get("loadbalancer")).get("url");
                currentUrl = (String) monService.get("url");

                JSONObject mapping = ((JSONObject) monService.get("agentlist"));
                mappingsURL = (String) mapping.get("url");
                JSONArray jsonArray = (JSONArray) mapping.get("mapping");
                jsonArray.forEach(o -> {
                    try {
                        removeDevice((String) ((JSONObject) o).get("url"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private void pushInitialData() throws IOException {
        JSONObject obj = new JSONObject();
        JSONObject agentList = new JSONObject();
        JSONArray mappings = new JSONArray();
        JSONObject timeStamp = new JSONObject();

        agentList.put("mappings", mappings);
        timeStamp.put("timestamp", 0);
        obj.put("loadbalancer", timeStamp);
        obj.put("agentlist", agentList);

        RequestBody body = RequestBody.create(JSON, obj.toJSONString());

        Request request = new Request.Builder()
                .url(currentUrl + postfix)
                .put(body)
                .build();

        client.newCall(request).execute();
    }

    /**
     * Sets the current time in mills in the data base
     */
    public void updateLoadBalancer() {
        JSONObject timeStamp = new JSONObject();
        timeStamp.put("timestamp", String.valueOf(new Date().getTime()));

        RequestBody body = RequestBody.create(JSON, timeStamp.toJSONString());

        Request request = new Request.Builder()
                .url(loadbalancerUrl)
                .put(body)
                .build();
        try {
            client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a new device into the device list in the kms
     *
     * @param deviceID the device id of the device agent
     * @param agentID  the agent id
     * @return The url of the new entry
     * @throws IOException
     */
    public String addNewDevice(String deviceID, String agentID) throws IOException, ParseException {
        JSONObject mapObj = new JSONObject();
        mapObj.put("device_id", deviceID);
        mapObj.put("agent_id", agentID);

        JSONArray mappings = new JSONArray();
        mappings.add(mapObj);

        JSONObject reqObj = new JSONObject();
        reqObj.put("mappings", mappings);


        RequestBody body = RequestBody.create(JSON, reqObj.toJSONString());

        Request request = new Request.Builder()
                .url(mappingsURL)
                .put(body)
                .build();

        JSONParser parser = new JSONParser();
        try (Response response = client.newCall(request).execute()) {

            JSONObject jsonObject = (JSONObject) parser.parse(response.body().string());

            JSONArray recMappings = (JSONArray) jsonObject.get("mappings");
            for (Object recMaps : recMappings) {
                JSONObject recMap = (JSONObject) recMaps;
                if (recMap.get("agent_id").toString().equals(agentID))
                    return recMap.get("url").toString();
            }
            return null;
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
                .url(currentUrl)
                .get()
                .build();
        JSONParser parser = new JSONParser();
        try (Response response = client.newCall(request).execute()) {
            return (JSONObject) parser.parse(response.body().string());
        }
    }

    public static void setLoadbalancerUrl(String loadbalancerUrl) {
        HttpClient.loadbalancerUrl = loadbalancerUrl;
    }
}
