package com.flowkode.hubldap.data;

import com.google.gson.annotations.SerializedName;

public class UserGroupsResponse extends PagedResponse {

    @SerializedName("usergroups")
    private final UserGroup[] userGroups;

    public UserGroupsResponse(int skip, int top, int total, UserGroup[] userGroups) {
        super(skip, top, total);
        this.userGroups = userGroups;
    }

    public UserGroup[] getUserGroups() {
        return userGroups;
    }
}
