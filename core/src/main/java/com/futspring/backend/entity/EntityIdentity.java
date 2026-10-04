package com.futspring.backend.entity;

import org.hibernate.proxy.HibernateProxy;

// Id-based equals/hashCode for entities, safe with Hibernate proxies (does not initialize them)
final class EntityIdentity {

    private EntityIdentity() {
    }

    static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }

    static boolean sameEntity(Object self, Object other, Long selfId, Long otherId) {
        if (self == other) return true;
        if (other == null || effectiveClass(self) != effectiveClass(other)) return false;
        return selfId != null && selfId.equals(otherId);
    }
}
