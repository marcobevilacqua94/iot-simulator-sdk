package org.iot_simulator;

import com.couchbase.client.java.json.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class SensorDocGenerator implements DocGenerator {

    private final Random rand = new Random();
    public JsonObject generateDoc(long millis, Double lastValue, long sensor, double temperature){
        return JsonObject.create()
                .put("ts_start", millis)
                .put("ts_end", millis)
                .put("device", sensor)
                .put("ts_data", List.of(Arrays.asList(millis, temperature)));
    }


    public double generateTemperature(Double lastValue){
        double newValue;
        if(lastValue == null){
            return BigDecimal.valueOf(rand.nextDouble() * 40 - 10).setScale(3, RoundingMode.FLOOR).doubleValue();
        } else {
            newValue = BigDecimal.valueOf(lastValue + rand.nextDouble() * 2 - 1).setScale(3, RoundingMode.FLOOR).doubleValue();
        }

        if(newValue > 50){
            return lastValue;
        }
        if(newValue < -20){
            return lastValue;
        }

        return newValue;
    }


}
