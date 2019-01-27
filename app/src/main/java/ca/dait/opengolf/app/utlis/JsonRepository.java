package ca.dait.opengolf.app.utlis;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.Update;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic singleton repository for persisting Java Pojo's as JSON documents using Androids Room
 * Persistence Library. This repo delegates as much work to the background thread as possible
 * including all I/O and marshalling/unmarshalling of JSON.
 *
 */
public class JsonRepository{

    private static final Gson GSON = new Gson();

    public static final int TYPE_COURSE = 1;
    private static final String JSON_ENTITY_DATABASE = "JSON_ENTITY_DATABASE";

    private static JsonRepository instance = null;
    private final EntityDao dao;
    private JsonRepository(Context applicationContext){
        JsonDatabase db = Room.databaseBuilder(applicationContext, JsonDatabase.class, JSON_ENTITY_DATABASE).build();
        this.dao = db.getDao();
    }

    public static JsonRepository getInstance(Context applicationContext){
        if(instance == null){
            instance = new JsonRepository(applicationContext);
        }
        return instance;
    }

    public <T> void getAll(Class<T> clazz, Integer type, Consumer<List<JsonEntity<T>>> resultHandler){
        new FetchAsyncTask<T, Integer, List<JsonEntity<T>>>(clazz, this.dao::getAll, resultHandler).execute(type);
    }

    public <T> void get(Class<T> clazz, final Integer id, Consumer<JsonEntity<T>> resultHandler){
        new FetchAsyncTask<T, Integer, JsonEntity<T>>(clazz, this.dao::get, resultHandler).execute(id);
    }

    public <T> void insert(Integer type, T object){
        RawEntity rawEntity = new RawEntity(type, object);
        new UpdateAsyncTask<RawEntity, long[]>(this.dao::insert).execute(rawEntity);
    }

    public <T> void insert(Integer type, T object, Consumer<long[]> handler){
        RawEntity rawEntity = new RawEntity(type, object);
        new UpdateAsyncTask<RawEntity, long[]>(this.dao::insert, handler).execute(rawEntity);
    }

    public <T> void update(Integer id, Integer type, T object){
        RawEntity rawEntity = new RawEntity(id, type, object);
        new UpdateAsyncTask<RawEntity, Integer>(this.dao::update).execute(rawEntity);
    }

    public <T> void update(Integer id, Integer type, T object, Consumer<Integer> handler){
        RawEntity rawEntity = new RawEntity(id, type, object);
        new UpdateAsyncTask<RawEntity, Integer>(this.dao::update, handler).execute(rawEntity);
    }

    public void delete(Integer id){
        new UpdateAsyncTask<Integer, Integer>(this.dao::delete).execute(id);
    }

    public void delete(Integer id, Consumer<Integer> resultHandler){
        new UpdateAsyncTask<Integer, Integer>(this.dao::delete, resultHandler).execute(id);
    }

    /**
     * Async task for inserts, deletes and updates.
     *
     * @param <ParamType>
     * @param <ReturnType>
     */
    private static class UpdateAsyncTask<ParamType,ReturnType> extends AsyncTask<ParamType, Void, ReturnType>{
        private final Function<ParamType[], ReturnType> backgroundHandler;
        private final Consumer<ReturnType> postHandler;

        private UpdateAsyncTask(Function<ParamType[], ReturnType> backgroundHandler){
            this.backgroundHandler = backgroundHandler;
            this.postHandler = results -> { /* Do Nothing */ };
        }

        private UpdateAsyncTask(Function<ParamType[], ReturnType> backgroundHandler, Consumer<ReturnType> postHandler){
            this.backgroundHandler = backgroundHandler;
            this.postHandler = postHandler;
        }

        @Override
        @SafeVarargs
        protected final ReturnType doInBackground(ParamType... params){
            return this.backgroundHandler.apply(params);
        }
        @Override
        protected final void onPostExecute(ReturnType results){
            this.postHandler.accept(results);
        }
    }

