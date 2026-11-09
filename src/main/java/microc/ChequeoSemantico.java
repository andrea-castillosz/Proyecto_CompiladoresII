package microc;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import microc.TablaSimbolos.Categoria;
import microc.TablaSimbolos.ErrorSemantico;
import microc.TablaSimbolos.Simbolo;

import java.math.BigInteger;
import java.util.*;


public class ChequeoSemantico extends MicroCBaseVisitor<String> {

    private static final int MAX_ID = 12;

    private final TablaSimbolos tabla = new TablaSimbolos();
    private String tipoFuncionActual = null;
    private int profundidadWhile = 0;

    // ------------------------------------------------------------ utilidades

    private ErrorSemantico error(Token t, String mensaje) {
        return new ErrorSemantico(t.getLine(), t.getCharPositionInLine(), mensaje);
    }

    private static boolean numerico(String t) {
        return "int".equals(t) || "char".equals(t);
    }

    /** int y char son intercambiables (decisión de diseño, ver informe). */
    private static boolean compatibles(String a, String b) {
        return a.equals(b) || (numerico(a) && numerico(b));
    }

    private void verificarLongitud(Token t) {
        if (t.getText().length() > MAX_ID) {
            throw error(t, "el identificador '" + t.getText()
                    + "' excede el máximo de " + MAX_ID + " caracteres");
        }
    }

    private String operarNumerico(Token t, String a, String b, String op) {
        if (!numerico(a) || !numerico(b)) {
            throw error(t, "operandos inválidos para '" + op + "': '" + a + "' y '" + b + "'");
        }
        return "int";
    }

    private void exigirCondicion(MicroCParser.ExpressionContext e) {
        String tipo = visit(e);
        if (!numerico(tipo)) {
            throw error(e.start, "la condición debe ser de tipo int o char, no '" + tipo + "'");
        }
    }

    private boolean mismaFirma(Simbolo previo, String ret, List<String> tipos, boolean variadica) {
        return previo.tipo().equals(ret)
                && Objects.equals(previo.paramTipos(), tipos)
                && previo.variadica() == variadica;
    }

    // ------------------------------------------------------ nivel superior

    @Override
    public String visitTranslationUnit(MicroCParser.TranslationUnitContext ctx) {
        visitChildren(ctx);
        Simbolo m = tabla.buscarSilencioso("main");
        if (m == null || m.categoria() != Categoria.FUNCION || !m.definida()) {
            throw error(ctx.stop, "el programa debe definir la función 'main'");
        }
        return null;
    }

    @Override
    public String visitVariableDeclaration(MicroCParser.VariableDeclarationContext ctx) {
        String tipo = ctx.type().getText();
        for (TerminalNode id : ctx.identifierList().ID()) {
            Token t = id.getSymbol();
            verificarLongitud(t);
            tabla.declarar(new Simbolo(t.getText(), tipo, Categoria.VARIABLE, t.getLine(), t.getCharPositionInLine()), t);
        }
        return null;
    }

    @Override
    public String visitFunctionDeclaration(MicroCParser.FunctionDeclarationContext ctx) {
        Token nombre = ctx.ID().getSymbol();
        verificarLongitud(nombre);
        String ret = ctx.returnType().getText();

        List<String> tipos = new ArrayList<>();
        var pd = ctx.parametersDecl();
        boolean variadica = pd.getText().endsWith("...");
        if (pd.parameterDeclList() != null) {
            for (var spec : pd.parameterDeclList().parameterDeclSpec()) {
                tipos.add(spec.type() == null ? "char*" : spec.type().getText());
            }
        }

        Simbolo previo = tabla.buscarEnAmbitoActual(nombre.getText());
        if (previo != null) {
            if (previo.esPredefinida()) return null; // p. ej. un prototipo de printf
            if (previo.categoria() != Categoria.FUNCION || !mismaFirma(previo, ret, tipos, variadica)) {
                throw error(nombre, "'" + nombre.getText() + "' no coincide con su declaración previa de la línea " + previo.linea());
            }
            return null; // redeclaración idéntica: permitida
        }
        tabla.declarar(new Simbolo(nombre.getText(), ret, Categoria.FUNCION, nombre.getLine(), nombre.getCharPositionInLine(), tipos, variadica, false), nombre);
        return null;
    }

