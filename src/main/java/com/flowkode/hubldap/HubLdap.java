package com.flowkode.hubldap;


import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.FileUtils;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.core.shared.DefaultDnFactory;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HubLdap {

    private static final Logger LOGGER = LoggerFactory.getLogger(HubLdap.class);

    private final InstanceLayout instanceLayout;

    private int serverPort = 10389;

    private SchemaManager schemaManager;

    private LdifPartition schemaLdifPartition;

    private CacheService cacheService = new CacheService();

    private DnFactory dnFactory;

    private LdapServer ldapServer;

    private DirectoryService directoryService;

    private String adminPassword = "test";


    public HubLdap(Path workDir) throws Exception {
        final File normalizedWorkDir = workDir.toAbsolutePath().normalize().toFile();
        FileUtils.deleteDirectory(normalizedWorkDir);
        instanceLayout = new InstanceLayout(normalizedWorkDir);

        configure();
    }

    private void initDirectoryService() throws Exception {
        directoryService = new DefaultDirectoryService();

        directoryService.setSystemPartition(createSystemPartition());
        directoryService.setSchemaManager(schemaManager);
        directoryService.setDnFactory(dnFactory);

        // The schema partition
        SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
        schemaPartition.setWrappedPartition(schemaLdifPartition);
        directoryService.setSchemaPartition(schemaPartition);

        // Store the default directories
        directoryService.setInstanceLayout(instanceLayout);

        directoryService.setCacheService(cacheService);
//        directoryService.setAllowAnonymousAccess(true);

        directoryService.startup();
    }

    private JdbmPartition createSystemPartition() throws LdapException {
        final JdbmPartition systemPartition = new JdbmPartition(schemaManager, dnFactory);
        systemPartition.setId("system");
        systemPartition.setSuffixDn(new Dn(new Rdn(ServerDNConstants.SYSTEM_DN)));
        systemPartition.setPartitionPath(instanceLayout.getPartitionsDirectory().toPath().resolve("system").toUri());
        return systemPartition;
    }

    private void configure() throws Exception {
        Files.createDirectories(instanceLayout.getPartitionsDirectory().toPath().normalize());

        //init cache
        cacheService.initialize(instanceLayout);

        //init and load schema
        final Path schemaPath = instanceLayout.getPartitionsDirectory().toPath().resolve("schema");

        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor(instanceLayout.getPartitionsDirectory());
        extractor.extractOrCopy();

        SchemaLoader loader = new LdifSchemaLoader(schemaPath.toFile());
        schemaManager = new DefaultSchemaManager(loader.getAllSchemas());
        schemaManager.loadAllEnabled();

        List<Throwable> errors = schemaManager.getErrors();

        if (!errors.isEmpty()) {
            throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)));
        }

        dnFactory = new DefaultDnFactory(schemaManager, cacheService.getCache("dnCache"));

        schemaLdifPartition = new LdifPartition(schemaManager, dnFactory);
        schemaLdifPartition.setPartitionPath(schemaPath.toUri());


        initDirectoryService();
        directoryService.getAdminSession().modify(
                new Dn("uid=admin,ou=system"),
                new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "userPassword", adminPassword)
        );
        addHubPartition();
        buildLdapServer();


    }

    private void addHubPartition() throws LdapException {
        JdbmPartition partition = new JdbmPartition(schemaManager, dnFactory);
        partition.setId("hub");
        Dn suffixDn = new Dn(schemaManager, "dc=hub");
        partition.setSuffixDn(suffixDn);
        partition.setPartitionPath(instanceLayout.getPartitionsDirectory().toPath().resolve("hub").toUri());
        directoryService.addPartition(partition);

        addStaticData("dc=hub",
                      "objectclass:top",
                      "objectclass:domain",
                      "dc:hub"
        );
        addStaticData("ou=Users,dc=hub",
                      "objectclass:top",
                      "objectclass:organizationalUnit",
                      "ou:Users"
        );
        addStaticData("ou=Groups,dc=hub",
                      "objectclass:top",
                      "objectclass:organizationalUnit",
                      "ou:Groups"
        );

//        logger.debug("" + service.getInterceptor("org.apache.directory.server.core.authn.AuthenticationInterceptor"));
//        AuthenticationInterceptor ai = (AuthenticationInterceptor) service.getInterceptor("org.apache.directory.server.core.authn.AuthenticationInterceptor");
//        Set<Authenticator> auths = new HashSet<>();
//        auths.add(new CrowdAuthenticator(m_CrowdClient));
//        ai.setAuthenticators(auths);
//
//        addCrowdPartition("crowd", "dc=crowd");

    }

    private void addStaticData(String dnStr, String... attrs) throws LdapException {
        directoryService.getAdminSession().add(new DefaultEntry(schemaManager, new Dn(dnStr), attrs));
    }

    private void buildLdapServer() {
        ldapServer = new LdapServer();
        ldapServer.setDirectoryService(directoryService);
        ldapServer.setEnabled(true);
        ldapServer.setTransports(new TcpTransport(serverPort));
    }

    public void start() throws Exception {
        ldapServer.start();
    }
}
