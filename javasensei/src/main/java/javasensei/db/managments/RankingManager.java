/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javasensei.db.managments;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.Block;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javasensei.db.Connection;
import javasensei.estudiante.ModeloEstudiante;
import javasensei.exceptions.JavaException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.AbstractRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.PearsonCorrelationSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import static com.mongodb.client.model.Filters.*;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;

/**
 *
 * @author Rock
 */
public class RankingManager {

    private MongoCollection<BasicDBObject> ejercicios = Connection.getCollection().get(CollectionsDB.EJERCICIOS);
    private MongoCollection<BasicDBObject> rankingEjercicios = Connection.getCollection().get(CollectionsDB.RANKING_EJERCICIOS);

    private MongoCollection<BasicDBObject> recursos = Connection.getCollection().get(CollectionsDB.RECURSOS);
    private MongoCollection<BasicDBObject> rankingRecursos = Connection.getCollection().get(CollectionsDB.RANKING_RECURSOS);
    ;

    private ModeloEstudiante estudiante;

    protected static GenericUserBasedRecommender recommenderEjercicios;
    //protected static RandomRecommender randomRecommender; //No funciona el recommender no se porque FGH
    protected static GenericUserBasedRecommender recommenderRecursos;

    protected static boolean updateModelExcercises = true;
    protected static boolean updateModelResources = true;

    private static Timer timer = new Timer();

