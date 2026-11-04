grammar MicroC;

// ===================== PARSER =====================

translationUnit : externalDeclaration+ EOF ;

externalDeclaration : functionDefinition | declaration ;

functionDefinition : functionDefHeader functionBody ;
functionDefHeader  : returnType ID '(' parametersDef ')' ;
returnType         : type | 'void' ;
parametersDef      : parameterDefList | 'void' ;
parameterDefList   : type ID (',' type ID)* ;
functionBody       : '{' declarations statementList '}' ;

declarations : declaration* ;
declaration  : variableDeclaration | functionDeclaration ;

variableDeclaration : type identifierList ';' ;
functionDeclaration : returnType ID '(' parametersDecl ')' ';' ;
parametersDecl      : parameterDeclList (',' '...')?
                    | 'void'
                    ;
parameterDeclList   : parameterDeclSpec (',' parameterDeclSpec)* ;
parameterDeclSpec   : type ID
                    | 'char' '*' ID
                    ;

statementList : statement+ ;

statement
    : expression ';'                                      # ExprStmt
    | 'return' ';'                                        # ReturnVoid
    | 'return' expression ';'                             # ReturnValue
    | 'while' '(' expression ')' statement                # While
    | 'if' '(' expression ')' statement                   # IfNoElse
    | 'if' '(' expression ')' statement 'else' statement  # IfElse
    | '{' statementList '}'                               # Block
    | 'break' ';'                                         # Break
    | 'continue' ';'                                      # Continue
    ;

expression
    : equalityExpression '=' equalityExpression           # Assign
    | equalityExpression                                  # ExprEq
    ;

equalityExpression
    : relationalExpression eqOp relationalExpression      # Equality
    | relationalExpression                                # EqPass
    ;

relationalExpression
    : simpleExpression relOp simpleExpression             # Relational
    | simpleExpression                                    # RelPass
    ;

simpleExpression
    : simpleExpression addOp term                         # AddSub
    | term                                                # SimplePass
    ;

term
    : term mulOp factor                                   # MulDiv
    | factor                                              # TermPass
    ;

factor
    : constant                                            # ConstFactor
    | ID                                                  # IdFactor
    | '(' expression ')'                                  # ParenFactor
    | addOp factor                                        # UnaryFactor
    | ID '(' expressionList ')'                           # CallArgs
    | ID '(' ')'                                          # CallNoArgs
    ;

constant
    : STRING                                              # StringConst
    | numericConstant                                     # NumConst
    | CHAR                                                # CharConst
    ;

numericConstant : INTEGER ;

expressionList : expression (',' expression)* ;
identifierList : ID (',' ID)* ;
type           : 'int' | 'char' ;

addOp : '+' | '-' ;
mulOp : '*' | '/' ;
eqOp  : '==' | '!=' ;
relOp : '<' | '<=' | '>=' | '>' ;

// ===================== LEXER =====================

ID      : [a-zA-Z_] [a-zA-Z_0-9]* ;
INTEGER : [0-9]+ | '0' [xX] [0-9a-fA-F]+ ;
CHAR    : '\'' ~['\r\n] '\'' ;
STRING  : '"' ( '\\"' | '\\n' | '\\\\' | ~["\\\r\n] )* '"' ;

COMMENT : '/*' .*? '*/' -> skip ;
UNCLOSED_COMMENT : '/*' ( ~'*' | '*'+ ~[*/] )* '*'* EOF ;
WS      : [ \t\r\n]+ -> skip ;