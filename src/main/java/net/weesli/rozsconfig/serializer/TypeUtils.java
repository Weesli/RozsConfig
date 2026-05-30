package net.weesli.rozsconfig.serializer;

import net.weesli.rozsconfig.annotations.ConfigKey;
import net.weesli.rozsconfig.serializer.component.ObjectSerializer;

import java.lang.reflect.*;
import java.util.*;

final class TypeUtils {

    private TypeUtils() {}

    static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }

    static boolean isWrapper(Class<?> c) {
        return c == Integer.class || c == Long.class || c == Double.class ||
                c == Float.class || c == Boolean.class || c == Byte.class ||
                c == Character.class || c == Short.class;
    }

    static boolean isSimpleType(Class<?> c) {
        return c.isPrimitive() || isWrapper(c) || c == String.class || c.isEnum();
    }

    static boolean isCollectionOrMap(Class<?> c) {
        return Map.class.isAssignableFrom(c) || Collection.class.isAssignableFrom(c);
    }

    static String resolveKey(Field f) {
        if (f.isAnnotationPresent(ConfigKey.class)) {
            return f.getAnnotation(ConfigKey.class).value();
        }
        return f.getName();
    }

    @SuppressWarnings("unchecked")
    static Object coerce(Object v, Class<?> target) {
        if (v == null) return null;
        if (target.isInstance(v)) return v;

        // Enum conversion
        if (target.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) target, v.toString());
        }

        // String target – always safe via valueOf
        if (target == String.class) return String.valueOf(v);

        // Boolean target
        if (target == boolean.class || target == Boolean.class) {
            if (v instanceof Boolean) return v;
            return Boolean.parseBoolean(v.toString());
        }

        // Character target
        if (target == char.class || target == Character.class) {
            String s = v.toString();
            return s.isEmpty() ? '\0' : s.charAt(0);
        }

        // Number target – handle both Number and String sources
        if (v instanceof Number n) {
            if (target == int.class    || target == Integer.class) return n.intValue();
            if (target == long.class   || target == Long.class)   return n.longValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class  || target == Float.class)  return n.floatValue();
            if (target == short.class  || target == Short.class)  return n.shortValue();
            if (target == byte.class   || target == Byte.class)   return n.byteValue();
        }

        // String → Number parsing (e.g. YAML value "123" but field is int)
        String str = v.toString();
        try {
            if (target == int.class    || target == Integer.class) return Integer.parseInt(str);
            if (target == long.class   || target == Long.class)   return Long.parseLong(str);
            if (target == double.class || target == Double.class) return Double.parseDouble(str);
            if (target == float.class  || target == Float.class)  return Float.parseFloat(str);
            if (target == short.class  || target == Short.class)  return Short.parseShort(str);
            if (target == byte.class   || target == Byte.class)   return Byte.parseByte(str);
        } catch (NumberFormatException ignored) {
            // value is not a valid number string – fall through
        }

        return v;
    }

    @SuppressWarnings("unchecked")
    static Object newDefaultContainer(Class<?> c) {
        if (Map.class.isAssignableFrom(c)) {
            if (SortedMap.class.isAssignableFrom(c)) return new TreeMap<>();
            if (java.util.concurrent.ConcurrentMap.class.isAssignableFrom(c)) return new java.util.concurrent.ConcurrentHashMap<>();
            return new LinkedHashMap<>();
        }
        if (Set.class.isAssignableFrom(c)) {
            if (SortedSet.class.isAssignableFrom(c)) return new TreeSet<>();
            return new LinkedHashSet<>();
        }
        if (List.class.isAssignableFrom(c)) return new ArrayList<>();
        if (Deque.class.isAssignableFrom(c) || Queue.class.isAssignableFrom(c)) return new ArrayDeque<>();
        if (Collection.class.isAssignableFrom(c)) return new ArrayList<>();
        throw new IllegalArgumentException("Unsupported container type for default: " + c.getName());
    }

    static Class<?> getMapValueType(Field f) {
        Type t = f.getGenericType();
        if (t instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 2) {
                Type val = args[1];
                if (val instanceof Class<?>) return (Class<?>) val;
                if (val instanceof ParameterizedType pval && pval.getRawType() instanceof Class<?>)
                    return (Class<?>) pval.getRawType();
            }
        }
        return null;
    }

    static Class<?> getCollectionElementType(Field f) {
        Type t = f.getGenericType();
        if (t instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1) {
                Type val = args[0];
                if (val instanceof Class<?>) return (Class<?>) val;
                if (val instanceof ParameterizedType pval && pval.getRawType() instanceof Class<?>)
                    return (Class<?>) pval.getRawType();
            }
        }
        return null;
    }

    static Class<?> getRawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c)
            return c;
        return null;
    }

    static Type getMapValueGenericType(Type containerType) {
        if (containerType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 2) return args[1];
        }
        return null;
    }

    static Type getCollectionElementGenericType(Type containerType) {
        if (containerType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1) return args[0];
        }
        return null;
    }

    static ObjectSerializer<?> findSerializerFor(Class<?> type, List<ObjectSerializer<?>> serializers) {
        for (ObjectSerializer<?> s : serializers) {
            if (s.isType(type)) return s;
        }
        return null;
    }
}
