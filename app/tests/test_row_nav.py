"""Regras de navegacao da tela inicial em listas (RowNav), sem Android."""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEST = '''package com.mlab.projetor;
public class RowNavTest {
 static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
 public static void main(String[] a) {
  int[] sizes = {6, 0, 4, 3, 8};
  // Cima/baixo pulam listas vazias e param nas bordas.
  check(RowNav.nextRow(sizes, 0, 1) == 2, "desce pulando a lista vazia");
  check(RowNav.nextRow(sizes, 2, -1) == 0, "sobe pulando a lista vazia");
  check(RowNav.nextRow(sizes, 0, -1) == -1, "primeira lista nao sobe");
  check(RowNav.nextRow(sizes, 4, 1) == -1, "ultima lista nao desce");
  check(RowNav.nextRow(new int[]{0, 0}, 0, 1) == -1, "sem listas com itens");
  // Volta ao ultimo item usado, limitado ao tamanho da lista.
  check(RowNav.clamp(5, 3) == 2, "limita ao ultimo item");
  check(RowNav.clamp(-1, 3) == 0, "nunca negativo");
  check(RowNav.clamp(1, 3) == 1, "mantem o ultimo usado");
  check(RowNav.clamp(0, 0) == -1, "lista vazia");
  // Esquerda/direita param nas bordas.
  check(RowNav.nextItem(4, 0, -1) == -1, "borda esquerda");
  check(RowNav.nextItem(4, 3, 1) == -1, "borda direita");
  check(RowNav.nextItem(4, 1, 1) == 2, "anda para a direita");
  check(RowNav.nextItem(4, 1, -1) == 0, "anda para a esquerda");
  // Rolagem horizontal: so rola quando o item sairia da tela, com margem.
  check(RowNav.scrollToShow(0, 1000, 56, 268, 56) == 0, "primeiro item nao rola");
  check(RowNav.scrollToShow(0, 1000, 500, 712, 56) == 0, "item visivel nao rola");
  check(RowNav.scrollToShow(0, 1000, 900, 1112, 56) == 168, "rola para mostrar o item da direita");
  check(RowNav.scrollToShow(400, 1000, 300, 512, 56) == 244, "rola de volta para a esquerda");
  check(RowNav.scrollToShow(400, 1000, 56, 268, 56) == 0, "volta ao inicio");
  System.out.println("Navegacao da tela inicial OK");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
    src = pathlib.Path(tmp) / 'com/mlab/projetor/RowNavTest.java'
    src.parent.mkdir(parents=True)
    src.write_text(TEST)
    subprocess.run(['javac', '-d', tmp, str(src), str(ROOT / 'src/com/mlab/projetor/RowNav.java')], check=True)
    subprocess.run(['java', '-cp', tmp, 'com.mlab.projetor.RowNavTest'], check=True)
