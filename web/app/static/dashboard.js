// Gráficas del dashboard (Chart.js). Dibuja la vista que calcula el servidor
// (servicios/dashboard.py), la misma que recibe el escritorio (JFreeChart).
// Se vuelven a dibujar al cambiar entre tema claro y oscuro.
(function () {
    "use strict";
    var fuente = document.getElementById("datos-graficas");
    if (!fuente || !window.Chart) return;
    var d = JSON.parse(fuente.textContent);
    var graficas = [];

    function css(nombre) {
        return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim();
    }

    function moneda(v) {
        var partes = Math.abs(v).toFixed(2).split(".");
        partes[0] = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".");
        return (v < 0 ? "-" : "") + "$ " + partes[0] + "," + partes[1];
    }

    function dibujar() {
        graficas.forEach(function (g) { g.destroy(); });
        graficas = [];
        var texto = css("--texto-suave");
        var rejilla = css("--borde");
        var presupuesto = document.documentElement.getAttribute("data-tema") === "oscuro" ? "#3A4157" : "#C9CED8";
        Chart.defaults.color = texto;
        Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
        var ejes = {
            x: { grid: { color: rejilla } },
            y: { grid: { color: rejilla }, ticks: { callback: function (v) { return moneda(v).replace(",00", ""); } } }
        };
        var tooltip = { callbacks: { label: function (c) { return c.dataset.label + ": " + moneda(c.parsed.y); } } };

        var meses = document.getElementById("grafica-meses");
        if (meses) graficas.push(new Chart(meses, {
            type: "bar",
            data: {
                labels: d.serie.map(function (m) { return m.etiqueta; }),
                datasets: [
                    { label: "Ingresos", data: d.serie.map(function (m) { return m.ingresos; }), backgroundColor: d.colores.ingresos, borderRadius: 4 },
                    { label: "Gastos", data: d.serie.map(function (m) { return m.gastos; }), backgroundColor: d.colores.gastos, borderRadius: 4 }
                ]
            },
            options: { maintainAspectRatio: false, scales: ejes, plugins: { tooltip: tooltip } }
        }));

        var cats = document.getElementById("grafica-categorias");
        if (cats) graficas.push(new Chart(cats, {
            type: "doughnut",
            data: {
                labels: d.categorias.map(function (c) { return c.nombre; }),
                datasets: [{ label: "Gasto", data: d.categorias.map(function (c) { return c.total; }), backgroundColor: d.categorias.map(function (c) { return c.color; }), borderColor: css("--superficie"), borderWidth: 2 }]
            },
            options: {
                maintainAspectRatio: false, cutout: "60%",
                plugins: {
                    legend: { position: "right" },
                    tooltip: { callbacks: { label: function (c) { return c.label + ": " + moneda(c.parsed); } } }
                }
            }
        }));

        var plan = document.getElementById("grafica-plan");
        if (plan) graficas.push(new Chart(plan, {
            type: "bar",
            data: {
                labels: d.plan.map(function (f) { return f.grupo; }),
                datasets: [
                    { label: "Presupuesto", data: d.plan.map(function (f) { return f.presupuesto; }), backgroundColor: presupuesto, borderRadius: 4 },
                    { label: "Real", data: d.plan.map(function (f) { return f.real; }), backgroundColor: d.plan.map(function (f) { return f.color_grupo; }), borderRadius: 4 }
                ]
            },
            options: {
                indexAxis: "y", maintainAspectRatio: false,
                scales: {
                    x: { grid: { color: rejilla }, ticks: { callback: function (v) { return moneda(v).replace(",00", ""); } } },
                    y: { grid: { color: rejilla } }
                },
                plugins: { tooltip: { callbacks: { label: function (c) { return c.dataset.label + ": " + moneda(c.parsed.x); } } } }
            }
        }));
    }

    dibujar();
    new MutationObserver(dibujar).observe(document.documentElement, { attributes: true, attributeFilter: ["data-tema"] });
})();
