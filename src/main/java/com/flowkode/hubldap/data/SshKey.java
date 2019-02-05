package com.flowkode.hubldap.data;

public class SshKey {

    private final String fingerPrint;

    private final String data;

    private final String openSshKey;

    private final String comment;

    public SshKey(String fingerPrint, String data, String openSshKey, String comment) {
        this.fingerPrint = fingerPrint;
        this.data = data;
        this.openSshKey = openSshKey;
        this.comment = comment;
    }

    public String getFingerPrint() {
        return fingerPrint;
    }

    public String getData() {
        return data;
    }

    public String getOpenSshKey() {
        return openSshKey;
    }

    public String getComment() {
        return comment;
    }
}
