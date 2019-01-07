package com.flowkode.hubldap.data;

import com.google.gson.annotations.SerializedName;

public class User {

    private final String id;

    private final String name;

    private final String login;

    private final boolean banned;

    @SerializedName("transitiveGroups")
    private final UserGroup[] groups;

    private final Profile profile;


    public User(String id, String name, String login, boolean banned, Profile profile, UserGroup[] groups) {
        this.id = id;
        this.name = name;
        this.login = login;
        this.banned = banned;
        this.profile = profile;
        this.groups = groups;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLogin() {
        return login;
    }

    public boolean isBanned() {
        return banned;
    }

    public Profile getProfile() {
        return profile;
    }

    public UserGroup[] getGroups() {
        return groups;
    }
}
