package com.flowkode.hubldap.data;

import com.google.gson.annotations.SerializedName;

public class UsersResponse extends PagedResponse {

    @SerializedName("users")
    private final User[] users;

    public UsersResponse(int skip, int top, int total, User[] users) {
        super(skip, top, total);
        this.users = users;
    }

    public User[] getUsers() {
        return users;
    }
}
