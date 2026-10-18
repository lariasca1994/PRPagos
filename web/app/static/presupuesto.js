// Gráficas del presupuesto (Chart.js). Dibuja la vista que calcula el servidor
// (servicios/presupuesto.py), la misma que recibe el escritorio (JFreeChart).
// Un clic en un mes de las barras abre ese mes. Se vuelven a dibujar al
// cambiar entre tema claro y oscuro.
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
        var rejilla = css("--borde");
        var presupuesto = document.documentElement.getAttribute("data-tema") === "oscuro" ? "#3A4157" : "#C9CED8";
        Chart.defaults.color = css("--texto-suave");
        Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';

        var anual = document.getElementById("grafica-anual");
        if (anual) graficas.push(new Chart(anual, {
            type: "bar",
            data: {
                labels: d.serie.map(function (m) { return m.etiqueta; }),
                datasets: [
                    { label: "Presupuestado", data: d.serie.map(function (m) { return m.gastos_plan; }), backgroundColor: presupuesto, borderRadius: 4 },
                    { label: "Real", data: d.serie.map(function (m) { return m.gastos_real; }), backgroundColor: d.serie.map(function (m) { return m.mes === d.mes ? d.colores.gastos : d.colores.gastos + "B3"; }), borderRadius: 4 }
                ]
            },
            options: {
                maintainAspectRatio: false,
                scales: {
                    x: { grid: { color: rejilla } },
                    y: { grid: { color: rejilla }, ticks: { callback: function (v) { return moneda(v).replace(",00", ""); } } }
                },
                plugins: { tooltip: { callbacks: { label: function (c) { return c.dataset.label + ": " + moneda(c.parsed.y); } } } },
                onHover: function (e, elementos) { e.native.target.style.cursor = elementos.length ? "pointer" : "default"; },
                onClick: function (e, elementos) {
                    if (!elementos.length) return;
                    var mes = d.serie[elementos[0].index].mes;
                    window.location.href = "/presupuesto?anio=" + d.anio + "&mes=" + mes;
                }
            }
        }));

        var dist = document.getElementById("grafica-distribucion");
        if (dist) graficas.push(new Chart(dist, {
            type: "doughnut",
            data: {
                labels: d.distribucion.map(function (g) { return g.etiqueta; }),
                datasets: [{ label: "Presupuesto", data: d.distribucion.map(function (g) { return g.total; }), backgroundColor: d.distribucion.map(function (g) { return g.color; }), borderColor: css("--superficie"), borderWidth: 2 }]
            },
            options: {
                maintainAspectRatio: false, cutout: "60%",
                plugins: {
                    legend: { position: "right" },
                    tooltip: { callbacks: { label: function (c) { return c.label + ": " + moneda(c.parsed); } } }
                }
            }
        }));
    }

    dibujar();
    new MutationObserver(dibujar).observe(document.documentElement, { attributes: true, attributeFilter: ["data-tema"] });
})();
