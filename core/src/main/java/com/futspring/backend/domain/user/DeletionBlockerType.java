package com.futspring.backend.domain.user;

public enum DeletionBlockerType {
    // created the pelada: transfer or delete it first
    CREATOR,
    // the pelada's only responsável financeiro: appoint another first
    SOLE_FINANCE_MANAGER
}