    static {
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                updateModelExcercises = true;
                updateModelResources = true;
            }
        }, 0, 1800000);
    }

    public RankingManager(ModeloEstudiante estudiante) {
        this(estudiante, true, true);
    }

    public RankingManager(ModeloEstudiante estudiante, boolean updateExercises, boolean updateResources) {
        this.estudiante = estudiante;
        if (recommenderEjercicios == null && updateExercises) {
            updateDataModelEjercicios();
        }
        if (recommenderRecursos == null && updateResources) {
            updateDataModelRecursos();
        }
    }

    public String getRecommenderResources(int cantidad, boolean random) {
        return getRecommenders(rankingRecursos, recommenderRecursos, cantidad, random);
    }

    private int getIdLeccion(int idEjercicio) {
        return new Double(ejercicios.find(
                eq("id",idEjercicio)
        ).projection(
                new BasicDBObject("id",0)
                .append("idLeccion", 1)
        ).first().get("idLeccion").toString()).intValue();
    }

    public String getRecommenderResources(int cantidad, int idEjercicio) {
        String result = "[]";

        try {
            int idLeccionPrincipal = getIdLeccion(idEjercicio);
            
            List<BasicDBObject> listaRecursos = new ArrayList<>();
            
            recursos.find(
                    and(
                        in("id",getRecommendersItemsIDAsArray(recommenderRecursos, cantidad)),
                        eq("idLeccion",idLeccionPrincipal)
                    )
            ).forEach(new Block<BasicDBObject>(){

                @Override
                public void apply(BasicDBObject t) {
                    listaRecursos.add(t);
                }
                
            });

            listaRecursos.replaceAll((BasicDBObject object) -> {
                object.put("ranking", rankingRecursos.find(
                        and(
                                eq("idRecurso",object.get("id"))
                                ,eq("idAlumno", estudiante.getId())
                        )
                ).projection(
                        new BasicDBObject("_id",0)
                        .append("ranking", 1)
                ).first().get("ranking"));

                return object;
            });

            result = listaRecursos.toString();
        } catch (TasteException ex) {
            Logger.getLogger(RankingManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        return result;
    }

    /**
     * Metodo que obtiene recomendaciones de los ejercicios
     *
     * @param cantidad Cantidad de recomendaciones (No necesariamente devuelva
     * la cantidad pedida)
     * @param random Verdadero si necesita que genere un item aleatoriamente en
     * caso de no encontrar alguna recomendacion
     * @return JSON de los ejercicios
     */
    public String getRecommendersExercises(int cantidad, boolean random) {
        return getRecommenders(rankingEjercicios, recommenderEjercicios, cantidad, random);
    }

    protected Integer[] getRecommendersItemsIDAsArray(AbstractRecommender recommender, int cantidad) throws TasteException {
        List<Integer> listId = new ArrayList<>();

        for (RecommendedItem item : getRecommendersItems(recommender, cantidad)) {
            listId.add(new Long(item.getItemID()).intValue());
        }

        return listId.toArray(new Integer[listId.size()]);
    }

    protected List<RecommendedItem> getRecommendersItems(AbstractRecommender recommender, int cantidad) throws TasteException {
        return recommender.recommend(estudiante.getId(), cantidad);
    }

    protected String getRecommenders(MongoCollection<BasicDBObject> collection, AbstractRecommender recommender, int cantidad, boolean random) {
        String result = "{}";

        List<RecommendedItem> recommenders = new ArrayList<>();
        try {
            List<Long> array = new ArrayList();

            try {
                recommenders = getRecommendersItems(recommender, cantidad);//recommenderEjercicios.recommend(estudiante.getId(), cantidad); //5 Recomendaciones
            } catch (Exception ex) {
                System.out.println("El usuario no existe aun en el modelo de datos: " + estudiante.getId());
            }
            //Se agrega un item aleatorio...
            if (random && recommenders.size() < 1) { //RandomRecommender no funciona....

                double number = collection.count();

                DBObject object = collection.find().limit(1).skip((int) Math.floor(Math.random() * number)).first();

                array.add(
                        Math.round(Double.parseDouble(object.get("idEjercicio").toString()))
                );
            }

            //Las recomendaciones se transforman en json array
            for (RecommendedItem item : recommenders) {
                array.add(item.getItemID());
            }

            BasicDBList list = new BasicDBList();
            
            //Se crea un json array con los id obtenidos de los ejercicios
            ejercicios.find(
                    in("id", array)
            ).forEach(new Block<BasicDBObject>(){
                @Override
                public void apply(BasicDBObject t) {
                    list.add(t);
                }
            });
            
            result = list.toString();
        } catch (Exception ex) {
            JavaException.printMessage(ex, System.out);
        }

        return result;
    }

    public FastByIDMap buildDataModel(MongoCollection<BasicDBObject> rankings, String fieldNameUserId, String fieldNameItemId, String fieldNameValue) {
        //Obtenemos todos los ranking actuales y los almacenamos en el archivo csv
        FastByIDMap<PreferenceArray> userData = new FastByIDMap<>();
        
        if (rankings.count() > 0) {
            MongoCursor<BasicDBObject> cursor = rankings.find().iterator();

            List<Preference> preferences = new ArrayList<>();
            Long idLastUser = null;

            while (cursor.hasNext()) {
                DBObject object = cursor.next();
                Long idItem = new Double(object.get(fieldNameItemId).toString()).longValue();
                Long idCurrentUser = new Double(object.get(fieldNameUserId).toString()).longValue();
                Float value = new Float(object.get(fieldNameValue).toString());

                if (idLastUser == null || !idCurrentUser.equals(idLastUser)) {
                    if (preferences.size() > 0) {
                        userData.put(idLastUser, new GenericUserPreferenceArray(preferences));
                        preferences.clear();
                    }
                    idLastUser = idCurrentUser;
                }

                preferences.add(new GenericPreference(idLastUser, idItem, value)
                );

                if (!cursor.hasNext()) {
                    userData.put(idLastUser, new GenericUserPreferenceArray(preferences));
                }
            }

            System.out.println(userData);
        }

        return userData;
    }

    private void updateDataModelEjercicios() {
        try {
            if (updateModelExcercises) {
                //El archivo es pasado al dataModel
                DataModel dataModel = new GenericDataModel(buildDataModel(rankingEjercicios, "idAlumno", "idEjercicio", "ranking"));
                //Creamos la correlacion de pearson
                PearsonCorrelationSimilarity correlation = new PearsonCorrelationSimilarity(dataModel);
                UserNeighborhood neigh = new NearestNUserNeighborhood(5, correlation, dataModel);
                recommenderEjercicios = new GenericUserBasedRecommender(dataModel, neigh, correlation);
                //randomRecommender = new RandomRecommender(dataModel);

                updateModelExcercises = false;
            }
        } catch (Exception ex) {
            Logger.getLogger(RankingManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void updateDataModelRecursos() {
        try {
            if (updateModelResources) {
                //El archivo es pasado al dataModel
                DataModel dataModel = new GenericDataModel(buildDataModel(rankingRecursos, "idAlumno", "idRecurso", "ranking"));
                //Creamos la correlacion de pearson
                UserSimilarity correlation = new PearsonCorrelationSimilarity(dataModel);
                UserNeighborhood neigh = new NearestNUserNeighborhood(5, correlation, dataModel); //new ThresholdUserNeighborhood(0.1, correlation, dataModel);
                recommenderRecursos = new GenericUserBasedRecommender(dataModel, neigh, correlation);

                //csv.deleteOnExit();
                updateModelResources = false;
            }
        } catch (Exception ex) {
            Logger.getLogger(RankingManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean colocarRankingDefault() {
        boolean result = false;

        try {
            QueryBuilder query = QueryBuilder.start("id")
                    .notIn(rankingEjercicios.distinct("idEjercicio",
                                    QueryBuilder.start("idAlumno").
                                    is(estudiante.getId()).get()
                            ));

            //Obtenemos los id de ejercicios que no estan rankeados por el alumno
            //Se colocara un valor 2 de default para dicho ranking, de acuerdo a la escala de LIKERT
            List<DBObject> objects = new ArrayList<>();
                    
            ejercicios.find(
                    nin("id", 
                            rankingEjercicios.distinct("idEjercicio", eq("idAlumno",estudiante.getId())
                                    ,
                            )
                    )
            )

            if (!objects.isEmpty()) {
                List<DBObject> listObjects = new ArrayList<>();

                for (DBObject object : objects) {
                    listObjects.add(
                            BasicDBObjectBuilder.start()
                            .add("idEjercicio", object.get("id"))
                            .add("idAlumno", estudiante.getId())
                            .add("ranking", 2) //2 De acuerdo a la escala
                            .get()
                    );
                }

                rankingEjercicios.insert(listObjects);

                updateDataModelEjercicios();
            }

            //Mismo proceso para ranking de recursos
            QueryBuilder queryRecursos = QueryBuilder.start("id")
                    .notIn(rankingRecursos.distinct("idRecurso",
                                    QueryBuilder.start("idAlumno")
                                    .is(estudiante.getId()).get()));

            List<DBObject> idRecursos = recursos.find(queryRecursos.get()).toArray();

            if (!idRecursos.isEmpty()) {
                List<DBObject> listRankingRecursosSave = new ArrayList<>();

                for (DBObject object : idRecursos) {
                    listRankingRecursosSave.add(
                            BasicDBObjectBuilder.start()
                            .add("idRecurso", object.get("id"))
                            .add("idAlumno", estudiante.getId())
                            .add("ranking", 2) //2 De acuerdo a la escala
                            .get()
                    );
                }

                rankingRecursos.insert(listRankingRecursosSave);

                updateDataModelRecursos();
            }

            result = true;

        } catch (Exception ex) {
            System.err.println(ex);
        }

        return result;
    }
}
