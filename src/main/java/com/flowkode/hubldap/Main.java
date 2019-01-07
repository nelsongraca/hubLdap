package com.flowkode.hubldap;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost:8080/hub/api/rest/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        HubClient hubClient = retrofit.create(HubClient.class);
        new HubLdap(Paths.get("./target/work"), hubClient, "1be62e64-e5b6-457b-b0cd-9fb5b16cade4", "test").start();
    }
}
