package javasensei.db.managments;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import static com.mongodb.client.model.Filters.*;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javasensei.db.Connection;
import org.bson.Document;

/**
 *
 * @author PosgradoMCC
 */
public class MenuManager {

    private MongoCollection<BasicDBObject> leccionesCollection = Connection.getCollection().get(CollectionsDB.LECCIONES);
    private MongoCollection<BasicDBObject> alumnosCollections = Connection.getCollection().get(CollectionsDB.ALUMNOS);

    public String getDataGraphics(Long idAlumno) {
        List<String> lecciones = new ArrayList<>();
        List<Long> listaEjercicios = new ArrayList<>();

        long maximo = 0;

        List<DBObject> ejercicios = Arrays.stream(((BasicDBList) alumnosCollections.find(
                new BasicDBObject("id", idAlumno)
        ).first().get("ejercicios")).toArray())
                .map((ejercicioObj) -> (DBObject) ejercicioObj).collect(Collectors.toList());

        MongoCursor<BasicDBObject> cursor = leccionesCollection.find(
                eq("nombre", 1)
        ).projection(
                new BasicDBObject("nombre", 1)
                .append("id", 1)
                .append("_id", 0)
        ).iterator();

        while (cursor.hasNext()) {
            DBObject object = cursor.next();

            lecciones.add(object.get("nombre").toString());

            Integer id = new Double(object.get("id").toString()).intValue();

            List<DBObject> ejerciciosLeccion = ejercicios.stream().filter((ejercicio)
                    -> new Double(ejercicio.get("idLeccion").toString()).intValue() == id
            ).collect(Collectors.toList());

            long cantidadTotalEjercicios = ejerciciosLeccion.stream().count();
            if (cantidadTotalEjercicios > maximo) {
                maximo = cantidadTotalEjercicios;
            }

            long cantidadEjercicios = ejerciciosLeccion.stream().filter(ejercicio
                    -> Double.parseDouble(ejercicio.get("terminado").toString()) > 0
            ).count();

            listaEjercicios.add(cantidadEjercicios);
        }

        DBObject resultado = BasicDBObjectBuilder.start("maximo", maximo)
                .add("lecciones", lecciones)
                .add("listaEjercicios", listaEjercicios)
                .get();

        return resultado.toString();
    }

    public String getCursoMenu(Long idAlumno) {
        BasicDBList list = new BasicDBList();

        List<DBObject> ejercicios = Arrays.stream(((BasicDBList) alumnosCollections.find(
                new BasicDBObject("id", idAlumno)
        ).first().get("ejercicios")).toArray())
                .map((ejercicioObj) -> (DBObject) ejercicioObj).collect(Collectors.toList());

        MongoCursor<BasicDBObject> cursor = leccionesCollection.find(
                new BasicDBObject()
        ).projection(
                new BasicDBObject("nombre", 1)
                .append("id", 1)
                .append("_id", 0)
        ).iterator();

        while (cursor.hasNext()) {
            DBObject object = cursor.next();
            object.put("nombre", object.get("nombre"));

            Integer id = new Double(object.get("id").toString()).intValue();

            object.put("id", id);
            List<Object> o = ejercicios.stream().filter((ejercicio)
                    -> new Double(ejercicio.get("idLeccion").toString()).intValue() == id
            ).collect(Collectors.toList());

            if (!o.isEmpty()) {
                object.put("ejercicios", o);
                list.add(object);
            }

        }

        return list.toString();
    }
}
