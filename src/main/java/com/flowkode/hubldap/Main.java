package com.flowkode.hubldap;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {

    public static void main(String[] args) throws Exception {


        Properties config = new Properties();
        Path jarDir = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        if (!Files.isDirectory(jarDir)) {
            jarDir = jarDir.getParent();
        }
        config.load(new FileReader(jarDir.toAbsolutePath().normalize().resolve("hubLdap.properties").toFile()));

        final Path workDir = jarDir.resolve("work");
        final String hubUrl = config.getProperty("hubUrl");
        final String rootDomain = config.getProperty("rootDomain", "hub.local");
        final String adminPassword = config.getProperty("adminPassword", "admin");
        final String serviceId = config.getProperty("serviceId", "");
        final String serviceSecret = config.getProperty("serviceSecret", "");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(hubUrl + "/api/rest/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        HubClient hubClient = retrofit.create(HubClient.class);

        new HubLdap(rootDomain, adminPassword, workDir, hubClient, serviceId, serviceSecret).start();
    }
}
