package org.mineacademy.fo.model;

import org.jetbrains.annotations.NotNull;

/**
 * This interface is used to show that the class must be serialized as a String.<br><br>
 * A class must have two methods to accomplish this:<br>
 * <ol>
 *     <li>public String serializeToString()</li>
 *     <li>public static YourClass deserialize(String str)</li>
 * </ol>
 */
public interface StringSerializable {

    @NotNull
    String serialize();

}
