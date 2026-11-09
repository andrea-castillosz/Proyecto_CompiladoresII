package microc;

import org.antlr.v4.runtime.Token;
import java.util.*;

public class TablaSimbolos {

    public enum Categoria { VARIABLE, FUNCION, PARAMETRO }


    public record Simbolo(String nombre, String tipo, Categoria categoria, int linea, int columna, List<String> paramTipos, boolean variadica, boolean definida) {

        public Simbolo(String nombre, String tipo, Categoria categoria, int linea, int columna) {
            this(nombre, tipo, categoria, linea, columna, null, false, false);
        }

        /** printf y scanf se crean con línea 0. */
        public boolean esPredefinida() { return linea == 0; }
    }

    public static class ErrorSemantico extends RuntimeException {
        public ErrorSemantico(int linea, int columna, String mensaje) {
            super("Error SEMANTICO [" + linea + ":" + (columna + 1) + "] " + mensaje);
        }
    }

    private final Deque<Map<String, Simbolo>> ambitos = new ArrayDeque<>();

    public TablaSimbolos() {
        entrarAmbito();
        ambitos.peek().put("printf", new Simbolo("printf", "int", Categoria.FUNCION, 0, 0, List.of(), true, true));
        ambitos.peek().put("scanf", new Simbolo("scanf", "int", Categoria.FUNCION, 0, 0, List.of(), true, true));
    }

    public void entrarAmbito() { ambitos.push(new HashMap<>()); }

    public void salirAmbito() { ambitos.pop(); }

    public void declarar(Simbolo s, Token token) {
        Map<String, Simbolo> actual = ambitos.peek();
        if (actual.containsKey(s.nombre())) {
            Simbolo previo = actual.get(s.nombre());
            String donde = previo.esPredefinida() ? "es una función predefinida" : "ya fue declarado en la línea " + previo.linea();
            throw new ErrorSemantico(token.getLine(), token.getCharPositionInLine(), "'" + s.nombre() + "' " + donde);
        }
        actual.put(s.nombre(), s);
    }

    /** Busca solo en el ámbito más interno (null si no está). */
    public Simbolo buscarEnAmbitoActual(String nombre) {
        return ambitos.peek().get(nombre);
    }

    /** Reemplaza un símbolo ya existente en el ámbito actual (prototipo -> definición). */
    public void reemplazarEnActual(Simbolo s) {
        ambitos.peek().put(s.nombre(), s);
    }

    /** Busca del ámbito más interno al global; null si no existe. */
    public Simbolo buscarSilencioso(String nombre) {
        for (Map<String, Simbolo> ambito : ambitos) {
            Simbolo s = ambito.get(nombre);
            if (s != null) return s;
        }
        return null;
    }

    public Simbolo buscar(String nombre, Token token) {
        Simbolo s = buscarSilencioso(nombre);
        if (s != null) return s;
        throw new ErrorSemantico(token.getLine(), token.getCharPositionInLine(),
                "'" + nombre + "' no ha sido declarado");
    }
}