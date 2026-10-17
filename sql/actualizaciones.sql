-- Actualización automática de la aplicación de escritorio (aditivo).
-- El workflow .github/workflows/escritorio.yml publica cada versión como
-- Release de GitHub y la registra aquí (vía POST /tareas/version-escritorio)
-- con la huella SHA-256 del paquete. El escritorio lee la última fila, y solo
-- instala un paquete descargado de GitHub cuya huella coincida con esta:
-- la huella viene de la base, no del mismo sitio de la descarga.
--
-- Aplicar: python migrar.py actualizaciones --aplicar   (desde web/)

CREATE TABLE TBVersionesEscritorio (
    version    VARCHAR2(20)  NOT NULL,
    url        VARCHAR2(400) NOT NULL,
    sha256     VARCHAR2(64)  NOT NULL,
    publicada  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_versiones_escritorio PRIMARY KEY (version),
    CONSTRAINT ck_versiones_sha CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$')),
    CONSTRAINT ck_versiones_url CHECK (url LIKE 'https://github.com/%')
)
/
