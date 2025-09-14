package net.weesli.rozsconfig.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If a user makes a change to a {@link java.util.Map} in the config, and it is marked with {@link IgnoreKeys}, the keys there will not be pulled from the default config.
 *
 * @author Weesli
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IgnoreKeys {
}
