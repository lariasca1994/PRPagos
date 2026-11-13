// Gráficas del dashboard (Chart.js). Dibuja la vista que calcula el servidor
// (servicios/dashboard.py), la misma que recibe el escritorio (JFreeChart).
// Un clic en un mes abre ese mes y uno en una categoría, sus registros.
// Se vuelven a dibujar al cambiar entre tema claro y oscuro.
//
// Estilo: barras redondeadas con el mes elegido resaltado y su valor en una
// píldora; anillo con el total al centro; medidores semicirculares por
// segmentos para el plan; cifras que cuentan desde cero. El escritorio
// (PanelDashboard.java) dibuja lo mismo.
(function () {
    "use strict";
    var fuente = document.getElementById("datos-graficas");
    if (!fuente) return;
    var d = JSON.parse(fuente.textContent);
    var graficas = [];
    var reducir = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var primeraVez = true;

    function css(nombre) {
        return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim();
    }

    function moneda(v) {
        var partes = Math.abs(v).toFixed(2).split(".");
        partes[0] = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".");
        return (v < 0 ? "-" : "") + "$ " + partes[0] + "," + partes[1];
    }

    // "$ 1,2 M" / "$ 850 mil": para las píldoras sobre las barras.
    function corto(v) {
        var a = Math.abs(v), s = v < 0 ? "-" : "";
        if (a >= 1e6) return s + "$ " + (a / 1e6).toFixed(1).replace(".", ",") + " M";
        if (a >= 1e3) return s + "$ " + Math.round(a / 1e3) + " mil";
        return s + "$ " + Math.round(a);
    }

    // #RRGGBB con transparencia, para atenuar los meses no elegidos.
    function suave(hex, alfa) {
        var h = hex.replace("#", "");
        var n = parseInt(h.length === 3 ? h.replace(/./g, "$&$&") : h, 16);
        return "rgba(" + (n >> 16 & 255) + "," + (n >> 8 & 255) + "," + (n & 255) + "," + alfa + ")";
    }

    function mano(e, elementos) { e.native.target.style.cursor = elementos.length ? "pointer" : "default"; }

    function enlaceRegistros(f) {
        return "/registros?tipo=" + encodeURIComponent(f.tipo) + "&categoria=" + encodeURIComponent(f.categoria) +
            "&fecha_desde=" + f.fecha_desde + "&fecha_hasta=" + f.fecha_hasta;
    }

    /* ───────── Cifras que cuentan desde cero ───────── */
    function contar(el) {
        var texto = el.textContent.trim();
        var m = texto.match(/\d[\d.]*(,\d+)?/);
        if (!m || reducir) return;
        var crudo = m[0], dec = (crudo.split(",")[1] || "").length;
        var valor = parseFloat(crudo.replace(/\./g, "").replace(",", "."));
        var antes = texto.slice(0, m.index), despues = texto.slice(m.index + crudo.length);
        var inicio = performance.now(), dura = 700;
        function formato(v) {
            var p = v.toFixed(dec).split(".");
            p[0] = p[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".");
            return p[0] + (dec ? "," + p[1] : "");
        }
        el.setAttribute("aria-label", texto);
        function paso(t) {
            var k = Math.min(1, (t - inicio) / dura), ez = 1 - Math.pow(1 - k, 3);
            el.textContent = k < 1 ? antes + formato(valor * ez) + despues : texto;
            if (k < 1) requestAnimationFrame(paso);
        }
        el.textContent = antes + formato(0) + despues;
        requestAnimationFrame(paso);
    }

    /* ───────── Variación frente al mes anterior ───────── */
    function variaciones() {
        var s = d.serie || [];
        if (s.length < 2) return;
        var act = s[s.length - 1], ant = s[s.length - 2];
        var valores = {
            ingresos: [act.ingresos, ant.ingresos, true],
            gastos: [act.gastos, ant.gastos, false],
            balance: [act.ingresos - act.gastos, ant.ingresos - ant.gastos, true]
        };
        document.querySelectorAll("[data-variacion]").forEach(function (el) {
            var v = valores[el.dataset.variacion];
            if (!v || !v[1]) { el.textContent = "Sin datos del mes anterior"; return; }
            var pct = Math.round((v[0] - v[1]) / Math.abs(v[1]) * 100);
            var sube = pct > 0, igual = pct === 0;
            el.textContent = (igual ? "= " : sube ? "▲ " : "▼ ") + Math.abs(pct) + " % vs. mes anterior";
            el.classList.add(igual ? "variacion-igual" : (sube === v[2] ? "variacion-bien" : "variacion-mal"));
        });
    }

    /* ───────── Medidores del plan (SVG, por segmentos) ───────── */
    function medidores() {
        var caja = document.getElementById("medidores-plan");
        if (!caja) return;
        var SEG = 18, R = 46, CX = 60, CY = 58, ICONO = { bien: "✓", mal: "✕", alerta: "!" };
        caja.innerHTML = "";
        d.plan.forEach(function (f, n) {
            var pct = parseInt(f.usado, 10) || 0;
            var llenos = Math.round(Math.min(pct, 100) / 100 * SEG);
            var segs = "";
            for (var i = 0; i < SEG; i++) {
                var a0 = Math.PI + (i / SEG) * Math.PI + 0.03, a1 = Math.PI + ((i + 1) / SEG) * Math.PI - 0.03;
                var x0 = CX + R * Math.cos(a0), y0 = CY + R * Math.sin(a0), x1 = CX + R * Math.cos(a1), y1 = CY + R * Math.sin(a1);
                segs += '<path class="seg' + (i < llenos ? " lleno" : "") + '" style="--i:' + i + ';--c:' + f.color_grupo +
                    '" d="M' + x0.toFixed(2) + " " + y0.toFixed(2) + " A" + R + " " + R + " 0 0 1 " + x1.toFixed(2) + " " + y1.toFixed(2) + '"/>';
            }
            var m = document.createElement("figure");
            m.className = "medidor";
            m.style.setProperty("--n", n);
            m.innerHTML = '<svg viewBox="0 0 120 66" aria-hidden="true">' + segs + "</svg>" +
                '<div class="medidor-valor"><strong>' + f.usado + "</strong><span>usado</span></div>" +
                "<figcaption><b>" + f.grupo + '</b><span class="estado-plan estado-' + f.clase_estado + '">' +
                (ICONO[f.clase_estado] || "") + " " + f.estado + "</span></figcaption>";
            caja.appendChild(m);
        });
    }

    /* ───────── Chart.js ───────── */
    // Píldora con el valor sobre las barras del mes elegido.
    var pildoras = {
        id: "pildoras",
        afterDatasetsDraw: function (chart) {
            var ctx = chart.ctx, i = chart.data.labels.length - 1, fondo = css("--texto"), letra = css("--superficie");
            var area = chart.chartArea, previa = null;
            chart.data.datasets.forEach(function (ds, k) {
                var meta = chart.getDatasetMeta(k);
                if (meta.hidden || !meta.data[i]) return;
                var bar = meta.data[i], txt = corto(ds.data[i]);
                if (!ds.data[i]) return;
                ctx.save();
                ctx.font = "600 11px system-ui, -apple-system, 'Segoe UI', sans-serif";
                var w = ctx.measureText(txt).width + 12, h = 18;
                // Dentro del área de la gráfica y sin chocar con la píldora anterior.
                var x = Math.min(area.right - w, Math.max(area.left, bar.x - w / 2)), y = bar.y - h - 6;
                if (previa && x < previa.x + previa.w + 4 && x + w > previa.x - 4) y = Math.min(y, previa.y - h - 4);
                previa = { x: x, y: y, w: w };
                ctx.globalAlpha = Math.min(1, chart._pildoraAlfa === undefined ? 1 : chart._pildoraAlfa);
                ctx.fillStyle = fondo;
                ctx.beginPath();
                if (ctx.roundRect) ctx.roundRect(x, y, w, h, 9); else ctx.rect(x, y, w, h);
                ctx.fill();
                ctx.fillStyle = letra;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(txt, x + w / 2, y + h / 2 + 0.5);
                ctx.restore();
            });
        }
    };

    // Total del mes al centro del anillo.
    var centro = {
        id: "centro",
        afterDraw: function (chart) {
            if (chart.config.type !== "doughnut") return;
            var meta = chart.getDatasetMeta(0);
            if (!meta.data[0]) return;
            var x = meta.data[0].x, y = meta.data[0].y, ctx = chart.ctx;
            var total = chart.data.datasets[0].data.reduce(function (a, b) { return a + b; }, 0);
            ctx.save();
            ctx.textAlign = "center";
            ctx.fillStyle = css("--texto-suave");
            ctx.font = "12px system-ui, -apple-system, 'Segoe UI', sans-serif";
            ctx.fillText("Gasto del mes", x, y - 10);
            ctx.fillStyle = css("--texto");
            ctx.font = "700 15px system-ui, -apple-system, 'Segoe UI', sans-serif";
            ctx.fillText(corto(total), x, y + 10);
            ctx.restore();
        }
    };

    function dibujar() {
        if (!window.Chart) return;
        graficas.forEach(function (g) { g.destroy(); });
        graficas = [];
        var texto = css("--texto-suave");
        var rejilla = css("--borde");
        Chart.defaults.color = texto;
        Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
        var animar = !reducir && primeraVez;
        var anim = animar ? {
            duration: 700, easing: "easeOutQuart",
            delay: function (c) { return c.type === "data" && c.mode === "default" ? c.dataIndex * 70 + c.datasetIndex * 40 : 0; }
        } : false;
        var ejes = {
            x: { grid: { display: false }, border: { display: false } },
            y: { grid: { color: rejilla }, border: { display: false }, ticks: { maxTicksLimit: 5, callback: function (v) { return corto(v); } } }
        };
        var tooltip = { callbacks: { label: function (c) { return c.dataset.label + ": " + moneda(c.parsed.y); } } };
        var ultimo = d.serie.length - 1;
        function colores(base) { return d.serie.map(function (m, i) { return i === ultimo ? base : suave(base, 0.32); }); }

        var meses = document.getElementById("grafica-meses");
        if (meses) graficas.push(new Chart(meses, {
            type: "bar",
            data: {
                labels: d.serie.map(function (m) { return m.etiqueta; }),
                datasets: [
                    { label: "Ingresos", data: d.serie.map(function (m) { return m.ingresos; }), backgroundColor: colores(d.colores.ingresos), hoverBackgroundColor: d.colores.ingresos, borderRadius: 8, borderSkipped: false, maxBarThickness: 34 },
                    { label: "Gastos", data: d.serie.map(function (m) { return m.gastos; }), backgroundColor: colores(d.colores.gastos), hoverBackgroundColor: d.colores.gastos, borderRadius: 8, borderSkipped: false, maxBarThickness: 34 }
                ]
            },
            options: {
                maintainAspectRatio: false, scales: ejes, animation: anim,
                layout: { padding: { top: 48 } },
                plugins: { tooltip: tooltip, legend: { labels: { usePointStyle: true, pointStyle: "rectRounded" } } },
                onHover: mano,
                onClick: function (e, elementos) {
                    if (elementos.length) window.location.href = "/dashboard?mes=" + d.serie[elementos[0].index].mes;
                }
            },
            plugins: [pildoras]
        }));

        var cats = document.getElementById("grafica-categorias");
        if (cats) graficas.push(new Chart(cats, {
            type: "doughnut",
            data: {
                labels: d.categorias.map(function (c) { return c.nombre; }),
                datasets: [{ label: "Gasto", data: d.categorias.map(function (c) { return c.total; }), backgroundColor: d.categorias.map(function (c) { return c.color; }), borderColor: css("--superficie"), borderWidth: 3, borderRadius: 6, hoverOffset: 8 }]
            },
            options: {
                maintainAspectRatio: false, cutout: "68%",
                animation: animar ? { animateRotate: true, duration: 900, easing: "easeOutQuart" } : false,
                plugins: {
                    legend: { position: "right", labels: { usePointStyle: true, pointStyle: "circle" } },
                    tooltip: { callbacks: { label: function (c) { return c.label + ": " + moneda(c.parsed); } } }
                },
                onHover: mano,
                onClick: function (e, elementos) {
                    if (elementos.length) window.location.href = enlaceRegistros(d.categorias[elementos[0].index].filtro);
                }
            },
            plugins: [centro]
        }));
        primeraVez = false;
    }

    variaciones();
    medidores();
    // En la app instalada, las cifras cuentan y las gráficas crecen cuando se quita el arranque.
    var arrancar = window.alArrancar || function (fn) { fn(); };
    arrancar(function () {
        document.querySelectorAll("[data-contar]").forEach(contar);
        dibujar();
    });
    new MutationObserver(dibujar).observe(document.documentElement, { attributes: true, attributeFilter: ["data-tema"] });
})();
