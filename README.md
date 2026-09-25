# PRPagos

![Java](https://img.shields.io/badge/Java-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat&logo=springboot&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle-F80000?style=flat&logo=oracle&logoColor=white)
![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=flat&logo=googlecloud&logoColor=white)

Aplicación para la gestión de pagos, con persistencia en Oracle Autonomous
Database. Desarrollada originalmente durante el curso de Desarrollo de
Software de la Universidad EAN.

El repositorio tiene dos versiones independientes de la misma aplicación,
que comparten la misma base de datos:

- **[`escritorio/`](./escritorio)** — la aplicación original en Java/Swing,
  para quien prefiera correrla localmente.
- **[`web/`](./web)** — versión web en Spring Boot (Java 21, Thymeleaf),
  desplegada en Google Cloud Run.

Ver [`CHANGELOG-migracion.md`](./CHANGELOG-migracion.md) para el detalle de
la migración de escritorio a web.

## Funcionalidades

- Registro e inicio de sesión de usuarios
- Registro, consulta y actualización de pagos
- Cada usuario ve únicamente sus propios pagos
- Rol Administrador de solo lectura: consulta los pagos de todos los
  usuarios sin poder modificarlos
- Exportar pagos a CSV
- Registro de auditoría de las operaciones

## Estructura del repositorio

```
escritorio/    Aplicación de escritorio en Java (Swing)
web/           Aplicación web en Java (Spring Boot + Thymeleaf)
sql/           Tablas, secuencias y procedimientos almacenados (compartido)
wallet/        Wallet de Oracle Autonomous Database (compartido, no se sube a git)
docs/          Documentación adicional (compartido)
```

## Arquitectura

```mermaid
flowchart TB

    subgraph Clientes["👥 Clientes"]
        Browser["🌐 Navegador Web<br/>Versión principal"]
        Desktop["🖥️ App de Escritorio<br/>Java Swing (legacy)"]
    end

    subgraph CloudRun["☁️ Google Cloud Run"]
        subgraph WebApp["Aplicación Web — FastAPI + Uvicorn"]
            Main["main.py<br/>Punto de entrada ASGI"]
            Routers["routers/<br/>auth · registros"]
            Templates["templates/<br/>Jinja2"]
            Static["static/<br/>CSS · JS"]
            Seguridad["seguridad.py<br/>JWT · SHA-256"]
            Repositorio["repositorio.py<br/>oracledb"]
            Database["database.py<br/>Pool de conexiones"]
        end
    end

    subgraph OCI["🗄️ Oracle Cloud Infrastructure"]
        Wallet["🔐 Wallet Oracle<br/>TLS / mTLS"]
        ADB[("Oracle Autonomous Database<br/>TBPLogin · TBPagos · Auditoría")]
        Procedures["⚙️ Procedimientos almacenados<br/>validar_login · insertar_tbpago<br/>actualizar_tbpago · buscar_tbpagos"]
    end

    %% ---- Flujo de datos ----
    Browser -->|HTTPS| Main
    Desktop -->|JDBC / TLS| ADB
    Main --> Routers
    Routers --> Templates
    Routers --> Static
    Routers --> Seguridad
    Seguridad -->|Cookie JWT httponly| Browser
    Routers --> Repositorio
    Repositorio --> Database
    Database -->|python-oracledb| Wallet
    Wallet -->|TCPS| ADB
    ADB --- Procedures
    Repositorio -.->|callproc| Procedures

    %% ---- Colores de marca (Brand Colors) ----
    classDef fastapi fill:#009688,stroke:#004D40,stroke-width:2px,color:#FFFFFF,rx:12,ry:12;
    classDef python fill:#3572A5,stroke:#1A3A5C,stroke-width:2px,color:#FFFFFF,rx:12,ry:12;
    classDef oracle fill:#F80000,stroke:#7F0000,stroke-width:2px,color:#FFFFFF;
    classDef gcp fill:#4285F4,stroke:#1A4B9C,stroke-width:2px,color:#FFFFFF,rx:12,ry:12;
    classDef java fill:#ED8B00,stroke:#B36B00,stroke-width:2px,color:#FFFFFF,rx:12,ry:12;
    classDef security fill:#333333,stroke:#000000,stroke-width:2px,color:#FFFFFF,rx:10,ry:10;
    classDef neutral fill:#F5F5F5,stroke:#CCCCCC,stroke-width:1px,color:#333333,rx:10,ry:10;

    class Browser neutral;
    class Desktop java;
    class Main,Routers,Templates,Static fastapi;
    class Seguridad security;
    class Repositorio,Database python;
    class Wallet,ADB,Procedures oracle;

    %% ---- Estilos de subgráficos ----
    style Clientes fill:#FAFAFA,stroke:#DDDDDD,stroke-width:1px,rx:14,ry:14;
    style CloudRun fill:#E1F5FE,stroke:#4285F4,stroke-width:2px,stroke-dasharray:6 4,rx:16,ry:16;
    style WebApp fill:#E0F2F1,stroke:#009688,stroke-width:1px,rx:10,ry:10;
    style OCI fill:#FFF0F0,stroke:#F80000,stroke-width:2px,stroke-dasharray:6 4,rx:16,ry:16;
```

`sql/`, `wallet/` y `docs/` están en la raíz porque los usan tanto
`escritorio/` como `web/`: ambas versiones hablan con la misma base de
datos y usan el mismo Wallet para conectarse.

## Base de datos

Requiere una base Oracle accesible (Oracle Autonomous Database tiene un
plan gratuito permanente). Ejecutar `sql/DBPagos.sql` crea las tablas, las
secuencias, los procedimientos almacenados y la tabla de auditoría —
solo hace falta hacerlo una vez, sin importar qué versión (escritorio o
web) se vaya a usar.

Si la base es Oracle Autonomous Database, la conexión es por TLS mediante
un Wallet: hay que descargarlo desde la consola de Oracle Cloud y ubicarlo
en `wallet/` (o donde indique la configuración de cada versión).

## Seguridad

Las contraseñas se almacenan con hash, nunca en texto plano, y ese hash es
el mismo en ambas versiones para que una cuenta funcione desde cualquiera
de las dos interfaces. La visibilidad de los pagos está limitada por
usuario, y el procedimiento de actualización verifica la autorización
antes de modificar un registro — esa regla vive en la base de datos, no en
ninguna de las dos aplicaciones cliente.

Para instrucciones de configuración y ejecución de cada versión, ver el
`README.md` dentro de `escritorio/` y de `web/`.

## Despliegue

La base de datos corre en Oracle Autonomous Database (OCI, plan Always
Free). La aplicación web (`web/`) está desplegada en Google Cloud Run.

## Autor

**Luis Felipe Arias Carriazo**
[GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1)