-- Llaves de acceso (passkeys / WebAuthn) — aditivo.
-- Ingresar con Windows Hello, huella, rostro o el PIN del dispositivo, en la
-- web (navegador o PWA en Android) y en el escritorio (webauthn.dll). La
-- biometría nunca sale del dispositivo: aquí solo se guarda la clave pública
-- de cada llave (formato COSE, ES256 o RS256) y su contador de firmas.
-- La misma llave sirve en el navegador y en la app de escritorio del mismo
-- Windows porque las dos usan el mismo identificador (el dominio de la web).
--
-- Aplicar: python migrar.py llaves --aplicar   (desde web/)

CREATE TABLE TBLlavesAcceso (
    id             NUMBER         NOT NULL,
    usuario_id     NUMBER         NOT NULL,
    credencial_id  VARCHAR2(1400) NOT NULL,
    clave_publica  VARCHAR2(2000) NOT NULL,
    contador       NUMBER         DEFAULT 0 NOT NULL,
    nombre         VARCHAR2(60)   NOT NULL,
    creada         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    ultimo_uso     TIMESTAMP,
    CONSTRAINT pk_llaves PRIMARY KEY (id),
    CONSTRAINT fk_llaves_usuario FOREIGN KEY (usuario_id) REFERENCES TBPLogin(id),
    CONSTRAINT uq_llaves_credencial UNIQUE (credencial_id)
)
/

CREATE SEQUENCE tbllave_sequence START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE
/

CREATE INDEX ix_llaves_usuario ON TBLlavesAcceso (usuario_id)
/
