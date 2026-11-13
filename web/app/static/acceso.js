/* Portada de acceso en una sola pantalla: ingresar, registrarse, recuperar la
   contraseña y el paso del código cambian sin recargar. El panel del recibo
   queda en su lugar (o se desliza al otro lado al ir a Registro) y solo se
   animan los formularios.

   Cada formulario se sigue enviando por POST a su misma ruta: el servidor
   valida, bloquea por intentos y redirige igual que siempre. Aquí solo se
   lee la página que responde y se muestra su formulario. Si algo falla, se
   navega de forma normal. La app de escritorio replica esto en VentanaLogin.

   Además: seis casillas para el código (sobre el mismo campo "codigo", para
   que el autocompletado, pegar y las pruebas sigan funcionando) y el candado
   que se abre con el código correcto. */
(function () {
    "use strict";
    var reducir = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var E = window.Efectos || { ocupado: function () {}, listo: function () {}, temblar: function () {}, prepararBoton: function () {} };
    var portada = document.querySelector(".portada");
    if (!portada || !window.fetch || !window.DOMParser || !history.pushState) {
        // Fuera de la portada (Perfil) o en navegadores viejos: solo las casillas y el anillo.
        iniciarPaso(document);
        return;
    }
    var RUTAS = /^\/(login|registro|recuperar|restablecer|logout)(\/|$)/;
    var ocupada = false;

    function espera(ms) { return new Promise(function (r) { setTimeout(r, reducir ? 0 : ms); }); }

    function tipo(url) {
        var p = new URL(url, location.href).pathname;
        if (p === "/registro") return "registro";
        if (p === "/recuperar" || p === "/restablecer") return "recuperar";
        if (p.indexOf("/login/") === 0) return "paso";
        return "login";
    }
    var RANGO = { login: 0, recuperar: 0, registro: 1, paso: 1 };

    function modo(desde, hacia) {
        if ((desde === "registro") !== (hacia === "registro")) return { m: "lado", dir: hacia === "registro" ? 1 : -1 };
        if (desde === "recuperar" || hacia === "recuperar") return { m: "arriba", dir: hacia === "recuperar" ? 1 : -1 };
        return { m: "avanza", dir: RANGO[hacia] >= RANGO[desde] ? 1 : -1 };
    }

    function seccion() { return portada.querySelector(".formulario-acceso"); }

    // El panel de marca y el formulario se cruzan (técnica FLIP: se mide antes y
    // después de cambiar de lado y se anima la diferencia).
    function invertir(si) {
        if (portada.classList.contains("invertida") === si) return;
        var piezas = [portada.querySelector(".lado"), portada.querySelector(".marca-panel")];
        var antes = piezas.map(function (p) { return p.getBoundingClientRect().left; });
        portada.classList.toggle("invertida", si);
        if (reducir || !piezas[0].animate) return;
        piezas.forEach(function (p, i) {
            var dx = antes[i] - p.getBoundingClientRect().left;
            if (!dx) return;
            p.animate([{ transform: "translateX(" + dx + "px)" }, { transform: "none" }],
                { duration: 650, easing: "cubic-bezier(.65,0,.35,1)" });
        });
    }

    // Repite la impresión del recibo, en versión corta.
    function reimprimir() {
        var r = portada.querySelector(".recibo");
        if (!r || reducir) return;
        var c = r.cloneNode(true);
        c.classList.add("rapido");
        r.replaceWith(c);
    }

    // Los <script> que llegan dentro del formulario (llaves.js) se vuelven a ejecutar.
    function revivirScripts(raiz) {
        raiz.querySelectorAll("script").forEach(function (viejo) {
            var s = document.createElement("script");
            if (viejo.src) s.src = viejo.src; else s.textContent = viejo.textContent;
            viejo.replaceWith(s);
        });
    }

    async function cambiar(doc, url, empujar) {
        var nueva = doc.querySelector(".formulario-acceso");
        var vieja = seccion();
        var desde = tipo(location.href), hacia = tipo(url);
        var t = modo(desde, hacia);
        vieja.style.setProperty("--dir", t.dir);
        vieja.dataset.modo = t.m;
        vieja.classList.add("sale");
        await espera(170);

        var invertida = doc.querySelector(".portada.invertida") !== null;
        nueva = document.importNode(nueva, true);
        nueva.style.setProperty("--dir", t.dir);
        nueva.dataset.modo = t.m;
        nueva.classList.add("entra");
        Array.prototype.forEach.call(nueva.children, function (h, i) { h.style.setProperty("--n", i); });
        vieja.replaceWith(nueva);
        invertir(invertida);
        if (t.m === "lado") reimprimir();

        document.title = doc.title;
        if (empujar === "push") history.pushState({ acceso: true }, "", url);
        else if (empujar === "replace") history.replaceState({ acceso: true }, "", url);
        revivirScripts(nueva);
        iniciarPaso(nueva);
        var foco = nueva.querySelector("[autofocus]") || nueva.querySelector("h1");
        if (foco) {
            if (foco.tagName === "H1") foco.setAttribute("tabindex", "-1");
            foco.focus({ preventScroll: true });
        }
    }

    async function traer(url, opciones) {
        var r = await fetch(url, Object.assign({ credentials: "same-origin" }, opciones || {}));
        var html = (r.headers.get("content-type") || "").indexOf("text/html") >= 0 ? await r.text() : null;
        return { r: r, html: html, doc: html ? new DOMParser().parseFromString(html, "text/html") : null };
    }

    // Respuesta de error sin formulario (por ejemplo, la pantalla de
    // mantenimiento si Oracle no responde): se muestra tal cual, sin
    // repetir la petición. Esa pantalla vuelve sola con GET.
    function mostrarPagina(x) {
        if (!x.html) { location.assign(x.r.url); return; }
        document.open();
        document.write(x.html);
        document.close();
    }

    async function ir(url, empujar) {
        if (ocupada) return;
        ocupada = true;
        try {
            var x = await traer(url);
            if (!x.doc || !x.doc.querySelector(".formulario-acceso")) {
                if (x.r.ok) location.assign(x.r.url); else mostrarPagina(x);
                return;
            }
            await cambiar(x.doc, x.r.url, empujar);
        } catch (err) {
            location.assign(url);
        } finally {
            ocupada = false;
        }
    }

    // Enlaces entre pasos (Regístrate, Olvidé mi contraseña, Volver, Usar otra cuenta…).
    portada.addEventListener("click", function (e) {
        var a = e.target.closest("a[href]");
        if (!a || e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || a.target) return;
        var u = new URL(a.href, location.href);
        if (u.origin !== location.origin || !RUTAS.test(u.pathname)) return;
        e.preventDefault();
        ir(u.href, "push");
    });

    window.addEventListener("popstate", function () { ir(location.href, null); });
    history.replaceState({ acceso: true }, "", location.href);

    function avisoError(sec, nuevo) {
        var actual = sec.querySelector(".aviso-acceso");
        var copia = document.importNode(nuevo, true);
        if (actual) actual.replaceWith(copia);
        else sec.querySelector(".acceso-subtitulo").after(copia);
        return copia;
    }

    // Envíos: el mismo POST a la misma ruta; la respuesta decide qué se muestra.
    portada.addEventListener("submit", async function (e) {
        var form = e.target;
        if (!form.closest(".formulario-acceso") || form.method.toLowerCase() !== "post" || form.dataset.normal !== undefined) return;
        e.preventDefault();
        if (form.dataset.enviando === "1") return;
        form.dataset.enviando = "1";
        var boton = e.submitter || form.querySelector('[type="submit"]');
        E.ocupado(boton, true);
        var datos;
        try { datos = new FormData(form, e.submitter); } catch (err) {
            datos = new FormData(form);
            if (e.submitter && e.submitter.name) datos.append(e.submitter.name, e.submitter.value);
        }
        var sec = seccion();
        var candado = sec.querySelector(".candado");
        var actual = tipo(location.href);
        if (candado) candado.classList.add("verificando");
        try {
            var x = await traer(form.action, { method: "POST", body: new URLSearchParams(datos) });
            var nuevaSec = x.doc && x.doc.querySelector(".formulario-acceso");
            if (candado) candado.classList.remove("verificando");
            if (!nuevaSec && !x.r.ok) { mostrarPagina(x); return; }
            if (!nuevaSec) {
                // Sesión abierta: el candado se abre y el botón pasa a ✓ antes de entrar.
                if (candado) candado.classList.add("abierto");
                E.listo(boton);
                await espera(candado ? 700 : 350);
                location.assign(x.r.url);
                return;
            }
            var error = nuevaSec.querySelector(".aviso-acceso.error");
            if (error && tipo(x.r.url) === actual) {
                // Error en el mismo paso: se queda el formulario, aparece el aviso y todo tiembla.
                avisoError(sec, error);
                history.replaceState({ acceso: true }, "", x.r.url);
                E.ocupado(boton, false);
                delete form.dataset.enviando;
                E.temblar(boton);
                var cas = sec.querySelector(".casillas");
                if (cas) { E.temblar(cas); limpiarCasillas(cas); }
                if (candado) { candado.classList.remove("sacudir"); void candado.offsetWidth; candado.classList.add("sacudir"); }
                return;
            }
            if (candado && !error) { candado.classList.add("abierto"); E.listo(boton); await espera(650); }
            await cambiar(x.doc, x.r.url, "push");
        } catch (err) {
            if (candado) candado.classList.remove("verificando");
            E.ocupado(boton, false);
            delete form.dataset.enviando;
            var p = document.createElement("p");
            p.className = "aviso-acceso error";
            p.setAttribute("role", "alert");
            p.textContent = "No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.";
            avisoError(sec, p);
            E.temblar(boton);
        }
    });

    iniciarPaso(document);

    /* ───────── Paso del código: casillas, anillo y candado ───────── */

    function limpiarCasillas(cas) {
        var input = cas.querySelector("input");
        if (cas.classList.contains("modo-respaldo")) return;
        setTimeout(function () { input.value = ""; input.dispatchEvent(new Event("input")); input.focus(); }, reducir ? 0 : 420);
    }

    function iniciarPaso(raiz) {
        if (raiz.querySelectorAll) raiz.querySelectorAll(".boton").forEach(function (b) { E.prepararBoton(b); });
        (raiz.querySelectorAll ? raiz : document).querySelectorAll("input[data-casillas]").forEach(casillas);
        (raiz.querySelectorAll ? raiz : document).querySelectorAll("[data-anillo]").forEach(anillo);
    }
    window.AccesoCasillas = iniciarPaso;

    function casillas(input) {
        if (input.dataset.listo) return;
        input.dataset.listo = "1";
        var n = 6;
        var largoRespaldo = input.getAttribute("maxlength") || "10";
        var caja = document.createElement("div");
        caja.className = "casillas";
        var vista = document.createElement("div");
        vista.className = "casillas-vista";
        vista.setAttribute("aria-hidden", "true");
        for (var i = 0; i < n; i++) vista.appendChild(document.createElement("span"));
        input.before(caja);
        caja.appendChild(input);
        caja.appendChild(vista);
        input.setAttribute("maxlength", String(n));
        input.setAttribute("inputmode", "numeric");
        input.removeAttribute("placeholder");
        var cajas = vista.children;

        function pintar() {
            var v = input.value;
            for (var k = 0; k < n; k++) {
                var tenia = cajas[k].textContent;
                cajas[k].textContent = v[k] || "";
                cajas[k].classList.toggle("llena", !!v[k]);
                if (v[k] && !tenia && !reducir) { cajas[k].classList.remove("pulso"); void cajas[k].offsetWidth; cajas[k].classList.add("pulso"); }
                cajas[k].classList.toggle("activa", document.activeElement === input && k === Math.min(v.length, n - 1));
            }
            caja.classList.toggle("completa", v.length === n);
        }

        function respaldo(si) {
            caja.classList.toggle("modo-respaldo", si);
            input.setAttribute("maxlength", si ? largoRespaldo : String(n));
            input.setAttribute("inputmode", si ? "text" : "numeric");
            if (cambio) cambio.textContent = si ? "Usar el código de 6 dígitos" : "Usar un código de respaldo";
            if (!si) input.value = input.value.replace(/\D/g, "").slice(0, n);
            pintar();
            input.focus();
        }

        input.addEventListener("input", function () {
            if (caja.classList.contains("modo-respaldo")) return;
            // Un código de respaldo pegado (con letras) pasa solo al campo normal.
            if (cambio && /[^\d\s]/.test(input.value)) { respaldo(true); return; }
            var limpio = input.value.replace(/\D/g, "").slice(0, n);
            if (limpio !== input.value) input.value = limpio;
            pintar();
        });
        ["focus", "blur", "keyup", "click"].forEach(function (ev) {
            input.addEventListener(ev, function () {
                if (!caja.classList.contains("modo-respaldo") && ev !== "blur") {
                    var l = input.value.length;
                    try { input.setSelectionRange(l, l); } catch (err) { /* nada */ }
                }
                pintar();
            });
        });

        var cambio = null;
        if (input.dataset.respaldo !== undefined) {
            cambio = document.createElement("button");
            cambio.type = "button";
            cambio.className = "enlace-boton cambio-respaldo";
            cambio.textContent = "Usar un código de respaldo";
            caja.after(cambio);
            cambio.addEventListener("click", function () { respaldo(!caja.classList.contains("modo-respaldo")); });
        }
        pintar();
    }

    // Anillo: en modo app, el tiempo que le queda al código actual (cambia cada
    // 30 s); en modo correo, la cuenta regresiva hasta que vence.
    function anillo(el) {
        if (el.dataset.listo) return;
        el.dataset.listo = "1";
        var texto = el.querySelector("[data-anillo-texto]");
        var arco = el.querySelector(".anillo-valor");
        var vence = el.dataset.anillo === "correo" ? Date.now() + (parseInt(el.dataset.minutos, 10) || 10) * 60000 : 0;
        function paso() {
            if (!document.contains(el)) { clearInterval(id); return; }
            var resto, total, msg;
            if (vence) {
                total = (parseInt(el.dataset.minutos, 10) || 10) * 60;
                resto = Math.max(0, Math.round((vence - Date.now()) / 1000));
                var m = Math.floor(resto / 60), s = resto % 60;
                msg = resto ? "El código vence en " + m + ":" + (s < 10 ? "0" : "") + s : "El código venció. Vuelve a ingresar para recibir otro.";
            } else {
                total = 30;
                resto = 30 - Math.floor(Date.now() / 1000) % 30;
                msg = "El código de tu app cambia en " + resto + " s";
            }
            arco.style.strokeDashoffset = String(1 - resto / total);
            texto.textContent = msg;
            el.classList.toggle("urgente", resto <= (vence ? 60 : 5));
        }
        var id = setInterval(paso, 1000);
        paso();
    }
})();
