package com.mlab.projetor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/** Porcentagens de uso; -1 indica que ainda nao existe uma leitura valida. */
final class HardwareUsage {
    private long previousTotal = -1, previousIdle;

    static int percent(long available, long total) {
        if (total <= 0) return -1;
        return (int) Math.max(0, Math.min(100,
                Math.round(100.0 * (1.0 - (double) available / total))));
    }

    int readCpu() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            return sampleCpu(reader.readLine());
        } catch (IOException | SecurityException e) {
            resetCpu();
            return -1;
        }
    }

    int sampleCpu(String line) {
        try {
            long[] counters = cpuCounters(line);
            long totalDelta = counters[0] - previousTotal;
            long idleDelta = counters[1] - previousIdle;
            boolean valid = previousTotal >= 0 && totalDelta > 0 && idleDelta >= 0;
            previousTotal = counters[0];
            previousIdle = counters[1];
            return valid ? percent(idleDelta, totalDelta) : -1;
        } catch (IllegalArgumentException e) {
            resetCpu();
            return -1;
        }
    }

    private static long[] cpuCounters(String line) {
        if (line == null) throw new IllegalArgumentException();
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 9 || !"cpu".equals(fields[0])) throw new IllegalArgumentException();
        long total = 0, idle = 0;
        // guest e guest_nice ja estao incluidos em user e nice; somar so os oito primeiros.
        for (int i = 1; i <= 8; i++) {
            long value = Long.parseLong(fields[i]);
            if (value < 0) throw new IllegalArgumentException();
            total += value;
            if (i == 4 || i == 5) idle += value;
        }
        return new long[]{total, idle};
    }

    void resetCpu() {
        previousTotal = -1;
        previousIdle = 0;
    }
}
