package net.weesli.rozsconfig.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * If a variable is marked with this class, that field name is mapped to this value in the config.
 *
 * @author Weesli
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigKey {
    String value();
}
