# Dockerfile para desplegar PRPagos (web) en Cloud Run.
# El contexto de build es la RAÍZ del repo (A:\Github\PRPagos), no la carpeta web/,
# porque necesitamos copiar tanto web/app/ como wallet/ (que están a un mismo nivel).
FROM python:3.12.15-slim
# Almacén de certificados del sistema sin las raíces que firmaron los intermedios
# comprometidos de eMudhra (octubre 2026): emSign y Comodo "AAA Certificate Services".
RUN if command -v update-ca-certificates > /dev/null; then \
        rm -f /usr/share/ca-certificates/mozilla/emSign_* /usr/share/ca-certificates/mozilla/Comodo_AAA_Services_root.crt \
        && update-ca-certificates --fresh > /dev/null 2>&1; \
    fi

WORKDIR /app

COPY web/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY web/app/ ./app/
# Consultas SQL y plantillas de correo compartidas con el escritorio.
COPY compartido/ ./compartido/
COPY wallet/ ./wallet/

ENV WALLET_PATH=/app/wallet
ENV PYTHONUNBUFFERED=1

EXPOSE 8080

CMD exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080}