    @Override
    public String visitFunctionDefinition(MicroCParser.FunctionDefinitionContext ctx) {
        var header = ctx.functionDefHeader();
        Token nombre = header.ID().getSymbol();
        verificarLongitud(nombre);
        String ret = header.returnType().getText();

        List<String> tipos = new ArrayList<>();
        var lista = header.parametersDef().parameterDefList();
        if (lista != null) {
            for (var t : lista.type()) tipos.add(t.getText());
        }

        if (nombre.getText().equals("main") && !tipos.isEmpty()) {
            throw error(nombre, "'main' no debe tener parámetros");
        }

        Simbolo nuevo = new Simbolo(nombre.getText(), ret, Categoria.FUNCION,
                nombre.getLine(), nombre.getCharPositionInLine(), tipos, false, true);

        Simbolo previo = tabla.buscarEnAmbitoActual(nombre.getText());
        if (previo == null) {
            tabla.declarar(nuevo, nombre);
        } else {
            if (previo.esPredefinida()) {
                throw error(nombre, "'" + nombre.getText() + "' es una función predefinida y no puede redefinirse");
            }
            if (previo.categoria() != Categoria.FUNCION) {
                throw error(nombre, "'" + nombre.getText() + "' ya fue declarado en la línea " + previo.linea());
            }
            if (!mismaFirma(previo, ret, tipos, false)) {
                throw error(nombre, "la definición de '" + nombre.getText() + "' no coincide con su prototipo de la línea " + previo.linea());
            }
            if (previo.definida()) {
                throw error(nombre, "la función '" + nombre.getText() + "' ya fue definida");
            }
            tabla.reemplazarEnActual(nuevo);
        }

        tabla.entrarAmbito();
        if (lista != null) {
            for (int i = 0; i < lista.ID().size(); i++) {
                Token p = lista.ID(i).getSymbol();
                verificarLongitud(p);
                tabla.declarar(new Simbolo(p.getText(), lista.type(i).getText(), Categoria.PARAMETRO, p.getLine(), p.getCharPositionInLine()), p);
            }
        }

        String anterior = tipoFuncionActual;
        tipoFuncionActual = ret;
        visit(ctx.functionBody());
        tipoFuncionActual = anterior;

        tabla.salirAmbito();
        return null;
    }

    // ------------------------------------------------------------ statements

    @Override
    public String visitExprStmt(MicroCParser.ExprStmtContext ctx) {
        visit(ctx.expression());
        return null;
    }

    @Override
    public String visitReturnVoid(MicroCParser.ReturnVoidContext ctx) {
        if (!"void".equals(tipoFuncionActual)) {
            throw error(ctx.start, "la función retorna '" + tipoFuncionActual + "'; 'return' debe llevar un valor");
        }
        return null;
    }

    @Override
    public String visitReturnValue(MicroCParser.ReturnValueContext ctx) {
        if ("void".equals(tipoFuncionActual)) {
            throw error(ctx.start, "una función 'void' no puede retornar un valor");
        }
        String tipo = visit(ctx.expression());
        if (!compatibles(tipoFuncionActual, tipo)) {
            throw error(ctx.expression().start, "se retorna '" + tipo + "' pero la función retorna '" + tipoFuncionActual + "'");
        }
        return null;
    }

    @Override
    public String visitWhile(MicroCParser.WhileContext ctx) {
        exigirCondicion(ctx.expression());
        profundidadWhile++;
        visit(ctx.statement());
        profundidadWhile--;
        return null;
    }

    @Override
    public String visitIfNoElse(MicroCParser.IfNoElseContext ctx) {
        exigirCondicion(ctx.expression());
        visit(ctx.statement());
        return null;
    }

    @Override
    public String visitIfElse(MicroCParser.IfElseContext ctx) {
        exigirCondicion(ctx.expression());
        visit(ctx.statement(0));
        visit(ctx.statement(1));
        return null;
    }

    @Override
    public String visitBlock(MicroCParser.BlockContext ctx) {
        visit(ctx.statementList());
        return null;
    }

    @Override
    public String visitBreak(MicroCParser.BreakContext ctx) {
        if (profundidadWhile == 0) {
            throw error(ctx.start, "'break' solo puede usarse dentro de un 'while'");
        }
        return null;
    }

    @Override
    public String visitContinue(MicroCParser.ContinueContext ctx) {
        if (profundidadWhile == 0) {
            throw error(ctx.start, "'continue' solo puede usarse dentro de un 'while'");
        }
        return null;
    }

    // ----------------------------------------------------------- expresiones

