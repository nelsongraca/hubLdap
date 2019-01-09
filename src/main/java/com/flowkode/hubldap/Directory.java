package com.flowkode.hubldap;

import org.apache.directory.api.ldap.model.cursor.Cursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.name.Dn;

import java.util.Set;

public interface Directory {

    Cursor<Entry> search(ExprNode node);

    void delete(Dn dn);

    void addUser(String name, String id, String mail, String login, Set<String> groups);

    Cursor<Entry> findAllUsers();

    Cursor<Entry> findAllGroups();

    void addGroup(String name, String id);
}
