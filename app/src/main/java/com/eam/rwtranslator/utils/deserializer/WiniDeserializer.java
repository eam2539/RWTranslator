package com.eam.rwtranslator.utils.deserializer;

import androidx.annotation.Keep;

import com.google.gson.JsonElement;
import java.io.File;
import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import java.util.Map;
import org.ini4j.Wini;
import org.ini4j.Profile;
import com.eam.rwtranslator.utils.ini.RWIniFileLoader;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializer;

@Keep
public class WiniDeserializer implements JsonDeserializer<Wini> {
    @Override
    public Wini deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        try {
            JsonObject jsonObject = json.getAsJsonObject();
            Wini wini = RWIniFileLoader.createEmpty();
            
            // 处理配置文件路径
            if (jsonObject.has("configFile")) {
                String filePath = jsonObject.get("configFile").getAsString();
                wini.setFile(new File(filePath));
                jsonObject.remove("configFile");
            }
            
            // 反序列化各个section
            for (Map.Entry<String, JsonElement> sectionEntry : jsonObject.entrySet()) {
                String sectionName = sectionEntry.getKey();
                JsonElement sectionElement = sectionEntry.getValue();
                
                if (sectionElement.isJsonObject()) {
                    JsonObject sectionObject = sectionElement.getAsJsonObject();
                    Profile.Section section = wini.add(sectionName);
                    
                    for (Map.Entry<String, JsonElement> optionEntry : sectionObject.entrySet()) {
                        String optionName = optionEntry.getKey();
                        JsonElement optionElement = optionEntry.getValue();
                        
                        if (optionElement.isJsonPrimitive()) {
                            String optionValue = optionElement.getAsString();
                            section.add(optionName, optionValue);
                        }
                    }
                }
            }
            
            RWIniFileLoader.ensureDocument(wini);
            return wini;
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize Wini object", e);
        }
    }
}