package juna;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Juna {

    private static final Logger LOGGER = LoggerFactory.getLogger(Juna.class);

    private final String servername;
    private final String fromEmail;
    
    private Map<String, Map<String, Object>> stations = new HashMap<>();
    private Map<String, Map<String, Object>> causeCategoryCodes = new HashMap<>();
    private Map<String, Map<String, Object>> detailedCategoryCodes = new HashMap<>();
    private Map<String, Map<String, Object>> thirdCategoryCodes = new HashMap<>();
    
    public Juna(String servername, String fromEmail) {
        this.servername = servername;
        this.fromEmail = fromEmail;
    }

    public String getServername() {
        return servername;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void addStations(List<Map<String, Object>> stationss) {
        for (Map<String, Object> station : stationss) {
            String stationShortCode = (String) station.get("stationShortCode");
            stations.put(stationShortCode, station);
        }
    }

    public String getStationNameByShortCode(String shortCode) {
        String stationName = shortCode;
        Map<String, Object> stat = stations.get(shortCode);
        if (stat != null) {
            String possibleStationName = (String) stat.get("stationName");
            if (possibleStationName != null) {
                stationName = possibleStationName;
            }
        }
        return stationName;
    }

    public void addCauseCategoryCodes(List<Map<String, Object>> causeCategoryCodes) {
        for (Map<String, Object> causeCategoryCode : causeCategoryCodes) {
            String categoryCode = (String) causeCategoryCode.get("categoryCode");
            this.causeCategoryCodes.put(categoryCode, causeCategoryCode);
        }
    }

    public void addDetailedCauseCategoryCodes(List<Map<String, Object>> causeCategoryCodes) {
        for (Map<String, Object> causeCategoryCode : causeCategoryCodes) {
            String detailedCategoryCode = (String) causeCategoryCode.get("detailedCategoryCode");
            this.detailedCategoryCodes.put(detailedCategoryCode, causeCategoryCode);
        }
    }

    public void addThirdCauseCategoryCodes(List<Map<String, Object>> causeCategoryCodes) {
        for (Map<String, Object> causeCategoryCode : causeCategoryCodes) {
            String thirdCategoryCode = (String) causeCategoryCode.get("thirdCategoryCode");
            this.thirdCategoryCodes.put(thirdCategoryCode, causeCategoryCode);
        }
    }

    public String resolveCauseCodesToHumanMessage(List<String> causes) {
        if (causes == null || causes.size() == 0)
            return "Tuntematon / Ei julkaistu";

        try {
            String categoryCode = causes.get(0);
            String detailedCategoryCode = null;
            String thirdCategoryCode = null;
            if (causes.size() > 1)
                detailedCategoryCode = causes.get(1);
            if (causes.size() > 2)
                thirdCategoryCode = causes.get(2);
            
            StringBuilder syy = new StringBuilder();
            Map<String, Object> causeCategoryCode = causeCategoryCodes.get(categoryCode);
            if (causeCategoryCode != null) {
                Object categoryName = causeCategoryCode.get("categoryName");
                if (categoryName != null) {
                    syy.append(categoryName);
                }
            }
            if (detailedCategoryCode != null) {
                Map<String, Object> detailedCauseCategoryCode = detailedCategoryCodes.get(detailedCategoryCode);
                if (detailedCauseCategoryCode != null) {
                    Object detailedCategoryName = detailedCauseCategoryCode.get("detailedCategoryName");
                    if (detailedCategoryName != null) {
                        syy.append(" : ").append(detailedCategoryName);
                    }
                }
            }
            if (thirdCategoryCode != null) {
                Map<String, Object> thirdCauseCategoryCode = thirdCategoryCodes.get(thirdCategoryCode);
                if (thirdCauseCategoryCode != null) {
                    Object thirdCategoryName = thirdCauseCategoryCode.get("thirdCategoryName");
                    if (thirdCategoryName != null) {
                        syy.append(" : ").append(thirdCategoryName);
                    }
                }
            }
            return syy.toString();
        } catch (Throwable t) {
            LOGGER.error("Could not resolve causes to human {}", causes, t);
            
            return "Tuntematon / Ei julkaistu";
        }
    }
}
