package com.flowkode.hubldap;


import org.apache.directory.api.ldap.model.cursor.Cursor;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.FileUtils;
import org.apache.directory.api.util.IOUtils;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.*;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authn.Authenticator;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.core.shared.DefaultDnFactory;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HubLdap {

    private static final Logger LOGGER = LoggerFactory.getLogger(HubLdap.class);

    private final InstanceLayout instanceLayout;

    private final String serviceId;

    private final String serviceSecret;

    private final HubClient hubClient;

    private final String adminPassword;

    private final String rootDomain;

    private String dcDn;

    private HubDataSynchronizer dataSynchronizer;

    private HubAutenticator hubAutenticator;

    private int serverPort = 10389;

    private SchemaManager schemaManager;

    private LdifPartition schemaLdifPartition;

    private CacheService cacheService = new CacheService();

    private DnFactory dnFactory;

    private LdapServer ldapServer;

    private DirectoryService directoryService;

    private final Directory directory = new Directory() {
        @Override
        public void addStaticData(String dnStr, String... attrs) {
            try {
                //search
                final Dn dn = dnFactory.create(dnStr);
                try {
                    final Cursor<Entry> search = directoryService.getAdminSession().search(dn, "(objectClass=*)");
                    //if it has next exists so we need to delete so we can add again
                    if (search.next()) {
                        directoryService.getAdminSession().delete(dn);
                    }
                }
                catch (LdapNoSuchObjectException ignored) {
                }
                directoryService.getAdminSession().add(new DefaultEntry(schemaManager, dn, attrs));
            }
            catch (CursorException | LdapException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Dn getRootDn() {
            try {
                return dnFactory.create(dcDn);
            }
            catch (LdapInvalidDnException e) {
                LOGGER.error(e.getMessage(), e);
            }
            return null;
        }

        @Override
        public CoreSession getAdminSession() {
            return directoryService.getAdminSession();
        }
    };

    public HubLdap(String rootDomain, String adminPassword, Path workDir, HubClient hubClient, String serviceId, String serviceSecret) throws Exception {
        this.adminPassword = adminPassword;
        this.rootDomain = rootDomain;
        this.serviceId = serviceId;
        this.serviceSecret = serviceSecret;
        this.hubClient = hubClient;

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
        directoryService.setAllowAnonymousAccess(false);

        directoryService.startup();
    }

    private JdbmPartition createSystemPartition() throws LdapException {
        final JdbmPartition systemPartition = new JdbmPartition(schemaManager, dnFactory);
        systemPartition.setId("system");
        systemPartition.setSuffixDn(dnFactory.create(ServerDNConstants.SYSTEM_DN));
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

        Path attributeTypesDir = schemaPath.resolve("ou=schema/cn=other/ou=attributetypes");
        Files.createDirectories(attributeTypesDir);
        try (
                InputStream ldif = getClass().getClassLoader().getResourceAsStream("memberof.ldif");
                FileOutputStream target = new FileOutputStream(attributeTypesDir.resolve("m-oid=1.2.840.113556.1.4.222.ldif").toFile())
        ) {
            IOUtils.copy(ldif, target);
        }

        Path objectClassesDir = schemaPath.resolve("ou=schema/cn=other/ou=objectclasses");
        Files.createDirectories(objectClassesDir);
        try (
                InputStream ldif = getClass().getClassLoader().getResourceAsStream("msprincipal.ldif");
                FileOutputStream target = new FileOutputStream(objectClassesDir.resolve("m-oid=1.2.840.113556.1.5.6.ldif").toFile())
        ) {
            IOUtils.copy(ldif, target);
        }

        SchemaLoader loader = new LdifSchemaLoader(schemaPath.toFile());
        schemaManager = new DefaultSchemaManager(loader.getAllSchemas());
        schemaManager.loadAllEnabled();

        List<Throwable> errors = schemaManager.getErrors();

        if (!errors.isEmpty()) {
            throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)));
        }

        dnFactory = new DefaultDnFactory(schemaManager, cacheService.getCache("dnCache"));

        dcDn = "dc=" + Arrays.stream(rootDomain.split("\\.")).collect(Collectors.joining(",dc="));

        dataSynchronizer = new HubDataSynchronizer(directory, hubClient, serviceId, serviceSecret);
        hubAutenticator = new HubAutenticator(dnFactory.create(dcDn), hubClient, serviceId, serviceSecret);


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
        HubPartition hubPartition = new HubPartition(
                schemaManager,
                dnFactory,
                directoryService,
                instanceLayout.getPartitionsDirectory().toPath().resolve("hub").toUri()
        );
        Dn suffixDn = dnFactory.create(dcDn);
        hubPartition.setSuffixDn(suffixDn);

        directoryService.addPartition(hubPartition);
        hubPartition.populate();

        final AuthenticationInterceptor authenticationInterceptor = (AuthenticationInterceptor) directoryService.getInterceptor("authenticationInterceptor");
        final Set<Authenticator> authenticators = authenticationInterceptor.getAuthenticators();
        authenticators.add(hubAutenticator);
        authenticationInterceptor.setAuthenticators(authenticators.toArray(new Authenticator[0])); //must use this one, read the sources if you want to know why
    }


    private void buildLdapServer() {
        ldapServer = new LdapServer();
        ldapServer.setDirectoryService(directoryService);
        ldapServer.setEnabled(true);
        ldapServer.setTransports(new TcpTransport(serverPort));
    }

    public void start() throws Exception {
        ldapServer.start();
        dataSynchronizer.startSync();
    }
}
