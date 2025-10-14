package com.example.demo.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VehicleUtil {
    private static final Map<String, String> VEHICLE_ALIASES = new LinkedHashMap<>();

    static {
        VEHICLE_ALIASES.put("maxx", "Bolero Pickup (MaXX)");
        VEHICLE_ALIASES.put("bolero", "Bolero Pickup (MaXX)");
        VEHICLE_ALIASES.put("bolero pickup", "Bolero Pickup (MaXX)");
        VEHICLE_ALIASES.put("tata ace", "Tata Ace (Chhota Hathi)");
        VEHICLE_ALIASES.put("chhota hathi", "Tata Ace (Chhota Hathi)");
        VEHICLE_ALIASES.put("vikram tempo", "Vikram Tempo");
        VEHICLE_ALIASES.put("mini truck", "Mini Truck (Eicher Canter)");
        VEHICLE_ALIASES.put("container truck", "Container Truck");
        VEHICLE_ALIASES.put("open body truck", "Open Body Truck (6 wheeler)");
        VEHICLE_ALIASES.put("closed body truck", "Closed Body Truck");
        VEHICLE_ALIASES.put("flatbed truck", "Flatbed Truck");
        VEHICLE_ALIASES.put("e-rickshaw", "E-Rickshaw Loader (Tuk-Tuk)");
        VEHICLE_ALIASES.put("jcb", "JCB");
        VEHICLE_ALIASES.put("crane", "Crane");
        VEHICLE_ALIASES.put("tanker", "Tanker Truck");
        VEHICLE_ALIASES.put("garbage truck", "Garbage Truck");
        VEHICLE_ALIASES.put("tow-truck", "Tow-Truck");
        VEHICLE_ALIASES.put("packers", "Packer&Movers");
        VEHICLE_ALIASES.put("movers", "Packer&Movers");
        VEHICLE_ALIASES.put("parcel", "Parcel Delivery");
        VEHICLE_ALIASES.put("food truck", "Food trucks");
        VEHICLE_ALIASES.put("drink truck", "Drink trucks");
        VEHICLE_ALIASES.put("truck", "Trucks");
        VEHICLE_ALIASES.put("trucks", "Trucks");
    }

    private VehicleUtil() {}

    public static String normalizeType(String typeParam) {
        if (typeParam == null || typeParam.isBlank()) return null;
        String key = typeParam.trim().toLowerCase(Locale.ROOT);
        if (VEHICLE_ALIASES.containsKey(key)) return VEHICLE_ALIASES.get(key);
        for (String k : VEHICLE_ALIASES.keySet()) {
            if (key.contains(k)) return VEHICLE_ALIASES.get(k);
        }
        return toTitle(typeParam);
    }

    public static String toTitle(String str) {
        if (str == null || str.isBlank()) return str;
        String[] parts = str.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }
}


