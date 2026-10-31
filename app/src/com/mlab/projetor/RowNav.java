package com.mlab.projetor;

/**
 * Regras de navegacao da tela inicial em listas (sem Android, para testar no PC).
 * Setas para cima/baixo trocam de lista e voltam ao ultimo item usado nela, como no Google TV;
 * nas bordas o foco fica onde esta (nunca escapa para outra parte da tela).
 */
final class RowNav {
    private RowNav() {}

    /** Proxima lista com itens na direcao (+1 desce, -1 sobe); -1 se nao houver. */
    static int nextRow(int[] sizes, int row, int dir) {
        for (int r = row + dir; r >= 0 && r < sizes.length; r += dir) {
            if (sizes[r] > 0) return r;
        }
        return -1;
    }

    /** Item a focar numa lista: o ultimo usado, limitado ao tamanho atual; -1 se vazia. */
    static int clamp(int index, int size) {
        if (size <= 0) return -1;
        return Math.max(0, Math.min(index, size - 1));
    }

    /** Item vizinho na mesma lista (+1 direita, -1 esquerda); -1 na borda. */
    static int nextItem(int size, int index, int dir) {
        int n = index + dir;
        return n >= 0 && n < size ? n : -1;
    }

    /**
     * Rolagem horizontal que deixa o item [left, right) inteiro na tela com uma margem;
     * so rola quando o item sairia da area visivel.
     */
    static int scrollToShow(int scrollX, int width, int left, int right, int margin) {
        int target = scrollX;
        if (right + margin > scrollX + width) target = right + margin - width;
        if (left - margin < target) target = left - margin;
        return Math.max(0, target);
    }
}
