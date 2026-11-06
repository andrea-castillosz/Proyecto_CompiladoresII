package microc;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import microc.TablaSimbolos.Categoria;
import microc.TablaSimbolos.Simbolo;
import microc.MicroCBaseVisitor;
import microc.MicroCParser;
public class ChequeoSemantico extends MicroCBaseVisitor<Void> {

    private final TablaSimbolos tabla = new TablaSimbolos();

    @Override
    public Void visitVariableDeclaration(MicroCParser.VariableDeclarationContext ctx) {
        String tipo = ctx.type().getText();
        for (TerminalNode id : ctx.identifierList().ID()) {
            Token t = id.getSymbol();
            tabla.declarar(new Simbolo(t.getText(), tipo, Categoria.VARIABLE,
                    t.getLine(), t.getCharPositionInLine()), t);
        }
        return null;
    }

    @Override
    public Void visitFunctionDefinition(MicroCParser.FunctionDefinitionContext ctx) {
        var header = ctx.functionDefHeader();
        Token nombre = header.ID().getSymbol();


        tabla.declarar(new Simbolo(nombre.getText(), header.returnType().getText(),
                Categoria.FUNCION, nombre.getLine(), nombre.getCharPositionInLine()), nombre);

        tabla.entrarAmbito();

        var lista = header.parametersDef().parameterDefList();
        if (lista != null) {
            for (int i = 0; i < lista.ID().size(); i++) {
                Token p = lista.ID(i).getSymbol();
                tabla.declarar(new Simbolo(p.getText(), lista.type(i).getText(),
                        Categoria.PARAMETRO, p.getLine(), p.getCharPositionInLine()), p);
            }
        }

        visit(ctx.functionBody());
        tabla.salirAmbito();
        return null;
    }


    @Override
    public Void visitIdFactor(MicroCParser.IdFactorContext ctx) {
        Token t = ctx.ID().getSymbol();
        tabla.buscar(t.getText(), t);
        return null;
    }

    @Override
    public Void visitCallArgs(MicroCParser.CallArgsContext ctx) {
        Token t = ctx.ID().getSymbol();
        tabla.buscar(t.getText(), t);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCallNoArgs(MicroCParser.CallNoArgsContext ctx) {
        Token t = ctx.ID().getSymbol();
        tabla.buscar(t.getText(), t);
        return null;
    }
}