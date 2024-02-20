package org.mineacademy.fo.model;

import com.google.common.base.CaseFormat;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.collection.SerializedMap;

/**
 * Classes implementing this interface are automatically serialized and deserialized to/from the file.<br>
 * It is not necessary to implement any methods.
 */
public interface AutoSerializable extends ConfigSerializable {

    /**
     * If true, firstly calls parent's 'serialize()' method and then
     * auto-serializes the fields of this class.
     * @return should we serialize deeply
     */
    default boolean serializeDeeply(){
        return false;
    }

    /**
     * If false, collections (lists, maps, sets, etc.) with 0 elements would not be loaded
     * and assigned to a field when using @AutoSerialize
     * @return default = true
     */
    default boolean loadEmptyCollections() {
        return true;
    }

    /**
     * In what format should we convert your fields to SerializedMap.<br>
     * Default: lower_underscore.
     */
    default CaseFormat getFormat(){
        return CaseFormat.LOWER_UNDERSCORE;
    }

    default SerializeUtil.Mode getMode(){
        return SerializeUtil.Mode.YAML;
    }

    @Override
    default SerializedMap serialize() {
        return SerializeUtil.saveObjectsToMap(this);
    }
}
