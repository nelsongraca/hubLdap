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
