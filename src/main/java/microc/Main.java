package microc;

import org.antlr.v4.runtime.*;
import java.nio.file.Path;

public class Main {

    static class ErroresListener extends BaseErrorListener {
        private final String fase;
        ErroresListener(String fase) { this.fase = fase; }

        @Override
        public void syntaxError(Recognizer<?, ?> r, Object sym, int line,
                                int col, String msg, RecognitionException e) {
            throw new RuntimeException(
                    "Error " + fase + " [" + line + ":" + (col + 1) + "] " + msg);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Uso: Main <archivo.c>");
            System.exit(1);
        }
        CharStream input = CharStreams.fromPath(Path.of(args[0]));

        MicroCLexer lexer = new MicroCLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new ErroresListener("LEXICO"));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MicroCParser parser = new MicroCParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ErroresListener("SINTACTICO"));

        try {
            var tree = parser.translationUnit();
            System.out.println(tree.toStringTree(parser));
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }
}