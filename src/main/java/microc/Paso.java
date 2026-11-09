package microc;


public record Paso(Op op, String arg1, String arg2, String resultado) {

    public enum Op {
        // aritmética y asignación
        ADD, SUB, MUL, DIV, NEG, ASSIGN,
        // comparaciones
        EQ, NE, LT, LE, GT, GE,
        // control de flujo
        LABEL, JUMP, JUMP_FALSE,
        // funciones
        FUNC_BEGIN, FUNC_END, PARAM, CALL, RETURN
    }

    @Override
    public String toString() {
        return "(" + op + ", " + (arg1 == null ? "_" : arg1) + ", " + (arg2 == null ? "_" : arg2) + ", " + (resultado == null ? "_" : resultado) + ")";
    }
}