    @Override
    public String visitAssign(MicroCParser.AssignContext ctx) {
        var izq = ctx.equalityExpression(0);
        var der = ctx.equalityExpression(1);

        if (!izq.getText().matches("[A-Za-z_][A-Za-z_0-9]*")) {
            throw error(izq.start, "el lado izquierdo de '=' debe ser una variable");
        }
        Simbolo s = tabla.buscar(izq.getText(), izq.start);
        if (s.categoria() == Categoria.FUNCION) {
            throw error(izq.start, "no se puede asignar a la función '" + s.nombre() + "'");
        }
        String tDer = visit(der);
        if (!compatibles(s.tipo(), tDer)) {
            throw error(der.start, "no se puede asignar un valor de tipo '" + tDer + "' a '" + s.nombre() + "' de tipo '" + s.tipo() + "'");
        }
        return s.tipo();
    }

    @Override
    public String visitExprEq(MicroCParser.ExprEqContext ctx) {
        return visit(ctx.equalityExpression());
    }

    @Override
    public String visitEquality(MicroCParser.EqualityContext ctx) {
        String a = visit(ctx.relationalExpression(0));
        String b = visit(ctx.relationalExpression(1));
        return operarNumerico(ctx.eqOp().start, a, b, ctx.eqOp().getText());
    }

    @Override
    public String visitEqPass(MicroCParser.EqPassContext ctx) {
        return visit(ctx.relationalExpression());
    }

    @Override
    public String visitRelational(MicroCParser.RelationalContext ctx) {
        String a = visit(ctx.simpleExpression(0));
        String b = visit(ctx.simpleExpression(1));
        return operarNumerico(ctx.relOp().start, a, b, ctx.relOp().getText());
    }

    @Override
    public String visitRelPass(MicroCParser.RelPassContext ctx) {
        return visit(ctx.simpleExpression());
    }

    @Override
    public String visitAddSub(MicroCParser.AddSubContext ctx) {
        String a = visit(ctx.simpleExpression());
        String b = visit(ctx.term());
        return operarNumerico(ctx.addOp().start, a, b, ctx.addOp().getText());
    }

    @Override
    public String visitSimplePass(MicroCParser.SimplePassContext ctx) {
        return visit(ctx.term());
    }

    @Override
    public String visitMulDiv(MicroCParser.MulDivContext ctx) {
        String a = visit(ctx.term());
        String b = visit(ctx.factor());
        return operarNumerico(ctx.mulOp().start, a, b, ctx.mulOp().getText());
    }

    @Override
    public String visitTermPass(MicroCParser.TermPassContext ctx) {
        return visit(ctx.factor());
    }

    @Override
    public String visitConstFactor(MicroCParser.ConstFactorContext ctx) {
        return visit(ctx.constant());
    }

