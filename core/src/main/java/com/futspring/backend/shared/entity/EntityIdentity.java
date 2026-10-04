package com.futspring.backend.shared.entity;

import org.hibernate.proxy.HibernateProxy;

// Id-based equals/hashCode for entities, safe with Hibernate proxies (does not initialize them)
public final class EntityIdentity {

    private EntityIdentity() {
    }

    public static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }

    public static boolean sameEntity(Object self, Object other, Long selfId, Long otherId) {
        if (self == other) return true;
        if (other == null || effectiveClass(self) != effectiveClass(other)) return false;
        return selfId != null && selfId.equals(otherId);
    }
}
