/* Efectos comunes (web y PWA): onda al tocar, botón "enviando", temblor en
   errores, menú lateral (indicador, fondo que sigue al ratón, compacto con
   etiqueta), hoja "Más" del teléfono y transición al cambiar de pantalla.
   Sin dependencias. Si algo falla, la página sigue funcionando sin efectos. */
(function () {
    "use strict";
    var raiz = document.documentElement;
    var reducir = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var CHECK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';

    function guardar(clave, valor) { try { sessionStorage.setItem(clave, valor); } catch (e) { /* sin almacenamiento */ } }
    function leer(clave) { try { return sessionStorage.getItem(clave); } catch (e) { return null; } }

    /* ───────── Botones ───────── */

    // El texto va en un <span> para poder desvanecerlo sin cambiar el ancho.
    function prepararBoton(b) {
        if (b.dataset.efectos) return;
        b.dataset.efectos = "1";
        if (!b.querySelector(".boton-texto") && b.childElementCount === 0) {
            var t = document.createElement("span");
            t.className = "boton-texto";
            t.textContent = b.textContent;
            b.textContent = "";
            b.appendChild(t);
        }
    }

    function onda(e) {
        var b = e.target.closest(".boton, .boton-secundario, .menu-op, .menu-pie-boton");
        if (!b || reducir || b.disabled) return;
        var r = b.getBoundingClientRect();
        var o = document.createElement("span");
        o.className = "onda";
        o.style.setProperty("--x", (e.clientX - r.left) + "px");
        o.style.setProperty("--y", (e.clientY - r.top) + "px");
        o.style.setProperty("--d", Math.max(r.width, r.height) * 2.2 + "px");
        b.appendChild(o);
        o.addEventListener("animationend", function () { o.remove(); });
    }

    function ocupado(b, si) {
        if (!b) return;
        prepararBoton(b);
        if (si) {
            b.setAttribute("aria-busy", "true");
            b.setAttribute("aria-disabled", "true");
        } else {
            b.removeAttribute("aria-busy");
            b.removeAttribute("aria-disabled");
        }
    }

    function listo(b) {
        if (!b) return;
        prepararBoton(b);
        b.removeAttribute("aria-busy");
        if (!b.querySelector(".boton-check")) {
            var c = document.createElement("span");
            c.className = "boton-check";
            c.innerHTML = CHECK;
            b.appendChild(c);
        }
        b.classList.add("listo");
    }

    function temblar(el) {
        if (!el || reducir) return;
        el.classList.remove("temblor");
        void el.offsetWidth;
        el.classList.add("temblor");
        el.addEventListener("animationend", function () { el.classList.remove("temblor"); }, { once: true });
    }

    // Envío normal (POST a su ruta): el botón muestra "enviando" y la página
    // no acepta un segundo envío mientras espera la respuesta.
    function alEnviar(e) {
        var f = e.target;
        if (!(f instanceof HTMLFormElement) || e.defaultPrevented) return;
        if (f.dataset.enviando === "1") { e.preventDefault(); return; }
        if (f.method.toLowerCase() !== "post") return;
        f.dataset.enviando = "1";
        ocupado(e.submitter || f.querySelector('[type="submit"]'), true);
    }

    // Al volver con el botón "atrás" (bfcache) los formularios quedan libres.
    window.addEventListener("pageshow", function (e) {
        if (!e.persisted) return;
        document.querySelectorAll("form[data-enviando]").forEach(function (f) { delete f.dataset.enviando; });
        document.querySelectorAll('[aria-busy="true"]').forEach(function (b) { ocupado(b, false); });
        var m = document.querySelector(".zona main");
        if (m) m.classList.remove("saliendo");
    });

    /* ───────── Menú lateral ───────── */

    function opciones(nav) { return Array.prototype.slice.call(nav.querySelectorAll(".menu-op")); }

    function ubicar(capa, op) {
        capa.style.transform = "translate(" + op.offsetLeft + "px," + op.offsetTop + "px)";
        capa.style.width = op.offsetWidth + "px";
        capa.style.height = op.offsetHeight + "px";
    }

    function iniciarMenu() {
        var menu = document.querySelector(".menu");
        if (!menu) return;
        var nav = menu.querySelector(".menu-nav");
        var ind = nav.querySelector(".menu-ind");
        var hov = nav.querySelector(".menu-hov");
        var etiqueta = document.querySelector(".menu-etiqueta");
        var ops = opciones(nav).filter(function (o) { return o.offsetParent !== null; });
        var activa = nav.querySelector('.menu-op[aria-current="page"]');
        var indice = activa ? ops.indexOf(activa) : -1;

        function colocarIndicador(sinAnim) {
            var a = nav.querySelector('.menu-op[aria-current="page"]');
            // En el teléfono, Perfil y Usuarios van en "Más": el haz marca ese botón.
            if ((!a || a.offsetParent === null) && nav.querySelector(".menu-mas.contiene-actual")) {
                a = nav.querySelector(".menu-mas");
            }
            if (!a || a.offsetParent === null) { ind.style.opacity = "0"; return; }
            ind.style.opacity = "1";
            if (sinAnim) nav.classList.add("menu-sin-anim");
            ubicar(ind, a);
            if (sinAnim) requestAnimationFrame(function () { requestAnimationFrame(function () { nav.classList.remove("menu-sin-anim"); }); });
        }

        // El indicador parte de la opción de la pantalla anterior y se desliza hasta la actual.
        var previo = parseInt(leer("prpagos-menu-indice") || "", 10);
        if (!reducir && !isNaN(previo) && ops[previo] && previo !== indice && activa) {
            nav.classList.add("menu-sin-anim");
            ubicar(ind, ops[previo]);
            requestAnimationFrame(function () {
                requestAnimationFrame(function () { nav.classList.remove("menu-sin-anim"); colocarIndicador(false); });
            });
        } else {
            colocarIndicador(true);
        }
        if (indice >= 0) guardar("prpagos-menu-indice", String(indice));
        guardar("prpagos-menu-visto", "1");
        // Terminada la cascada de entrada, se quita para que nada la reinicie.
        (window.alArrancar || function (fn) { fn(); })(function () {
            setTimeout(function () { raiz.classList.remove("menu-primera"); }, 1100);
        });

        // Fondo que sigue al ratón y etiqueta del modo compacto.
        nav.addEventListener("mouseover", function (e) {
            var op = e.target.closest(".menu-op");
            if (!op) return;
            if (!hov.classList.contains("listo")) {
                nav.classList.add("menu-sin-anim");
                ubicar(hov, op);
                void hov.offsetWidth;
                nav.classList.remove("menu-sin-anim");
                hov.classList.add("listo");
            }
            ubicar(hov, op);
            if (etiqueta) {
                var r = op.getBoundingClientRect();
                etiqueta.textContent = (op.querySelector(".texto-largo") || op).textContent.trim();
                etiqueta.style.top = (r.top + r.height / 2 - etiqueta.offsetHeight / 2) + "px";
                etiqueta.classList.add("visible");
            }
        });
        nav.addEventListener("mouseleave", function () {
            hov.classList.remove("listo");
            if (etiqueta) etiqueta.classList.remove("visible");
        });

        // Compactar o extender; la preferencia queda guardada en este dispositivo.
        var alternar = menu.querySelector(".menu-alternar");
        if (alternar) {
            alternar.addEventListener("click", function () {
                var compacto = raiz.getAttribute("data-menu") !== "compacto";
                raiz.setAttribute("data-menu", compacto ? "compacto" : "extendido");
                alternar.setAttribute("aria-expanded", String(!compacto));
                var t = alternar.querySelector(".texto-largo");
                if (t) t.textContent = compacto ? "Extender menú" : "Compactar menú";
                try { localStorage.setItem("prpagos-menu", compacto ? "compacto" : "extendido"); } catch (err) { /* nada */ }
            });
            alternar.setAttribute("aria-expanded", String(raiz.getAttribute("data-menu") !== "compacto"));
        }

        // Mientras el ancho cambia, el indicador sigue a su opción.
        if (window.ResizeObserver) new ResizeObserver(function () { colocarIndicador(true); }).observe(menu);
        window.addEventListener("resize", function () { colocarIndicador(true); });
        raiz.classList.add("menu-listo");

        // Al elegir una sección: la pantalla actual sale hacia el lado contrario.
        ops.forEach(function (op, i) {
            if (op.tagName !== "A") return;
            op.addEventListener("click", function (e) {
                if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0 || op === activa) return;
                guardar("prpagos-dir", i >= indice ? "1" : "-1");
                nav.querySelectorAll('[aria-current="page"]').forEach(function (x) { x.removeAttribute("aria-current"); });
                op.setAttribute("aria-current", "page");
                colocarIndicador(false);
                salir(e, op.href);
            });
        });
    }

    function salir(e, destino) {
        var main = document.querySelector(".zona main");
        if (!main || reducir) return;
        e.preventDefault();
        main.style.setProperty("--dir", leer("prpagos-dir") || "1");
        main.classList.add("saliendo");
        setTimeout(function () { window.location.href = destino; }, 170);
    }

    /* ───────── Hoja "Más" (teléfono) ───────── */
    function iniciarHoja() {
        var hoja = document.querySelector(".hoja-mas");
        var abrir = document.querySelector(".menu-mas");
        if (!hoja || !abrir || typeof hoja.showModal !== "function") return;
        function cerrar() {
            if (reducir) { hoja.close(); return; }
            hoja.classList.add("cerrando");
            setTimeout(function () { hoja.classList.remove("cerrando"); hoja.close(); }, 200);
        }
        abrir.addEventListener("click", function () { hoja.showModal(); abrir.setAttribute("aria-expanded", "true"); });
        hoja.addEventListener("close", function () { abrir.setAttribute("aria-expanded", "false"); });
        hoja.addEventListener("click", function (e) { if (e.target === hoja) cerrar(); });
        hoja.addEventListener("cancel", function (e) { e.preventDefault(); cerrar(); });
    }

    /* ───────── Bloques escalonados ───────── */
    function escalonar() {
        document.querySelectorAll(".escalonado").forEach(function (g) {
            Array.prototype.forEach.call(g.children, function (h, i) { h.style.setProperty("--i", i); });
        });
    }

    function iniciar() {
        document.querySelectorAll(".boton").forEach(prepararBoton);
        // Si la página llegó con un error, el botón y el aviso tiemblan una vez.
        var error = document.querySelector(".aviso.error, .aviso-acceso.error");
        if (error) {
            var f = error.parentElement && error.parentElement.querySelector("form");
            temblar(f ? f.querySelector('[type="submit"]') : null);
        }
        var main = document.querySelector(".zona main");
        if (main) main.style.setProperty("--dir", leer("prpagos-dir") || "1");
        guardar("prpagos-dir", "1");
        escalonar();
        iniciarMenu();
        iniciarHoja();
    }

    document.addEventListener("pointerdown", onda, { passive: true });
    document.addEventListener("submit", alEnviar);
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar);
    else iniciar();

    // Para acceso.js (paso del código, formularios que se deslizan).
    window.Efectos = { ocupado: ocupado, listo: listo, temblar: temblar, prepararBoton: prepararBoton };
})();
