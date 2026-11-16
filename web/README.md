# Simulador de pagos · versión web

Versión web en Python (FastAPI, Jinja2 y Chart.js) del [Simulador de pagos](../README.md). Es el
único origen de datos y de lógica: las páginas web y la aplicación de escritorio usan los mismos
servicios. Se instala como aplicación desde el navegador.

> 📘 **[Manual de usuario](../docs/MANUAL-WEB.md)**: cómo usar cada pantalla, paso a paso y con imágenes.

## Estructura

```
app/
├── routers/      Páginas y API
├── servicios/    Reglas y cálculos (los usa también el escritorio)
├── templates/    Vistas Jinja2
└── static/       Estilos, scripts e íconos
tests/            Pruebas (no necesitan base de datos)
```

## Instalación para pruebas

1. Requisitos: Python 3.12 y una base de datos propia preparada con los scripts de `../sql/`.
2. Instala:

   ```bash
   cd web
   python -m venv .venv
   .venv\Scripts\Activate.ps1        # Windows
   source .venv/bin/activate         # Linux o macOS
   pip install -r requirements.txt
   cp .env.example .env
   ```

3. Completa `.env` con los datos de tu propia base y tus propias claves, siguiendo los comentarios
   de `.env.example` (ese archivo no se sube al repositorio).
4. Arranca con `uvicorn app.main:app --reload` (desde `web/`) y abre `http://127.0.0.1:8000`.
5. Pruebas:

   ```bash
   pip install -r requirements-dev.txt
   pytest tests -q
   ```

## Autor

**Luis F. A.** · [GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1) ·
Licencia [MIT](../LICENSE)
