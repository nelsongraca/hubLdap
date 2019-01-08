package com.flowkode.hubldap;

import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.CoreSession;

public interface Directory {

    void addStaticData(String dnStr, String... attrs);

    Dn getRootDn();

    CoreSession getAdminSession();
}
