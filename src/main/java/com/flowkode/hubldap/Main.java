package com.flowkode.hubldap;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {

    public static void main(String[] args) throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost:8080/hub/api/rest/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        HubClient hubClient = retrofit.create(HubClient.class);

        Properties config = new Properties();
        config.load(new FileReader(Paths.get(".").toAbsolutePath().normalize().resolve("hubLdap.properties").toFile()));

        final Path workDir = Paths.get("./work");
        final String rootDomain = config.getProperty("rootDomain", "hub.local");
        final String adminPassword = config.getProperty("adminPassword", "admin");
        final String serviceId = config.getProperty("serviceId", "");
        final String serviceSecret = config.getProperty("serviceSecret", "");

        new HubLdap(rootDomain, adminPassword, workDir, hubClient, serviceId, serviceSecret).start();
    }
}
