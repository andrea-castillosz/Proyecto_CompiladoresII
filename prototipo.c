int count( int n );

int main( void )
{
    int i, sum;
    i = scanf( "%d" );
    sum = count( i );
    printf( "%d\n", sum );
}

int count( int n )
{
    int i, sum;
    i = 1;
    sum = 0;
    while ( i <= n ) {
        sum = sum + i;
        i = i + 1;
    }
    return sum;
}