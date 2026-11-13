/* Pantalla de mantenimiento: cuenta regresiva visible y reintento contra
   /health. Cuando la base de datos responde, vuelve a la misma dirección con
   GET (location.replace), así nunca se reenvía un formulario. */
(function () {
    "use strict";
    var caja = document.querySelector("[data-reintento]");
    if (!caja) return;
    var total = parseInt(caja.dataset.reintento, 10) || 15;
    var texto = caja.querySelector("[data-reintento-texto]");
    var arco = caja.querySelector(".cuenta-valor");
    var boton = document.querySelector("[data-reintentar]");
    var resto = total, revisando = false;

    function pintar(msg) {
        texto.textContent = msg;
        arco.style.strokeDashoffset = String(revisando ? 0 : 1 - resto / total);
    }

    async function revisar() {
        if (revisando) return;
        revisando = true;
        pintar("Comprobando…");
        if (window.Efectos) window.Efectos.ocupado(boton, true);
        try {
            var r = await fetch("/health", { cache: "no-store" });
            if (r.ok) {
                pintar("La base de datos respondió. Volviendo…");
                if (window.Efectos) window.Efectos.listo(boton);
                setTimeout(function () { location.replace(location.href); }, 500);
                return;
            }
        } catch (e) { /* sin red o sin servidor: se sigue esperando */ }
        revisando = false;
        resto = total;
        if (window.Efectos) window.Efectos.ocupado(boton, false);
        pintar("La base de datos aún no responde. Reintento en " + total + " s");
    }

    boton.addEventListener("click", revisar);
    setInterval(function () {
        if (revisando) return;
        resto -= 1;
        if (resto <= 0) { revisar(); return; }
        pintar("Reintento automático en " + resto + " s");
    }, 1000);
    window.addEventListener("online", revisar);
    pintar("Reintento automático en " + resto + " s");
})();
