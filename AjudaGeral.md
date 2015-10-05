# Ajuda Geral #

O Pseudo código está baseado em comandos em JavaScript

Para atribuição basta um único sinal de igual.
Ex.:
```
a = 33
```

## Comandos de Entrada e Saída ##

### Entrada ###
A entrada é feita seguindo a seguinte sintaxe:
```
leia p1
```
No exemplo consideramos que p1 seja uma variável.

É possível ler mais de uma variável ao mesmo tempo:
```
leia p1, p2
```

### Saída ###
Para gerar uma saída no programa deve se seguir o exemplo abaixo:
```
imprima p1
```
No exemplo consideramos que p1 seja uma variável.

É possível concatenar com uma seqüência de caracteres.
```
imprima "sua média foi " + media
```
No exemplo acima _media_ é uma variável

## Condições ##

Os operadores lógicos são: **e** e **ou**.
Também funcionam os **&&** e **||** que são **e** e **ou** respectivamente.

Os operadores de relação são: **=**, **>**, **<**, **>=** e **<=**.

Se você é um programador que sabe C ou Java ou JavaScript,
saiba que não é possível fazer atribuições dentro de uma condição.
Ex.: while(a=leitor.read())