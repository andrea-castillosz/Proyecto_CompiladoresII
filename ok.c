int factorial( int n )
{
    if ( n <= 1 )
        return 1;
    else
        return n * factorial( n - 1 );
}

int main( void )
{
    int n, fact;
    printf( "Ingrese un entero: " );
    n = scanf( "%d" );
    fact = factorial( n );
    printf( "Factorial de %d ", n );
    printf( "es %d\n", fact );
}