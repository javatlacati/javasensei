/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javasensei.db.managments;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.util.ArrayList;
import java.util.List;
import javasensei.db.Connection;
import javasensei.dbo.bitacora.BitacoraEjerciciosDBO;

/**
 *
 * @author PosgradoMCC
 */
public class BitacoraEjerciciosManager {

    private final MongoCollection bitacoraEjercicios = Connection.getCollection().get(CollectionsDB.BITACORA_EJERCICIOS);

    public String guardarBitacoras(String logBitacoras) {
        JsonParser parser = new JsonParser();
        JsonArray array = parser.parse(logBitacoras).getAsJsonArray();
        List<DBObject> bitacoras = new ArrayList<>();

        synchronized (bitacoraEjercicios) {

            long sesionId = 1;

            //Sesion id
            MongoCursor<BasicDBObject> cursor = bitacoraEjercicios.find().sort(
                    new BasicDBObject()
                    .append("sesionId", -1)
            ).limit(1)
                    .iterator();

            if (cursor != null && cursor.hasNext()) {
                sesionId = new Long(cursor.next().get("sesionId").toString()) + 1;
            }

            if (array.size() > 0) {
                for (JsonElement object : array) {
                    bitacoras.add(
                            BitacoraEjerciciosDBO.createDbObject(
                                    object.getAsJsonObject(),
                                    sesionId
                            )
                    );
                }

                bitacoraEjercicios.insertMany(bitacoras);
            }

            return bitacoras.toString();
        }
    }
}