    /**
     * Async task for fetching content. Different than UpdateAsyncTask as it requires the BiFunction
     * to additionally accept an object type in the background thread.
     *
     * @param <ObjectType>
     * @param <ParamType>
     * @param <ResultType>
     */
    private static class FetchAsyncTask<ObjectType, ParamType, ResultType> extends AsyncTask<ParamType, Void, ResultType> {
        private Class<ObjectType> clazz;
        private final BiFunction<Class<ObjectType>, ParamType[], ResultType> backgroundHandler;
        private final Consumer<ResultType> postHandler;

        private FetchAsyncTask(Class<ObjectType> clazz, BiFunction<Class<ObjectType>, ParamType[], ResultType> backgroundHandler, Consumer<ResultType> postHandler){
            this.clazz = clazz;
            this.backgroundHandler = backgroundHandler;
            this.postHandler = postHandler;
        }

        @Override
        @SafeVarargs
        protected final ResultType doInBackground(ParamType... params){
            return this.backgroundHandler.apply(this.clazz, params);
        }

        @Override
        protected final void onPostExecute(ResultType results){
            this.postHandler.accept(results);
        }
    }

    @Database(entities = {JsonRepository.RawEntity.class}, version = 1, exportSchema = false)
    public static abstract class JsonDatabase extends RoomDatabase{
        public abstract EntityDao getDao();
    }

    /**
     * Dao interface for repo. Provides default methods to marshal/unmarshal JSON in the background.
     */
    @Dao
    public interface EntityDao {

        @Query("SELECT * FROM RawEntity WHERE id = :id")
        RawEntity get(Integer id);

        @Query("SELECT * FROM RawEntity WHERE type = :type")
        List<RawEntity> getAll(Integer type);

        @Insert
        long[] insert(RawEntity... entity);

        @Update
        int update(RawEntity... entity);

        @Query("DELETE from RawEntity where id =:id")
        int delete(Integer... id);

        default <T> T doSomething(T obj){
            return null;
        }

        default <T> JsonEntity<T> get(Class<T> clazz, Integer... id){
            RawEntity rawEntity = this.get(id[0]);
            if(rawEntity != null){
                return new JsonEntity<T>(clazz, rawEntity);
            }
            return null;
        }

        default <T> List<JsonEntity<T>> getAll(Class<T> clazz, Integer... type){
            List<RawEntity> result = this.getAll(type[0]);
            return result.stream()
                .map(rawEntity -> new JsonEntity<T>(clazz, rawEntity))
                .collect(Collectors.toList());
        }
    }

    /**
     * Simple entity for persisting a raw json string. Room Persistence Library does not play well
     * with Generics, therefore the RawEntity is used for persistence and JsonEntity<T> is exposed
     * to users as more user friendly.
     */
    @Entity
    public static class RawEntity{
        @PrimaryKey(autoGenerate = true) private int id;
        @ColumnInfo(name = "type") private int type;
        @NonNull @ColumnInfo(name = "json") private String json = "";

        RawEntity(){}
        RawEntity(Integer type, Object object){
            this.type = type;
            this.json = GSON.toJson(object);
        }
        RawEntity(Integer id, Integer type, Object object){
            this.id = id;
            this.type = type;
            this.json = GSON.toJson(object);
        }

        void setId(int id){
            this.id = id;
        }

        void setType(int type){
            this.type = type;
        }

        void setJson(@NonNull String json){
            this.json = json;
        }

        int getId(){
            return this.id;
        }

        int getType(){
            return this.type;
        }

        @NonNull String getJson(){
            return this.json;
        }
    }

    /**
     * Friendlier representation of persisted JSON object.
     *
     * @param <T>
     */
    public static class JsonEntity<T>{
        public final int id;
        public final int type;
        public final T ref;

        JsonEntity(Class<T> clazz, RawEntity rawEntity){
            this.id = rawEntity.id;
            this.type = rawEntity.getType();
            this.ref = GSON.fromJson(rawEntity.json, clazz);
        }
    }
}
