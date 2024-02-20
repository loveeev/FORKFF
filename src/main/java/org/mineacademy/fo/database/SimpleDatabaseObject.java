package org.mineacademy.fo.database;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.Logger;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.SerializeUtil;
import org.mineacademy.fo.collection.SerializedMap;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public abstract class SimpleDatabaseObject<T> extends SimpleDatabaseManager {

    public SimpleDatabaseObject() {
        this.addVariable("table", getTableName());

        // Check if deserialize method exist
        this.getDeserializeMethod();
    }

    public abstract String getTableName();
    public abstract Class<T> getObjectClass();
    @NonNull
    public abstract SerializedMap serialize(T object);

    public final void insert(@NotNull T object, @NotNull Callback<Void> callback) {
        this.insert(getTableName(), serialize(object), callback);
    }

    public final void insert(@NonNull SerializedMap columnsAndValues, @NotNull Callback<Void> callback) {
        this.insert(getTableName(), columnsAndValues, callback);
    }

    public final void insertBatch(@NonNull List<SerializedMap> maps, @NotNull Callback<Void> callback) {
        this.insertBatch(getTableName(), maps, callback);
    }

    protected final void count(@NotNull Callback<Integer> callback, Object... array) {
        this.count(getTableName(), callback, SerializedMap.ofArray(array));
    }

    protected final void count(SerializedMap conditions, @NotNull Callback<Integer> callback) {
        this.count(getTableName(), conditions, callback);
    }

    public final void select(String columns, @NotNull Callback<ResultSet> callback) {
        this.select(getTableName(), columns, callback);
    }

    public void selectAll(@NotNull Callback<List<T>> callback){
        this.selectAllWhere(null, callback);
    }

    public void selectAllWhere(String where, @NotNull Callback<List<T>> callback){
        final boolean[] failed = new boolean[1];
        List<T> objects = new ArrayList<>();

        this.selectAllForEach(getTableName(), where, new Callback<ResultSet>() {
            @Override
            public void onSuccess(ResultSet set) {
                objects.add(invokeDeserialize(set));
            }

            @Override
            public void onFail(Throwable t) {
                callback.onFail(t);
                failed[0] = true;
            }
        });

        if (!failed[0]){
            callback.onSuccess(objects);
        }
    }

    private Method getDeserializeMethod(){
        final Method des = ReflectionUtil.getMethod(getClass(), "deserialize", ResultSet.class);

        if (des == null){
            Logger.printErrors("Unable to deserialize ResultSet to class " + getObjectClass().getSimpleName(),
                    "Your class " + getClass().getSimpleName() + " is extending SimpleDatabaseObject",
                    "and must contain the following method:",
                    "public static " + getObjectClass().getSimpleName() + " deserialize(ResultSet set)");
            throw new SerializeUtil.SerializeFailedException("Unable to deserialize ResultSet to class " + getObjectClass().getSimpleName());
        }

        return des;
    }

    private T invokeDeserialize(ResultSet set){
        return ReflectionUtil.invokeStatic(getDeserializeMethod(), set);
    }
}