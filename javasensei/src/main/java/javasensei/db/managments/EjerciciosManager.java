package javasensei.db.managments;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import javasensei.db.Connection;

/**
 *
 * @author Rock
 */
public class EjerciciosManager {

    private final MongoCollection<BasicDBObject> rankingEjerciciosCollection = Connection.getCollection().get(CollectionsDB.RANKING_EJERCICIOS);

    public Integer getRankingEjercicio(Integer idEjercicio, Long idAlumno) {
        Integer ranking = 2; //Default

        BasicDBObject object = rankingEjerciciosCollection.find(
                new BasicDBObject()
                .append("idEjercicio", idEjercicio)
                .append("idAlumno",idAlumno)
        ).first();

        if (object != null) {
            ranking = new Double(object.get("ranking").toString()).intValue();
        }

        return ranking;
    }

    public Boolean setRankingEjercicio(Integer idEjercicio, Long idAlumno, Integer ranking) {
        boolean result = false;

        try {
            rankingEjerciciosCollection.updateOne(
                    new BasicDBObject()
                    .append("idEjercicio", idEjercicio)
                    .append("idAlumno", idAlumno)
                    , new BasicDBObject()
                    .append("$set",new BasicDBObject("ranking",ranking)
                )
            );

            result = true;
        } catch (Exception ex) {
            System.err.println(ex);
        }

        return result;
    }
}
