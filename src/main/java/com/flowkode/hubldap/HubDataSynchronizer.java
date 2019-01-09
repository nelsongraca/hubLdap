package com.flowkode.hubldap;

import com.flowkode.hubldap.data.*;
import org.apache.directory.api.ldap.model.cursor.Cursor;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HubDataSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HubDataSynchronizer.class);

    private static final int CHUNK_SIZE = 10;

    private final HubClient hubClient;

    private final Directory directory;

    private final String serviceCredentials;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public HubDataSynchronizer(Directory directory, HubClient hubClient, String serviceId, String serviceSecret) {
        this.directory = directory;
        this.hubClient = hubClient;

        serviceCredentials = "Basic " + Base64.getEncoder().encodeToString((serviceId + ":" + serviceSecret).getBytes());
    }

    public void startSync() {
        scheduler.scheduleWithFixedDelay(this::sync, 0, 1, TimeUnit.MINUTES);
    }

    private void sync() {
        try {
            final Response<AuthResponse> response = hubClient.serviceLogin(serviceCredentials).execute();
            if (response.isSuccessful()) {
                String token = "Bearer " + response.body().getAccessToken();
                loadUserGroups(token);
                loadUsers(token);
                purgeUsers(token);
                purgeGroups(token);
            }
            else {
                LOGGER.error("Failed to sync got code {} when logging in.", response.code());
            }
        }
        catch (Exception e) {
            LOGGER.error("Could not sync.", e);
        }
    }

    private void loadUsers(String authToken) throws IOException {
        int total = Integer.MAX_VALUE;

        int start = 0;

        while (total > start) {
            final Response<UsersResponse> response = hubClient.getUsers(authToken, start, CHUNK_SIZE).execute();
            final UsersResponse body = response.body();
            start += CHUNK_SIZE;
            total = body.getTotal();

            for (User user : body.getUsers()) {
                directory.addUser(
                        user.getName(),
                        user.getId(),
                        Optional.of(user.getProfile()).map(Profile::getEmail).map(Email::getEmail).orElse(""),
                        user.getLogin(),
                        Arrays.stream(user.getGroups()).map(UserGroup::getId).collect(Collectors.toSet())
                );

                LOGGER.debug("Found user: {}", user.getName());
            }
        }
    }

    private void purgeUsers(String authToken) throws IOException {
        //purge users that do not exist anymore
        try (final Cursor<Entry> search = directory.findAllUsers()) {
            while (search != null && search.next()) {
                final Entry entry = search.get();
                final String userId = entry.get("description").getString();
                final Response<User> response = hubClient.getUser(authToken, userId).execute();
                if (response.raw().code() == 404 || (response.isSuccessful() && !response.body().getId().equals(userId))) {
                    directory.delete(entry.getDn());
                    LOGGER.debug("Removed user: {}", entry.getDn());
                }
            }
        }
        catch (CursorException | LdapException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void loadUserGroups(String authToken) throws IOException {
        int total = Integer.MAX_VALUE;

        int start = 0;

        while (total > start) {
            final Response<UserGroupsResponse> response = hubClient.getUserGroups(authToken, start, CHUNK_SIZE).execute();
            final UserGroupsResponse body = response.body();
            start += CHUNK_SIZE;
            total = body.getTotal();

            for (UserGroup userGroup : body.getUserGroups()) {
                directory.addGroup(userGroup.getName(), userGroup.getId());
                LOGGER.debug("Found group: {}", userGroup.getName());
            }
        }
    }

    private void purgeGroups(String authToken) throws IOException {
        //purge users that do not exist anymore
        try (final Cursor<Entry> search = directory.findAllGroups()) {
            while (search != null && search.next()) {
                final Entry entry = search.get();
                final String groupId = entry.get("description").getString();
                final Response<User> response = hubClient.getUserGroup(authToken, groupId).execute();
                if (response.raw().code() == 404 || (response.isSuccessful() && !response.body().getId().equals(groupId))) {
                    directory.delete(entry.getDn());
                    LOGGER.debug("Removed group: {}", entry.getDn());
                }
            }
        }
        catch (CursorException | LdapException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
