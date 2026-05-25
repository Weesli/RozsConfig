package net.weesli.rozsconfig.serializer;

import net.weesli.rozsconfig.annotations.Comment;
import net.weesli.rozsconfig.annotations.IgnoreField;
import net.weesli.rozsconfig.serializer.component.ObjectNode;
import net.weesli.rozsconfig.serializer.component.ObjectSerializer;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Field;
import java.util.*;

final class ConfigWriter {

    private final Yaml yaml;
    private final List<ObjectSerializer<?>> serializers;

    ConfigWriter(Yaml yaml, List<ObjectSerializer<?>> serializers) {
        this.yaml = yaml;
        this.serializers = serializers;
    }

    void writeYamlWithComments(Object obj, StringBuilder sb) throws IllegalAccessException {
        for (Field field : TypeUtils.getAllFields(obj.getClass())) {
            if (field.getType() == ObjectNode.class) continue;
            if (field.isAnnotationPresent(IgnoreField.class)) continue;
            field.setAccessible(true);

            Object value = field.get(obj);
            if (value == null) continue;

            String key = TypeUtils.resolveKey(field);
            if (field.isAnnotationPresent(Comment.class)) {
                for (String c : field.getAnnotation(Comment.class).value()) {
                    indent(sb, 0).append("# ").append(c).append("\n");
                }
            }

            Object plain = toPlain(value);

            writeValue(sb, 0, key, plain);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeValue(StringBuilder sb, int indent, String key, Object value) {
        indent(sb, indent).append(key).append(":");

        if (value instanceof Map || value instanceof List) {
            sb.append("\n");

            String dumped = dumpValue(value);
            for (String line : dumped.split("\n")) {
                indent(sb, indent + 2).append(line).append("\n");
            }
        } else {
            sb.append(" ").append(dumpValue(value)).append("\n");
        }
    }

    private String dumpValue(Object value) {
        String dumped = yaml.dump(value);
        return dumped.endsWith("\n")
                ? dumped.substring(0, dumped.length() - 1)
                : dumped;
    }

    private StringBuilder indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append(" ");
        return sb;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object toPlain(Object value) {
        if (value == null) return null;
        Class<?> t = value.getClass();

        if (TypeUtils.isSimpleType(t) && !value.getClass().isEnum()) return value;
        if (TypeUtils.isSimpleType(t) && value.getClass().isEnum()) return ((Enum<?>) value).name();

        ObjectSerializer ser = TypeUtils.findSerializerFor(t, serializers);
        if (ser != null) {
            ObjectNode node = new ObjectNode(new LinkedHashMap<>());
            ser.serialize(value, node);
            return node.getVariableMap();
        }

        if (value instanceof Map<?,?> map) {
            Map<Object,Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : map.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                out.put(k, toPlain(v));
            }
            return out;
        }

        if (value instanceof Enum<?>){
            return ((Enum<?>) value).name();
        }

        if (value instanceof Collection<?> col) {
            List<Object> out = new ArrayList<>(col.size());
            for (Object v : col) out.add(toPlain(v));
            return out;
        }

        Map<String,Object> out = new LinkedHashMap<>();
        for (Field f : TypeUtils.getAllFields(t)) {
            try {
                if (f.getType() == ObjectNode.class) continue;
                f.setAccessible(true);
                Object fv = f.get(value);
                if (fv == null) continue;
                out.put(TypeUtils.resolveKey(f), toPlain(fv));
            } catch (IllegalAccessException ignored) {}
        }
        return out;
    }
}
