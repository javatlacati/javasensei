package javasensei.db.managments;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import static com.mongodb.client.model.Filters.*;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javasensei.db.Connection;
import javasensei.estudiante.ModeloEstudiante;

/**
 *
 * @author Rock
 */
public class EstudiantesManager {

    private MongoCollection<BasicDBObject> alumnosCollection = Connection.getCollection().get(CollectionsDB.ALUMNOS);
    private MongoCollection<BasicDBObject> temasCollection = Connection.getCollection().get(CollectionsDB.TEMAS);
    private MongoCollection<BasicDBObject> ejerciciosCollection = Connection.getCollection().get(CollectionsDB.EJERCICIOS);

    private ModeloEstudiante estudiante;

    public EstudiantesManager(ModeloEstudiante estudiante) {
        this.estudiante = estudiante;
    }
    
    public Boolean finalizarEjercicio(int idEjercicio) {
        return finalizarEjercicio(idEjercicio, 1);
    }

    public Boolean finalizarEjercicio(int idEjercicio, double valor) {
        boolean result = false;
        System.out.println("valor para terminado: "+valor);
        try {

            UpdateResult writeResult = alumnosCollection.updateOne(
                    new BasicDBObject("id",estudiante.getId())
                    .append("ejercicios.id",idEjercicio)
                    ,
                    new BasicDBObject("$set",
                            new BasicDBObject(
                                    "ejercicios.$.terminado",valor //valor depende de como se respondio(suboptimo(.7) ó optimo(1))
                        )
                    )
            );

            result = writeResult.getModifiedCount()>0;

        } catch (Exception ex) {
            System.err.println(ex);
        }

        return result;
    }

    public synchronized String insertOrCreateStudent() {
        String result = "{}";

        try {
            synchronized (alumnosCollection) {
                //Buscamos el id de facebook, en caso de no existir, creamos el objeto y obtenemos el nuevo id
                BasicDBObject dbObject = alumnosCollection.find(
                        new BasicDBObject("idFacebook", estudiante.getIdFacebook())
                ).first();
                
                if (dbObject != null) {
                    estudiante.setId(new Double(dbObject.get("id").toString()).longValue());
                    dbObject.put("token", estudiante.getToken()); //Se actualiza el token de facebook
                    
                    alumnosCollection.updateOne(
                            new BasicDBObject("id",estudiante.getId()), 
                            dbObject);
                } else { //El estudiante es nuevo
                    estudiante.setId(alumnosCollection.count());
                    dbObject = estudiante.convertToDBObject(); //True para guardar
                    
                    alumnosCollection.insertOne(dbObject);
                }

                dbObject.removeField("ejercicios");

                result = dbObject.toString();

            }
        } catch (Exception ex) {
            Logger.getGlobal().log(Level.SEVERE, ex.getMessage());
        }

        return result;
    }

    public Double getAbilityGlobal() {
        double result = 0;

        BasicDBObject alumno = alumnosCollection.find(new BasicDBObject("id", estudiante.getId())).first();

        if (alumno != null) {
            //Obtenemos los ejercicios del modelo del estudiante
            BasicDBList ejercicios = (BasicDBList) alumno.get("ejercicios");
            Double totalEjercicios = new Integer(ejercicios.size()).doubleValue();
            Double ejerciciosRealizados = 0D;

            for (Object ejercicio : ejercicios) {
                DBObject ejercicioObject = (DBObject) ejercicio;

                if (Double.parseDouble(ejercicioObject.get("terminado").toString()) == 1) {
                    ejerciciosRealizados++;
                }
            }

            result = ejerciciosRealizados / totalEjercicios;
        }

        return result;
    }

    public boolean createOrUpdateStudentModel() {
        boolean result = false;

        //Tramos los ejercicios del alumno
        BasicDBObject alumno = alumnosCollection.find(
                new BasicDBObject("id",estudiante.getId())
        ).projection(
                new BasicDBObject("_id", 0)
                .append("ejercicios", 1)
        ).first();

        if (alumno != null) {
            List<Integer> ejercicios = new ArrayList<>();
            //List<DBObject> ejerciciosAlumno = new ArrayList<>();

            if (alumno.containsField("ejercicios")) {
                BasicDBList listEjercicios = (BasicDBList) alumno.get("ejercicios");

                for (Object objEjercicio : listEjercicios) {
                    ejercicios.add(new Double(((DBObject) objEjercicio).get("id").toString()).intValue());
                }
            }

            //El campo ejercicio se llena con los id que no tenga el usuario (de ejercicios)
            //Ademas se pone un valor 0 para entender que no esta terminado
            //Id de ejercicios que el alumno no tiene
            MongoCursor<BasicDBObject> ejerciciosCursor = ejerciciosCollection.find(
                    nin("id", ejercicios)
            ).projection(
                    eq("_id", 0)
            ).iterator();

            while (ejerciciosCursor.hasNext()) {
                DBObject objectEjercicio = ejerciciosCursor.next();
                objectEjercicio.put("terminado", 0);
                //ejerciciosAlumno.add(objectEjercicio);

                alumnosCollection.updateOne(
                        eq("id", estudiante.getId()),
                        eq("$addToSet",
                                eq("ejercicios", objectEjercicio))
                );
            }

            result = true;
        }

        return result;
    }

    public boolean updateDataStudent() {
        boolean result = false;

        try {
            alumnosCollection.findOneAndUpdate(new BasicDBObject("id", estudiante.getId()),
                    eq("$set", estudiante.convertToDBObject())
            );

            result = true;

        } catch (Exception ex) {
            Logger.getGlobal().log(Level.SEVERE, ex.getMessage());
        }

        return result;
    }

    public void saveAbilityGlobal(Double value) {
        alumnosCollection.updateOne(
                eq("id", estudiante.getId()),
                eq("$set", 
                        eq("habilidadGlobal",value)
                )
        );
    }

    public void saveAbilityGlobal() {
        saveAbilityGlobal(getAbilityGlobal());
    }
}
