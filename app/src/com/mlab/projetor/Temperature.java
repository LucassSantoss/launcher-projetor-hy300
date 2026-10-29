package com.mlab.projetor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Temperatura mais alta entre CPU e GPU, em milesimos de grau Celsius. */
final class Temperature {
    // Escala visual escolhida pelo usuario, independente dos limites termicos do firmware.
    static final int GRAPH_SCALE = 100_000;

    private Temperature() {}

    static int readHighest() {
        return readHighest(new File("/sys/class/thermal"));
    }

    static int readHighest(File root) {
        File[] zones;
        try {
            zones = root.listFiles();
        } catch (SecurityException e) {
            return -1;
        }
        if (zones == null) return -1;
        int highest = -1;
        for (File zone : zones) {
            if (!zone.getName().startsWith("thermal_zone")) continue;
            String type = readLine(new File(zone, "type")).toLowerCase(Locale.ROOT);
            if (!type.contains("cpu") && !type.contains("gpu")) continue;
            highest = Math.max(highest, readValue(new File(zone, "temp")));
        }
        return highest;
    }

    private static int readValue(File file) {
        try {
            int value = Integer.parseInt(readLine(file));
            return value >= 0 && value <= 200_000 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String value = reader.readLine();
            return value == null ? "" : value.trim();
        } catch (IOException | SecurityException e) {
            return "";
        }
    }
}
