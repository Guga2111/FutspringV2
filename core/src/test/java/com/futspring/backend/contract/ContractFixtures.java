package com.futspring.backend.contract;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Builds a DTO with every field set, so every property shows up in its JSON snapshot. Each value comes from the
 * field's name and type only (strings are the field name, numbers a hash of it, booleans true, one list element),
 * never from declaration order or the class's shape: a DTO turned into a record gets the very same values, so its
 * snapshot must not change.
 */
final class ContractFixtures {

    static final Instant INSTANT = Instant.parse("2026-10-31T23:00:00Z");
    static final LocalDate DATE = LocalDate.of(2026, 10, 31);

    private ContractFixtures() {
    }

    static <T> T filled(Class<T> type) {
        return type.cast(value(type, type, "root", 0));
    }

    private static Object value(Class<?> type, Type generic, String name, int depth) {
        if (type == String.class) return name;
        if (type == Long.class || type == long.class) return (long) number(name);
        if (type == Integer.class || type == int.class) return number(name);
        if (type == Double.class || type == double.class) return number(name) + 0.5;
        if (type == Float.class || type == float.class) return number(name) + 0.5f;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == Instant.class) return INSTANT;
        if (type == LocalDate.class) return DATE;
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
            Type element = ((ParameterizedType) generic).getActualTypeArguments()[0];
            Object item = value(rawType(element), element, name, depth + 1);
            return List.class.isAssignableFrom(type) ? List.of(item) : Set.of(item);
        }
        if (depth > 5) {
            throw new IllegalStateException("Fixture too deep at " + name + " (" + type.getName() + ")");
        }
        return type.isRecord() ? record(type, depth) : bean(type, depth);
    }

    private static Object record(Class<?> type, int depth) {
        RecordComponent[] components = type.getRecordComponents();
        Object[] args = Arrays.stream(components)
                .map(c -> value(c.getType(), c.getGenericType(), c.getName(), depth + 1))
                .toArray();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        return construct(type, types, args);
    }

    // No-args constructor + fields when there is one (Lombok @NoArgsConstructor), else the all-args constructor
    private static Object bean(Class<?> type, int depth) {
        List<Field> fields = Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .toList();
        try {
            Constructor<?> noArgs = type.getDeclaredConstructor();
            noArgs.setAccessible(true);
            Object instance = noArgs.newInstance();
            for (Field field : fields) {
                field.setAccessible(true);
                field.set(instance, value(field.getType(), field.getGenericType(), field.getName(), depth + 1));
            }
            return instance;
        } catch (NoSuchMethodException e) {
            Object[] args = fields.stream()
                    .map(f -> value(f.getType(), f.getGenericType(), f.getName(), depth + 1))
                    .toArray();
            return construct(type, fields.stream().map(Field::getType).toArray(Class<?>[]::new), args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + type.getName(), e);
        }
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object[] args) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + type.getName(), e);
        }
    }

    private static Class<?> rawType(Type type) {
        return type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) type;
    }

    private static int number(String name) {
        return Math.floorMod(name.hashCode(), 1000) + 1;
    }
}
