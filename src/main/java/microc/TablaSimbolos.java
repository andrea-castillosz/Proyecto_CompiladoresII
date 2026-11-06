package microc;

import org.antlr.v4.runtime.Token;
import java.util.*;

public class TablaSimbolos {

    public enum Categoria { VARIABLE, FUNCION, PARAMETRO }

    public record Simbolo(String nombre, String tipo, Categoria categoria,
                          int linea, int columna) {}

    public static class ErrorSemantico extends RuntimeException {
        public ErrorSemantico(int linea, int columna, String mensaje) {
            super("Error SEMANTICO [" + linea + ":" + (columna + 1) + "] " + mensaje);
        }
    }


    private final Deque<Map<String, Simbolo>> ambitos = new ArrayDeque<>();

    public TablaSimbolos() {
        entrarAmbito();
        ambitos.peek().put("printf", new Simbolo("printf", "int", Categoria.FUNCION, 0, 0));
        ambitos.peek().put("scanf",  new Simbolo("scanf",  "int", Categoria.FUNCION, 0, 0));
    }

    public void entrarAmbito() { ambitos.push(new HashMap<>()); }

    public void salirAmbito() { ambitos.pop(); }

    public void declarar(Simbolo s, Token token) {
        Map<String, Simbolo> actual = ambitos.peek();
        if (actual.containsKey(s.nombre())) {
            Simbolo previo = actual.get(s.nombre());
            throw new ErrorSemantico(token.getLine(), token.getCharPositionInLine(),
                    "'" + s.nombre() + "' ya fue declarado en la línea " + previo.linea());
        }
        actual.put(s.nombre(), s);
    }

    public Simbolo buscar(String nombre, Token token) {
        for (Map<String, Simbolo> ambito : ambitos) {
            Simbolo s = ambito.get(nombre);
            if (s != null) return s;
        }
        throw new ErrorSemantico(token.getLine(), token.getCharPositionInLine(),
                "'" + nombre + "' no ha sido declarado");
    }
}