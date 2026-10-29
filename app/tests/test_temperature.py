"""Executa a leitura real contra sensores locais controlados."""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEST = '''package com.mlab.projetor;
import java.io.File;
import java.nio.file.Files;
public class TemperatureTest {
 static File root;
 static void zone(String name,String type,String value) throws Exception {
  File dir=new File(root,name); dir.mkdirs();
  Files.write(new File(dir,"type").toPath(),type.getBytes("UTF-8"));
  Files.write(new File(dir,"temp").toPath(),value.getBytes("UTF-8"));
 }
 static void check(int expected) {
  int actual=Temperature.readHighest(root);
  if(actual!=expected) throw new AssertionError(actual+" != "+expected);
 }
 public static void main(String[] args) throws Exception {
  root=new File(args[0]);
  check(-1);
  zone("thermal_zone0","cpu_thermal_zone","66933"); check(66933);
  zone("thermal_zone1","gpu_thermal_zone","64320"); check(66933);
  zone("thermal_zone2","virtual-ac","150000"); check(66933);
  zone("thermal_zone1","gpu_thermal_zone","71000"); check(71000);
  zone("thermal_zone0","cpu_thermal_zone","80000"); check(80000);
  zone("thermal_zone0","cpu_thermal_zone","invalid"); check(71000);
  zone("thermal_zone1","gpu_thermal_zone","-1"); check(-1);
  zone("thermal_zone1","gpu_thermal_zone","999999"); check(-1);
  zone("thermal_zone1","gpu_thermal_zone",""); check(-1);
  zone("thermal_zone0","cpu_thermal_zone","0"); check(0);
  new File(root,"thermal_zone0/temp").delete(); check(-1);
  zone("thermal_zone1","GPU","115000"); check(115000);
  if(Temperature.readHighest(new File(root,"missing"))!=-1) throw new AssertionError();
  System.out.println("Temperatura: maior CPU/GPU, troca de sensor e falhas OK");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
    folder = pathlib.Path(tmp)
    test = folder / 'TemperatureTest.java'
    test.write_text(TEST)
    sensors = folder / 'sensors'
    sensors.mkdir()
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', tmp,
                    str(ROOT / 'src/com/mlab/projetor/Temperature.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', tmp, 'com.mlab.projetor.TemperatureTest', str(sensors)], check=True)
