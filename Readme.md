## ECP Client com Challenge-Response Java
## Descrição
>O projeto consiste em uma adaptação da aplicação Client para executar a autenticação usando challenge-response conforme implementado em [ClientSamlPost](https://github.com/dsubires/SAMLClient4IoT). 
>
>**Obs.:** O Client foi testado utilizando um SP Shibboleth e um IdP SimpleSAMLphp. Para utilizar um IdP Shibboleth talvez sejam necessárias alterações no código.

### Pré-requisitos

* Git
* Gradle (Para empacotar o Client em arquivo _**.jar**_)

### Gerando arquivo _**.jar**_ do Client
1. Faça o download do projeto client-ecp-java

```bash
$ git clone https://git.rnp.br/gidlab/client-ecp-java.git
```

2. Acesse o diretório referente ao projeto pelo terminal, por exemplo: `$ cd ~/client-ecp-java/` 

3. Execute o checkout para o branch ecp-challenge com o comando:
  ```bash
  $ git checkout -b ecp-challenge
  ```  
	
4. Execute o comando abaixo dentro do diretório do projeto:
  ```bash
  $ gradle shadowJar
  ```

  **Obs.:** O arquivo _**.jar**_ será gerado em `./build/libs`

### Executando Client

1. Para iniciar, execute o comando abaixo substituindo os argumentos pelas respectivas informações:

```bash
$ java -jar <arquivo.jar> <endpoint do SP> <endpoint do IdP> <uuiddevice> [OPCÕES]
```

Exemplo:

```bash
$ java -jar build/libs/ECP-Client-1.0.jar http://idp-ecp.com http://sp-ecp.com/secure uuiddevice
```

**Obs.:** Caso deseje imprimir todas as informações de DEBUG, insira o argumento `debug` como argumento em `[OPÇÕES]`

Exemplo:

```bash
$ java -jar build/libs/ECP-Client-1.0.jar http://idp-ecp.com http://sp-ecp.com/secure uuiddevice debug
```
