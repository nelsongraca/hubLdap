package com.flowkode.hubldap.data;

public class UserGroup {

    private final String id;

    private final String name;

    public UserGroup(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
