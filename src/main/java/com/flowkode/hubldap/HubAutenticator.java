package com.flowkode.hubldap;

import com.flowkode.hubldap.data.AuthResponse;
import org.apache.directory.api.ldap.model.constants.AuthenticationLevel;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.server.core.api.LdapPrincipal;
import org.apache.directory.server.core.api.interceptor.context.BindOperationContext;
import org.apache.directory.server.core.authn.AbstractAuthenticator;
import retrofit2.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HubAutenticator extends AbstractAuthenticator {


    private final HubClient hubClient;

    private final String credentials;

    private final Directory directory;

    public HubAutenticator(Dn rootDn, Directory directory, HubClient hubClient, String serviceId, String serviceSecret) {
        super(AuthenticationLevel.SIMPLE, rootDn);
        this.hubClient = hubClient;
        this.directory = directory;
        credentials = "Basic " + Base64.getEncoder().encodeToString((serviceId + ":" + serviceSecret).getBytes());
    }

    @Override
    public LdapPrincipal authenticate(BindOperationContext bindOperationContext) throws LdapException {
        try {
            String username = directory.getUsername(bindOperationContext.getDn());
            String password = new String(bindOperationContext.getCredentials(), StandardCharsets.UTF_8);

            Response<AuthResponse> u = hubClient.userLogin(credentials, username, password).execute();
            if (u.isSuccessful()) {
                return new LdapPrincipal(this.getDirectoryService().getSchemaManager(), bindOperationContext.getDn(), AuthenticationLevel.SIMPLE);
            }
            else {
                throw new javax.naming.AuthenticationException("Invalid credentials for user: " + username);
            }
        }
        catch (Exception ex) {
            throw new LdapException("Could not authenticate.");
        }

    }
}
