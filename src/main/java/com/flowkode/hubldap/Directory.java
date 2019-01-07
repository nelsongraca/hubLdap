package com.flowkode.hubldap;

public interface Directory {

    void addStaticData(String dnStr, String... attrs);

    String getDcDn();
}
