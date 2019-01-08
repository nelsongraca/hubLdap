package com.flowkode.hubldap;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;

import java.net.URI;

public class HubPartition extends JdbmPartition {


    private final DirectoryService directoryService;

    public HubPartition(SchemaManager schemaManager, DnFactory dnFactory, DirectoryService directoryService, URI partitionPath) {
        super(schemaManager, dnFactory);
        this.directoryService = directoryService;

        setId("hub");
        setPartitionPath(partitionPath);
    }

    private void addStaticData(String dnStr, String... attrs) {
        try {
            directoryService.getAdminSession().add(new DefaultEntry(schemaManager, dnFactory.create(dnStr), attrs));
        }
        catch (LdapException e) {
            throw new RuntimeException(e);
        }
    }

    public void populate() {
        final String rootDn = getSuffixDn().getName();
        addStaticData(rootDn,
                      "objectClass:top",
                      "objectClass:domain",
                      "dc:" + rootDn.substring(3, rootDn.indexOf(",dc="))
        );

        addStaticData("ou=Users," + rootDn,
                      "objectClass:top",
                      "objectClass:organizationalUnit",
                      "ou:Users"
        );
        addStaticData("ou=Groups," + rootDn,
                      "objectClass:top",
                      "objectClass:organizationalUnit",
                      "ou:Groups"
        );

    }
}
