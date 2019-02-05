package com.flowkode.hubldap.data;

import com.google.gson.annotations.SerializedName;

public class SshKeysResponse extends PagedResponse {

    @SerializedName("sshpublickeys")
    private final SshKey[] sshKeys;

    public SshKeysResponse(int skip, int top, int total, SshKey[] sshKeys) {
        super(skip, top, total);
        this.sshKeys = sshKeys;
    }

    public SshKey[] getSshKeys() {
        return sshKeys;
    }
}
