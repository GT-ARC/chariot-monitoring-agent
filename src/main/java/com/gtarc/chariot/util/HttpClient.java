package com.gtarc.chariot.util;

import okhttp3.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class HttpClient {

    private static String currentUrl = "";
    private static String loadbalancerUrl = "";
    private static String mappingsURL = "";
    private static final String startUrl = "http://chariot-km.dai-lab.de:8080/v1/monitoringservice/";
    private static String addUrl = "";
    private static final String postfix = "?format=json";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.parse("text; charset=utf-8");

    public void establishConnection() throws Exception {
        Request request = new Request.Builder()
                .url(startUrl + postfix)
                .get()
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();;

        try (Response response = client.newCall(request).execute()) {
            JSONParser parser = new JSONParser();
            Object receivedO = parser.parse(response.body().string());
            if (!(receivedO instanceof JSONArray))
                throw new Exception("KMS didn't answered json array");

            JSONArray receivedArray = (JSONArray) receivedO;
            if (response.code() == 404 || receivedArray.size() == 0) {
                pushInitialData();
                establishConnection();
            } else {
                JSONObject monService = ((JSONObject) receivedArray.get(0));

                loadbalancerUrl = (String) ((JSONObject) monService.get("loadbalancer")).get("url");
                currentUrl = (String) monService.get("url");

                JSONObject mapping = ((JSONObject) monService.get("agentlist"));
                mappingsURL = (String) mapping.get("url");
                int secondLastSlash = mappingsURL.substring(0, mappingsURL.length() - 1).lastIndexOf("/");
                addUrl = mappingsURL.substring(0, secondLastSlash+1);

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
                .url(startUrl + postfix)
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();;

        client.newCall(request).execute().close();
    }

    /**
     * Sets the current time in mills in the data base
     */
    public void updateLoadBalancer() throws Exception {
        JSONObject timeStamp = new JSONObject();
        timeStamp.put("timestamp", new Date().getTime());

        RequestBody body = RequestBody.create(JSON, timeStamp.toJSONString());

        Request request = new Request.Builder()
                .url(loadbalancerUrl)
                .put(body)
                .build();


        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200)
                throw new Exception("Loadbalancer Update Failed");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a new entity into the entity list in the kms
     *
     * @param entityID the entity id of the entity agent
     * @param agentID  the agent id
     * @return The url of the new entry
     * @throws IOException
     */
    public String addNewEntity(String entityID, String agentID) throws IOException, ParseException {
        JSONObject mapObj = new JSONObject();
        mapObj.put("device_id", entityID);
        mapObj.put("agent_id", agentID);


        RequestBody body = RequestBody.create(JSON, mapObj.toJSONString());

        Request request = new Request.Builder()
                .url(addUrl)
                .put(body)
                .build();

        JSONParser parser = new JSONParser();
        String retUrl = null;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(request).execute()) {

            JSONObject jsonObject = (JSONObject) parser.parse(response.body().string());

            JSONArray recMappings = (JSONArray) ((JSONObject) jsonObject.get("agentlist")).get("mappings");
            for (Object recMaps : recMappings) {
                JSONObject recMap = (JSONObject) recMaps;
                if (recMap.get("agent_id").toString().equals(agentID)) {
                    retUrl = recMap.get("url").toString();
                    break;
                }
            }
        }
        return retUrl;
    }

    public void removeAllEntities() throws Exception {
        Request request = new Request.Builder()
                .url(mappingsURL)
                .get()
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();

        JSONParser parser = new JSONParser();
        try (Response response = client.newCall(request).execute()) {

            if (response.code() != 200)
                throw new Exception("Couldn't do kms request for entities: " + response.body().string());

            JSONObject mapping = (JSONObject) parser.parse(response.body().string());
            JSONArray jsonArray = (JSONArray) mapping.get("mappings");
            jsonArray.forEach(o -> {
                try {
                    removeEntity((String) ((JSONObject) o).get("url"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public String removeEntity(String deviceUrl) throws IOException {
        Request request = new Request.Builder()
                .url(deviceUrl).delete().build();
        String responseString;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(request).execute()) {
            responseString = response.body().string();
        }
        return responseString;
    }

    public JSONObject getAllMappings() throws IOException, ParseException {
        Request request = new Request.Builder()
                .url(currentUrl)
                .get()
                .build();
        JSONParser parser = new JSONParser();
        JSONObject receivedObj;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(request).execute()) {
             receivedObj = (JSONObject) parser.parse(response.body().string());
        }
        return receivedObj;
    }

    public static void setLoadbalancerUrl(String loadbalancerUrl) {
        HttpClient.loadbalancerUrl = loadbalancerUrl;
    }
}
