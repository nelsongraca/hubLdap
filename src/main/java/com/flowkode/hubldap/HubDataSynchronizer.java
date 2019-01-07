package com.flowkode.hubldap;

import com.flowkode.hubldap.data.*;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HubDataSynchronizer {


    private static final int CHUNK_SIZE = 10;

    private final HubClient hubClient;

    private final HashMap<String, String> groups = new HashMap<>();

    private final Directory directory;

    private final String credentials;

    public HubDataSynchronizer(Directory directory, HubClient hubClient, String serviceId, String serviceSecret) {
        this.directory = directory;
        this.hubClient = hubClient;

        credentials = "Basic " + Base64.getEncoder().encodeToString((serviceId + ":" + serviceSecret).getBytes());
    }

    public void sync() {
        try {

            final Response<AuthResponse> response = hubClient.serviceLogin(credentials).execute();
            String token = response.body().getAccessToken();
            loadUserGroups(token);
            loadUsers(token);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadUsers(String authToken) throws IOException {
        int total = Integer.MAX_VALUE;

        int start = 0;

        while (total > start) {
            final Response<UsersResponse> response = hubClient.getUsers("Bearer " + authToken, start, CHUNK_SIZE).execute();
            final UsersResponse body = response.body();
            start += CHUNK_SIZE;
            total = body.getTotal();

            for (User user : body.getUsers()) {
                addUser(user);
            }
        }
    }

    private void addUser(User user) {
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
        attributes.add("mail:" + Optional.of(user.getProfile()).map(Profile::getEmail).map(Email::getEmail).orElse(""));
        attributes.add("uid:" + user.getLogin());
        directory.addStaticData("cn=" + user.getName() + ",ou=Users," + directory.getDcDn(), attributes.toArray(new String[0]));

        System.out.println("User:" + user.getName());
    }

    private void loadUserGroups(String authToken) throws IOException {
        int total = Integer.MAX_VALUE;

        int start = 0;

        while (total > start) {
            final Response<UserGroupsResponse> response = hubClient.getUserGroups("Bearer " + authToken, start, CHUNK_SIZE).execute();
            final UserGroupsResponse body = response.body();
            start += CHUNK_SIZE;
            total = body.getTotal();

            for (UserGroup userGroup : body.getUserGroups()) {
                final String groupCn = "cn=" + userGroup.getName() + ",ou=Groups," + directory.getDcDn();
                this.groups.put(userGroup.getId(), groupCn);
                directory.addStaticData(groupCn,
                                        "objectClass:top",
                                        "objectClass:groupOfNames",
                                        "member: ",
                                        "cn:" + userGroup.getName(),
                                        "description:" + userGroup.getId()
                );
                System.out.println("Group: " + userGroup.getName());
            }
        }
    }


}
