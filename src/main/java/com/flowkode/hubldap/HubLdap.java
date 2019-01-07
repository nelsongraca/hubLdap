package com.flowkode.hubldap;


import com.flowkode.hubldap.data.*;
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
import org.apache.directory.api.util.IOUtils;
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
import retrofit2.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class HubLdap {

    private static final Logger LOGGER = LoggerFactory.getLogger(HubLdap.class);

    private static final int CHUNK_SIZE = 1;

    private final InstanceLayout instanceLayout;

    private final HubClient hubClient;

    private final String serviceId;

    private final String serviceSecret;

    private int serverPort = 10389;

    private SchemaManager schemaManager;

    private LdifPartition schemaLdifPartition;

    private CacheService cacheService = new CacheService();

    private DnFactory dnFactory;

    private LdapServer ldapServer;

    private DirectoryService directoryService;

    private String adminPassword = "test";

    private HashMap<String, String> groups = new HashMap<>();


    public HubLdap(Path workDir, HubClient hubClient, String serviceId, String serviceSecret) throws Exception {
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

        Path attributeTypesDir = schemaPath.resolve("ou=schema/cn=other/ou=attributetypes");
        Files.createDirectories(attributeTypesDir);
        try (
                InputStream ldif = getClass().getClassLoader().getResourceAsStream("memberof.ldif");
                FileOutputStream target = new FileOutputStream(attributeTypesDir.resolve("m-oid=1.2.840.113556.1.4.222.ldif").toFile())
        ) {
            IOUtils.copy(ldif, target);
        }

        Path objectClassesDir = schemaPath.resolve("ou=schema/cn=other/ou=objectClasses");
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
                      "objectClass:top",
                      "objectClass:domain",
                      "dc:hub"
        );
        addStaticData("ou=Users,dc=hub",
                      "objectClass:top",
                      "objectClass:organizationalUnit",
                      "ou:Users"
        );
        addStaticData("ou=Groups,dc=hub",
                      "objectClass:top",
                      "objectClass:organizationalUnit",
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
        loadData();
    }

    private void loadData() {
        try {
            String credentials = Base64.getEncoder().encodeToString((serviceId + ":" + serviceSecret).getBytes());
            final Response<AuthResponse> response = hubClient.serviceLogin("Basic " + credentials).execute();
            String token = response.body().getAccessToken();
            loadUserGroups(token);
            loadUsers(token);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (LdapException e) {
            e.printStackTrace();
        }
    }

    private void loadUsers(String authToken) throws IOException, LdapException {
        int total = Integer.MAX_VALUE;

        int start = 0;
        int limit = 1;

        while (total > start) {
            final Response<UsersResponse> response = hubClient.getUsers("Bearer " + authToken, start, limit).execute();
            final UsersResponse body = response.body();
            start += CHUNK_SIZE;
            total = body.getTotal();

            for (User user : body.getUsers()) {
                addUser(user);
            }
        }
    }

    private void addUser(User user) throws LdapException {
        final Set<String> attributes = Arrays.stream(user.getGroups())
                                             .map(g -> this.groups.get(g.getId()))
                                             .filter(Objects::nonNull)
                                             .map(g -> "memberOf:" + g)
                                             .collect(Collectors.toSet());

        attributes.add("objectClass:top");
        attributes.add("objectClass:inetOrgPerson");
        attributes.add("objectClass:organizationalPerson");
        attributes.add("objectClass:person");
        attributes.add("objectClass:microsoftPrincipal");
        attributes.add("cn:" + user.getName());
        attributes.add("sn: ");
        attributes.add("uid:" + user.getLogin());
        addStaticData("cn=" + user.getName() + ",ou=Users,dc=hub", attributes.toArray(new String[0]));
    }

    private void loadUserGroups(String authToken) throws IOException, LdapException {
        int total = Integer.MAX_VALUE;

        int start = 0;
        int limit = 1;

        while (total > start) {
            final Response<UserGroupsResponse> response = hubClient.getUserGroups("Bearer " + authToken, start, limit).execute();
            final UserGroupsResponse body = response.body();
            start += CHUNK_SIZE;
            total = body.getTotal();

            for (UserGroup userGroup : body.getUserGroups()) {
                final String groupCn = "cn=" + userGroup.getName() + ",ou=Groups,dc=hub";
                this.groups.put(userGroup.getId(), groupCn);
                addStaticData(groupCn,
                              "objectClass:top",
                              "objectClass:groupOfNames",
                              "member:dc=hub",
                              "cn:" + userGroup.getName(),
                              "description:" + userGroup.getId()
                );
            }
        }
    }

}
