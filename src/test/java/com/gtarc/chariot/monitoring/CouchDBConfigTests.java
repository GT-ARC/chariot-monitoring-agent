package com.gtarc.chariot.monitoring;

import org.ektorp.CouchDbInstance;
import org.ektorp.DbAccessException;
import org.ektorp.DbInfo;
import org.ektorp.http.HttpClient;
import org.ektorp.http.StdHttpClient;
import org.ektorp.impl.StdCouchDbConnector;
import org.ektorp.impl.StdCouchDbInstance;
import org.junit.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Properties;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CouchDBConfigTests {

    private Properties prop;

    @Before
    public void getCouchDBConfigFile(){
        this.prop = new Properties();
        FileInputStream inputStream = null;

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("somefile").getFile());
        System.out.println(file.getAbsolutePath());

        try {
            if(new File("src/main/resources/couchdb.properties").exists()) {
                inputStream = new FileInputStream("src/main/resources/couchdb.properties");
            } else if(new File("couchdb.properties").exists()) {
                inputStream = new FileInputStream("couchdb.properties");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        assertTrue("InputStream of properties file is null", inputStream != null);

        try {
            this.prop.load(inputStream);
        } catch (IOException e) { 
            e.printStackTrace();
            fail("Couldn't load the properties file.");
        }
    }

    @Test
    public void testCouchDBConnection(){
        String url = prop.getProperty("couchdb.protocol")
                + "://"
                + prop.getProperty("couchdb.host")
                + ":"
                + prop.getProperty("couchdb.port");

        HttpClient httpClient = null;

        try {

            httpClient = new StdHttpClient.Builder()
                    .url(url)
                    .username(prop.getProperty("couchdb.username"))
                    .password(prop.getProperty("couchdb.password"))
                    .build();

        } catch (MalformedURLException e) {
            e.printStackTrace();
            fail("Could't create the http client.");
        }

        CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

        String couchdbname = prop.getProperty("couchdb.name");
        try {
            DbInfo info = new StdCouchDbConnector(couchdbname, dbInstance).getDbInfo();
            System.out.println("CouchDBConfigTests.testCouchDBConnection: Connection OK");
            System.out.printf("DB name [%s], doc count [%s]", info.getDbName(), info.getDocCount());
        } catch (DbAccessException dbAccExc){
            dbAccExc.printStackTrace();
            fail("Couldn't connect to db");
        }
    }
}
