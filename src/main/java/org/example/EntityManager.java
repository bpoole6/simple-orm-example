package org.example;

import org.apache.commons.dbcp2.BasicDataSource;
import org.example.annotations.Column;
import org.example.annotations.Entity;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.Call;
import org.jdbi.v3.core.statement.Query;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.*;


//Read all classes from a package
//Look for all classes that has @Entity(table="")
//Dynamically construct select statement
//Dynamically construct upsert statement

public class EntityManager {

    private final Map<Class, List<ColumnInfo>> columnInfoMap = new HashMap<>();
    private final Map<Class, String> selectSqlMap = new HashMap<>();
    private final Map<Class, String> upsertSqlMap = new HashMap<>();

    private static EntityManager self;
    @Column
    private Integer a;


    private EntityManager(){

    }

    public synchronized static EntityManager getOrCreate(){
        if(EntityManager.self == null){
            EntityManager.self = new EntityManager();
        }
        return EntityManager.self;
    }




    private void add(Class clz,  List<ColumnInfo> columnInfos) throws NoSuchFieldException, IllegalAccessException {
        String tableName = EntityManager.tableName(clz);
        String selectSQL = EntityManager.constructSelectStatement(columnInfos, tableName);
        String upsertSQL = EntityManager.constructUpsertStatement(columnInfos, tableName);
        System.out.println(upsertSQL);
        selectSqlMap.put(clz,selectSQL);
        upsertSqlMap.put(clz, upsertSQL);
    }

    public boolean save(Object obj, Connection connection) throws IllegalAccessException {
        Class clz = obj.getClass();
        if(columnInfoMap.containsKey(clz)){
            List<ColumnInfo> columns = columnInfoMap.get(clz).stream().filter(ColumnInfo::isInsertable).toList();
            Jdbi jdbi = Jdbi.create(connection);
            try(Handle handle = jdbi.open()) {
                Call ps = handle.createCall(upsertSqlMap.get(clz));
                List<ColumnInfo> insertable = columns.stream().filter(ColumnInfo::isInsertable).toList();
                for (ColumnInfo ci : insertable) {
                    ps.bind(":%s".formatted(ci.getName()), ci.getField().get(obj));
                }
            }
        }
        return true;
    }

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        Class.forName("org.h2.Driver");
        BasicDataSource ds = new BasicDataSource();
        Jdbi c = Jdbi.create("jdbc:h2:mem:myDb;DB_CLOSE_DELAY=-1");

        try(Handle h = c.open()){
            Call cc = h.createCall("Create table T(id int)");
            cc.invoke();
            h.execute("insert into T VALUES (4)");

            Query q = h.createQuery("select * from T");
            ResultIterable<Map<String, Object>> m = q.mapToMap();
            List<Map<String, Object>> list = m.list();
            System.out.println();
        }

        loadSqlStatements("org.example");
    }


    public static void loadSqlStatements(String packageName) throws NoSuchFieldException, IllegalAccessException {
        Reflections reflections = new Reflections(packageName);
        Set<Class<? extends Object>> allClasses =
                reflections.getTypesAnnotatedWith(Entity.class);
        for (Class<? extends Object> clz : allClasses) {
            List<ColumnInfo> columnInfos = readFields(clz);
            EntityManager.getOrCreate().add(clz,columnInfos);
        }

    }

    private static List<ColumnInfo> readFields(Class clz){
        Field[] fields = clz.getDeclaredFields();
        List<ColumnInfo> columnInfos = new ArrayList<>();
        for (Field f : fields) {
            if (f.isAnnotationPresent(Column.class)) {
                ColumnInfo columnInfo = new ColumnInfo();
                columnInfo.setName(columnName(f));
                columnInfo.setOnConflict(f.getAnnotation(Column.class).conflict());
                columnInfo.setInsertable(isInsertable(f));
                columnInfo.setUpdatable(columnInfo.isInsertable() && f.getAnnotation(Column.class).updatable());

                columnInfo.setField(f);
                columnInfos.add(columnInfo);
            }
        }
        return columnInfos;
    }
    public static String constructSelectStatement(List<ColumnInfo> list, String tableName) {
        StringJoiner sj = new StringJoiner(", ");
        for (ColumnInfo ci : list) {
            sj.add(ci.getName());
        }


        return "SELECT %s FROM %s".formatted(sj.toString(), tableName);
    }
    public static String constructUpsertStatement(List<ColumnInfo> columnInfos, String tableName) {
        StringJoiner columnNames = new StringJoiner(", ");
        StringJoiner namedParameters = new StringJoiner(", ");
        StringJoiner updates = new StringJoiner(", ");
        StringJoiner onConflictClause = new StringJoiner(", ");
        for (ColumnInfo ci : columnInfos) {
            if (ci.isInsertable()) {
                columnNames.add(ci.getName());
                namedParameters.add(":%s".formatted(ci.getName()));
            }
            if (ci.isUpdatable()) {
                updates.add("%s = EXCLUDED.%s".formatted(ci.getName(), ci.getName()));
            }
            if(ci.isOnConflict()){
                onConflictClause.add(ci.getName());
            }
        }

        return "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT(%s) DO UPDATE %s".formatted(tableName,
                columnNames.toString(),
                namedParameters.toString(),
                onConflictClause.toString(),
                updates.toString());
    }


    private static boolean isInsertable(Field f) {
        boolean isTransient = Modifier.isTransient(f.getModifiers());
        boolean isInsertable = f.getAnnotation(Column.class).insertable();
        return !isTransient && isInsertable;
    }



    public static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column.name().isBlank()) {
            return toSnake(field.getName());
        } else {
            return column.name();
        }
    }

    public static <T> String tableName(Class<T> clz) {
        Entity column = clz.getAnnotation(Entity.class);

        if (column.table().isBlank()) {
            return toSnake(clz.getSimpleName());
        } else {
            return column.table();
        }
    }

    public static String toSnake(String s) {
        String result = "";
        char c = s.charAt(0);
        result = result + Character.toLowerCase(c);

        for (int i = 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch)) {
                result = result + '_';
                result = result + Character.toLowerCase(ch);
            } else {
                result = result + ch;
            }
        }
        return result;
    }
}