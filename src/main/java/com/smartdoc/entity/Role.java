package com.smartdoc.entity;


/**
 * Roles assigned to users for role-based access control.
 *
 * USER  → can upload and view their own documents only
 * ADMIN → can view all documents, run reconciliation, see reports
 */
public enum Role {
    USER,
    ADMIN
}
