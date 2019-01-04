package com.flowkode.hubldap;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.partition.ldif.LdifPartition;

import java.util.HashMap;
import java.util.Map;

public class HubPartition extends LdifPartition {

    private final DirectoryService directoryService;

    private Map<String, Entry> staticData = new HashMap<>();

    public HubPartition(SchemaManager schemaManager, DnFactory dnFactory, DirectoryService directoryService) {
        super(schemaManager, dnFactory);
        this.directoryService=directoryService;
    }


}
