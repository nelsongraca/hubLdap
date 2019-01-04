package com.flowkode.hubldap;

import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws Exception {
        new HubLdap(Paths.get("./target/work")).start();
    }
}
