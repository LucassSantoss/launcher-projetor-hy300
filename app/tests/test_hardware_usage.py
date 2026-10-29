"""Valida as porcentagens e as amostras reais do calculador, sem hardware Android."""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEST = r'''package com.mlab.projetor;
public class HardwareUsageTest {
    static void check(int actual, int expected) {
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        long gb = 1024L * 1024 * 1024;
        check(HardwareUsage.percent(gb, 2 * gb), 50);
        check(HardwareUsage.percent(0, gb), 100);
        check(HardwareUsage.percent(gb, gb), 0);
        check(HardwareUsage.percent(1, 3), 67);
        check(HardwareUsage.percent(0, 0), -1);
        check(HardwareUsage.percent(0, -1), -1);
        check(HardwareUsage.percent(-1, 100), 100);
        check(HardwareUsage.percent(101, 100), 0);
        HardwareUsage cpu = new HardwareUsage();
        check(cpu.sampleCpu("cpu 100 0 0 100 0 0 0 0 0 0"), -1);
        // +40 user, +10 system, +30 idle, +20 iowait: 50% em uso.
        // guest e guest_nice sao excluidos, evitando contar duas vezes.
        check(cpu.sampleCpu("cpu 140 0 10 130 20 0 0 0 40 20"), 50);
        check(cpu.sampleCpu("cpu 140 0 10 230 20 0 0 0 40 20"), 0);
        check(cpu.sampleCpu("cpu 240 0 10 230 20 0 0 0 40 20"), 100);
        check(cpu.sampleCpu("cpu 240 0 10 230 20 0 0 0 40 20"), -1);
        cpu.resetCpu();
        check(cpu.sampleCpu("cpu 240 0 10 230 20 0 0 0"), -1);
        check(cpu.sampleCpu("cpu 0 0 0 0 0 0 0 0"), -1);
        check(cpu.sampleCpu("cpu 25 0 0 75 0 0 0 0"), 25);
        check(cpu.sampleCpu("cpu invalid 0 0 0 0 0 0 0"), -1);
        check(cpu.sampleCpu(null), -1);
        check(cpu.sampleCpu("cpu0 0 0 0 0 0 0 0 0"), -1);
        check(cpu.sampleCpu("cpu 1 2"), -1);
        check(cpu.sampleCpu("cpu -1 0 0 0 0 0 0 0"), -1);
        check(cpu.sampleCpu("cpu 0 0 0 0 0 0 0 0"), -1);
        check(cpu.sampleCpu("cpu 25 0 0 75 0 0 0 0"), 25);
        System.out.println("Hardware: porcentagens, CPU, reinicio e leituras invalidas OK");
    }
}'''
with tempfile.TemporaryDirectory() as tmp:
    test = pathlib.Path(tmp) / 'HardwareUsageTest.java'
    test.write_text(TEST)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', tmp,
                    str(ROOT / 'src/com/mlab/projetor/HardwareUsage.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', tmp, 'com.mlab.projetor.HardwareUsageTest'], check=True)
