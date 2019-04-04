package com.flowkode.hubldap;


import org.apache.directory.api.ldap.model.cursor.Cursor;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.filter.AndNode;
import org.apache.directory.api.ldap.model.filter.EqualityNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.message.AliasDerefMode;
import org.apache.directory.api.ldap.model.message.SearchScope;
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
import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class HubLdap {

    private static final Logger LOGGER = LoggerFactory.getLogger(HubLdap.class);

    private final InstanceLayout instanceLayout;

    private final String serviceId;

    private final String serviceSecret;

    private final HubClient hubClient;

    private final String adminPassword;

    private final String rootDomain;

    private final Directory directory = new DirectoryImpl();

    private String dcDn;

    private HubDataSynchronizer dataSynchronizer;

    private HubAutenticator hubAutenticator;

    private int serverPort = 10389;

    private int sslServerPort = 10636;

    private String keystoreFile;

    private String certificatePassword;

    private SchemaManager schemaManager;

    private LdifPartition schemaLdifPartition;

    private CacheService cacheService = new CacheService();

    private DnFactory dnFactory;

    private LdapServer ldapServer;

    private DirectoryService directoryService;

    public HubLdap(
            String rootDomain,
            String adminPassword,
            Path workDir,
            HubClient hubClient,
            String serviceId,
            String serviceSecret,
            String keystoreFile,
            String certificatePassword
    ) throws Exception {
        this.adminPassword = adminPassword;
        this.rootDomain = rootDomain;
        this.serviceId = serviceId;
        this.serviceSecret = serviceSecret;
        this.hubClient = hubClient;
        this.keystoreFile = keystoreFile;
        this.certificatePassword = certificatePassword;

        final File normalizedWorkDir = workDir.toAbsolutePath().normalize().toFile();
        FileUtils.deleteDirectory(normalizedWorkDir);
        instanceLayout = new InstanceLayout(normalizedWorkDir);

        final Path keyFilePath = Paths.get(this.keystoreFile);
        if (!keyFilePath.toFile().exists()) {
            CertificateUtils.generateKeyStore(keyFilePath, certificatePassword, "cn=HubLdap, o=HubLdap, c=US");
        }

        configure();
    }

    public void addStaticData(String dnStr, String... attrs) {
        try {
            //search
            final Dn dn = dnFactory.create(dnStr);
            try (final Cursor<Entry> search = directoryService.getAdminSession().search(dn, "(objectClass=*)")) {
                //if it has next exists so we need to delete so we can add again
                if (search.next()) {
                    directoryService.getAdminSession().delete(dn);
                }
            }
            catch (IOException | LdapNoSuchObjectException ignored) {
            }
            directoryService.getAdminSession().add(new DefaultEntry(schemaManager, dn, attrs));
        }
        catch (CursorException | LdapException e) {
            throw new RuntimeException(e);
        }
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

        copyLdif(schemaPath.resolve("ou=schema/cn=other/ou=attributetypes"), "memberOf.ldif", "m-oid=1.2.840.113556.1.4.222.ldif");
        copyLdif(schemaPath.resolve("ou=schema/cn=other/ou=objectclasses"), "msPrincipal.ldif", "m-oid=1.2.840.113556.1.5.6.ldif");

        copyLdif(schemaPath.resolve("ou=schema/cn=other/ou=attributetypes"), "sshPublicKey.ldif", "m-oid=1.3.6.1.4.1.24552.500.1.1.1.13.ldif");
        copyLdif(schemaPath.resolve("ou=schema/cn=other/ou=objectclasses"), "ldapPublicKey.ldif", "m-oid=1.3.6.1.4.1.24552.500.1.1.2.0.ldif");


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
        hubAutenticator = new HubAutenticator(dnFactory.create(dcDn), directory, hubClient, serviceId, serviceSecret);


        schemaLdifPartition = new LdifPartition(schemaManager, dnFactory);
        schemaLdifPartition.setPartitionPath(schemaPath.toUri());


        initDirectoryService();
        //change admin password
        directoryService.getAdminSession().modify(
                new Dn("uid=admin,ou=system"),
                new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "userPassword", adminPassword)
        );
        // TODO: 2/5/19 future reference (ngraca)
        //enable posix account stuff
//        directoryService.getAdminSession().modify(
//                new Dn("cn=nis,ou=schema"),
//                new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "m-disabled", "FALSE")
//        );

        addHubPartition();
        buildLdapServer();
    }

    private void copyLdif(Path destinationDir, String sourceName, String destinationName) throws IOException {
        Files.createDirectories(destinationDir);
        try (
                InputStream ldif = getClass().getClassLoader().getResourceAsStream(sourceName);
                FileOutputStream target = new FileOutputStream(destinationDir.resolve(destinationName).toFile())
        ) {
            IOUtils.copy(ldif, target);
        }
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

        final TcpTransport ldapTransport = new TcpTransport(serverPort);

        final TcpTransport ldapsTransport = new TcpTransport(sslServerPort);
        ldapsTransport.enableSSL(true);
        ldapsTransport.setEnabledCiphers(Cipher.getAllCiphers());

        //add certificate
        ldapServer.setKeystoreFile(keystoreFile);
        ldapServer.setCertificatePassword(certificatePassword);

        ldapServer.setTransports(ldapTransport, ldapsTransport);
    }

    public void start() throws Exception {
        ldapServer.start();
        dataSynchronizer.startSync();
    }

    private class DirectoryImpl implements Directory {

        @Override
        public void delete(Dn dn) {
            try {
                directoryService.getAdminSession().delete(dn);
            }
            catch (LdapException e) {
                LOGGER.error("Failed to delete: " + dn, e);
            }
        }

        private Cursor<Entry> search(Dn baseDn, ExprNode node) {
            try {
                return directoryService.getAdminSession().search(baseDn, SearchScope.SUBTREE, node, AliasDerefMode.DEREF_ALWAYS);
            }
            catch (LdapException e) {
                LOGGER.error("Error searching", e);
                return null;
            }
        }

        @Override
        public Cursor<Entry> search(ExprNode node) {
            try {
                return directoryService.getAdminSession().search(dnFactory.create(dcDn), SearchScope.SUBTREE, node, AliasDerefMode.DEREF_ALWAYS);
            }
            catch (LdapException e) {
                LOGGER.error("Error searching", e);
                return null;
            }
        }

        private Dn findGroup(String groupId) {
            try (final Cursor<Entry> search = search(dnFactory.create(dcDn), new AndNode(
                    new EqualityNode<String>("objectClass", "groupOfNames"),
                    new EqualityNode<String>("description", groupId)
            ))) {
                if (search != null && search.next()) {
                    return search.get().getDn();
                }
            }
            catch (IOException | LdapException | CursorException e) {
                LOGGER.error("Could not find group with id: " + groupId, e);
            }
            return null;
        }

        @Override
        public Cursor<Entry> findAllUsers() {
            return search(new EqualityNode<String>("objectClass", "person"));
        }

        @Override
        public Cursor<Entry> findAllGroups() {
            return search(new EqualityNode<String>("objectClass", "groupOfNames"));
        }

        @Override
        public void addGroup(String name, String id) {
            final Set<String> attributes = new HashSet<>();
            attributes.add("objectClass:top");
            attributes.add("objectClass:groupOfNames");
            attributes.add("member: ");
            attributes.add("cn:" + name);
            attributes.add("description:" + id);
            addStaticData("cn=" + name + ",ou=Groups," + dcDn, attributes.toArray(new String[0]));
        }

        @Override
        public void addUser(String name, String id, String mail, String login, Set<String> groups, Set<String> publicKeys) {
            final String userDn = "cn=" + name + ",ou=Users," + dcDn;

            final Set<String> attributes = groups.stream()
                                                 .map(this::findGroup)
                                                 .filter(Objects::nonNull)
                                                 .map(g -> "memberOf:" + g)
                                                 .collect(Collectors.toSet());

            attributes.addAll(publicKeys.stream()
                                        .map(k -> "sshPublicKey:" + k)
                                        .collect(Collectors.toSet()));

            attributes.add("objectClass:top");
            attributes.add("objectClass:inetOrgPerson");
            attributes.add("objectClass:organizationalPerson");
            attributes.add("objectClass:person");
            attributes.add("objectClass:microsoftPrincipal");
            attributes.add("objectClass:ldapPublicKey");
            attributes.add("description:" + id);
            attributes.add("cn:" + name);
            attributes.add("sn: .");
            attributes.add("mail:" + mail);
            attributes.add("uid:" + login);
            addStaticData(userDn, attributes.toArray(new String[0]));

            //now add him as member on the groups
            for (String group : groups) {
                final Dn groupDn = findGroup(group);
                if (groupDn != null) {
                    try {
                        final DefaultModification defaultModification = new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "member", userDn);
                        directoryService.getAdminSession().modify(groupDn, defaultModification);
                    }
                    catch (LdapException e) {
                        LOGGER.warn("Could not add user {} to group {}", userDn, groupDn);
                        LOGGER.warn(e.getMessage(), e);
                    }
                }
            }
        }

        @Override
        public String getUsername(Dn dn) {
            try (final Cursor<Entry> search = search(dn, new EqualityNode<String>("objectClass", "person"))) {
                if (search != null && search.next()) {
                    return search.get().get("uid").getString();
                }
            }
            catch (IOException | LdapException | CursorException e) {
                LOGGER.error("Could not find user with cn: " + dn.toString(), e);
            }
            return null;
        }
    }
}
