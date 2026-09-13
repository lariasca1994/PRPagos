# Dockerfile para desplegar PRPagos (web) en Cloud Run.
# El contexto de build es la RAÍZ del repo (A:\Github\PRPagos), no la carpeta web/,
# porque necesitamos copiar tanto web/app/ como wallet/ (que están a un mismo nivel).
FROM python:3.12-slim

WORKDIR /app

COPY web/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY web/app/ ./app/
COPY wallet/ ./wallet/

ENV WALLET_PATH=/app/wallet
ENV PYTHONUNBUFFERED=1

EXPOSE 8080

CMD exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080}