    @Override
    public String visitParenFactor(MicroCParser.ParenFactorContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public String visitUnaryFactor(MicroCParser.UnaryFactorContext ctx) {
        String t = visit(ctx.factor());
        if (!numerico(t)) {
            throw error(ctx.addOp().start, "el operador unario '" + ctx.addOp().getText() + "' requiere int o char, no '" + t + "'");
        }
        return "int";
    }

    @Override
    public String visitIdFactor(MicroCParser.IdFactorContext ctx) {
        Token t = ctx.ID().getSymbol();
        Simbolo s = tabla.buscar(t.getText(), t);
        if (s.categoria() == Categoria.FUNCION) {
            throw error(t, "'" + t.getText() + "' es una función; se debe llamar con paréntesis");
        }
        return s.tipo();
    }

    @Override
    public String visitStringConst(MicroCParser.StringConstContext ctx) {
        return "char*";
    }

    @Override
    public String visitCharConst(MicroCParser.CharConstContext ctx) {
        return "char";
    }

    @Override
    public String visitNumConst(MicroCParser.NumConstContext ctx) {
        Token t = ctx.start;
        String txt = ctx.getText();
        if (txt.startsWith("0x") || txt.startsWith("0X")) {
            BigInteger v = new BigInteger(txt.substring(2), 16);
            if (v.compareTo(BigInteger.valueOf(0xFFFF)) > 0) {
                throw error(t, "la constante hexadecimal " + txt + " excede los 16 bits");
            }
        } else {
            BigInteger v = new BigInteger(txt);
            long max = esNegado(ctx) ? 32768 : 32767;
            if (v.compareTo(BigInteger.valueOf(max)) > 0) {
                throw error(t, "la constante " + txt + " está fuera del rango de 16 bits (-32768..32767)");
            }
        }
        return "int";
    }

    /** true si la constante es el operando directo de un '-' unario (permite -32768). */
    private boolean esNegado(MicroCParser.NumConstContext ctx) {
        var factor = ctx.getParent();                       // ConstFactor
        var padre = factor == null ? null : factor.getParent();
        return padre instanceof MicroCParser.UnaryFactorContext u && u.addOp().getText().equals("-");
    }

    // -------------------------------------------------------------- llamadas

    @Override
    public String visitCallArgs(MicroCParser.CallArgsContext ctx) {
        return verificarLlamada(ctx.ID().getSymbol(), ctx.expressionList().expression());
    }

    @Override
    public String visitCallNoArgs(MicroCParser.CallNoArgsContext ctx) {
        return verificarLlamada(ctx.ID().getSymbol(), List.of());
    }

    private String verificarLlamada(Token id, List<MicroCParser.ExpressionContext> args) {
        Simbolo f = tabla.buscar(id.getText(), id);
        if (f.categoria() != Categoria.FUNCION) {
            throw error(id, "'" + id.getText() + "' no es una función");
        }

        List<String> tipos = new ArrayList<>();
        for (var a : args) tipos.add(visit(a));

        if (f.esPredefinida()) return llamadaPredefinida(f, id, args, tipos);

        List<String> esperados = f.paramTipos();
        int n = esperados.size();
        if (args.size() < n || (!f.variadica() && args.size() > n)) {
            throw error(id, "'" + f.nombre() + "' espera " + n + " argumento(s) pero recibió " + args.size());
        }
        for (int i = 0; i < args.size(); i++) {
            String real = tipos.get(i);
            if (i < n) {
                if (!compatibles(esperados.get(i), real)) {
                    throw error(args.get(i).start, "argumento " + (i + 1) + " de '" + f.nombre() + "': se esperaba '" + esperados.get(i) + "' pero es '" + real + "'");
                }
            } else if ("void".equals(real)) {
                throw error(args.get(i).start, "argumento " + (i + 1) + " de '" + f.nombre() + "' no puede ser void");
            }
        }
        return f.tipo();
    }

    private String llamadaPredefinida(Simbolo f, Token id,
                                      List<MicroCParser.ExpressionContext> args,
                                      List<String> tipos) {
        if (f.nombre().equals("scanf")) {
            if (args.size() != 1) {
                throw error(id, "'scanf' requiere exactamente 1 argumento");
            }
            String fmt = args.get(0).getText();
            if (fmt.equals("\"%d\"")) return "int";
            if (fmt.equals("\"%c\"")) return "char";
            throw error(args.get(0).start, "el formato de 'scanf' debe ser \"%d\" o \"%c\"");
        }

        // printf
        if (args.size() < 1 || args.size() > 2) {
            throw error(id, "'printf' acepta 1 o 2 argumentos");
        }
        String fmt = args.get(0).getText();
        if (!fmt.startsWith("\"")) {
            throw error(args.get(0).start, "el primer argumento de 'printf' debe ser una constante de string");
        }
        int conversiones = contarConversiones(fmt, args.get(0).start);
        if (args.size() == 1 && conversiones != 0) {
            throw error(args.get(0).start, "el formato tiene una conversión pero falta el segundo argumento");
        }
        if (args.size() == 2) {
            if (conversiones != 1) {
                throw error(args.get(0).start, "con 2 argumentos el formato debe tener exactamente una conversión (%d o %c)");
            }
            if (!numerico(tipos.get(1))) {
                throw error(args.get(1).start, "el segundo argumento de 'printf' debe ser int o char, no '" + tipos.get(1) + "'");
            }
        }
        return "int";
    }

    /** Cuenta %d y %c; acepta %%; cualquier otra conversión es error. */
    private int contarConversiones(String fmt, Token t) {
        int n = 0;
        for (int i = 1; i < fmt.length() - 1; i++) {
            char c = fmt.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '%') {
                char sig = fmt.charAt(i + 1);
                if (sig == '%') {
                    i++;
                } else if (sig == 'd' || sig == 'c') {
                    n++;
                    i++;
                } else {
                    throw error(t, "conversión no válida en el formato; solo %d, %c y %%");
                }
            }
        }
        return n;
    }
}