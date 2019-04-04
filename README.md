# HubLdap

HubLdap is a small server that connects to a JetBrains Hub instance and provides LDAP. 

Admin bindDn is `uid=admin,ou=system`

### Config example:

Create `hubLdap.properties` in the same directory as the jar

    hubUrl=http://localhost:8080/hub
    rootDomain=hub.local
    adminPassword=test
    serviceId=1be62e64-e5b6-457b-b0cd-9fb5b16cade4
    serviceSecret=test
    


### LDAPS

A certificate with a validity of 10 years is automatically generated,
if you wish you can set your own, just place it with the name `keystore.p12`
on the same directory as the jar.
The certificate password can ne set with the property `certificatePassword` in the property file.

### Docker

    docker --name hubLdap \
    -e"HUB_URL=http://localhost:8080/hub" \
    -e"ROOT_DOMAIN=hub.local" \
    -e"ADMIN_PASSWORD=test" \
    -e"SERVICE_ID=1be62e64-e5b6-457b-b0cd-9fb5b16cade4" \
    -e"SERVICE_SECRET=test" \
    -e"CERTIFICATE_PASSWORD=secret" \
    -p"10389:10389" \
    -p"10636:10636" \
    nelsongraca/hubldap` 
