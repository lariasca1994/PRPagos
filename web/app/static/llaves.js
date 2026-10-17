/* Llaves de acceso (WebAuthn): ingresar con Windows Hello, la huella, el
   rostro o el PIN del dispositivo, y registrarlas desde Perfil. El servidor
   verifica todo (app/llaves.py); aquí solo se convierten los datos entre JSON
   (base64url) y lo que piden navigator.credentials.create()/get().
   Los mensajes son los mismos del escritorio (VentanaLogin y PanelPerfil). */
(function () {
    "use strict";

    var soportado = !!(window.PublicKeyCredential && navigator.credentials);

    function aBytes(texto) {
        var b64 = texto.replace(/-/g, "+").replace(/_/g, "/");
        while (b64.length % 4) { b64 += "="; }
        var bin = atob(b64), bytes = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) { bytes[i] = bin.charCodeAt(i); }
        return bytes.buffer;
    }

    function aTexto(buffer) {
        if (!buffer) { return null; }
        var bytes = new Uint8Array(buffer), bin = "";
        for (var i = 0; i < bytes.length; i++) { bin += String.fromCharCode(bytes[i]); }
        return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    }

    function serializar(cred) {
        var r = cred.response, respuesta = { clientDataJSON: aTexto(r.clientDataJSON) };
        if (r.attestationObject) {
            respuesta.attestationObject = aTexto(r.attestationObject);
            respuesta.transports = r.getTransports ? r.getTransports() : [];
        } else {
            respuesta.authenticatorData = aTexto(r.authenticatorData);
            respuesta.signature = aTexto(r.signature);
            respuesta.userHandle = aTexto(r.userHandle);
        }
        return {
            id: cred.id, rawId: aTexto(cred.rawId), type: cred.type, response: respuesta,
            authenticatorAttachment: cred.authenticatorAttachment || null,
            clientExtensionResults: cred.getClientExtensionResults ? cred.getClientExtensionResults() : {}
        };
    }

    function mensajeError(error, creando) {
        if (error && error.name === "InvalidStateError" && creando) {
            return "Este dispositivo ya tiene una llave de acceso de PRPagos para tu cuenta.";
        }
        if (error && (error.name === "NotAllowedError" || error.name === "AbortError")) {
            return "Se canceló o se agotó el tiempo de la verificación.";
        }
        if (error && error.name === "NotSupportedError") {
            return "Este dispositivo no admite llaves de acceso.";
        }
        return (error && error.servidor) || "No se pudo usar la llave de acceso; inténtalo de nuevo.";
    }

    function enviar(url, cuerpo) {
        return fetch(url, {
            method: "POST",
            credentials: "same-origin",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(cuerpo || {})
        }).then(function (r) {
            return r.json().catch(function () { return {}; }).then(function (datos) {
                if (!r.ok || datos.error) {
                    var e = new Error(datos.error || "error");
                    e.servidor = datos.error || "No se pudo completar la solicitud; recarga la página.";
                    throw e;
                }
                return datos;
            });
        });
    }

    function mostrar(aviso, texto) {
        aviso.textContent = texto;
        aviso.hidden = false;
    }

    // ── Portada: Ingresar con llave de acceso ──
    var botonIngreso = document.getElementById("boton-llave");
    if (botonIngreso) {
        var avisoIngreso = document.getElementById("aviso-llave");
        if (!soportado) {
            botonIngreso.disabled = true;
            mostrar(avisoIngreso, "Este navegador no admite llaves de acceso.");
        }
        botonIngreso.addEventListener("click", function () {
            avisoIngreso.hidden = true;
            botonIngreso.disabled = true;
            enviar("/login/llave/opciones").then(function (o) {
                var publicKey = {
                    challenge: aBytes(o.challenge), rpId: o.rpId, timeout: o.timeout,
                    userVerification: o.userVerification,
                    allowCredentials: (o.allowCredentials || []).map(function (c) {
                        return { type: c.type, id: aBytes(c.id), transports: c.transports };
                    })
                };
                return navigator.credentials.get({ publicKey: publicKey });
            }).then(function (cred) {
                return enviar("/login/llave", serializar(cred));
            }).then(function (datos) {
                window.location.href = datos.ir;
            }).catch(function (error) {
                mostrar(avisoIngreso, mensajeError(error, false));
                botonIngreso.disabled = false;
            });
        });
    }

    // ── Perfil: Agregar este dispositivo ──
    var formulario = document.getElementById("form-llave");
    if (formulario) {
        var avisoPerfil = document.getElementById("aviso-llave");
        var botonAgregar = formulario.querySelector("button[type=submit]");
        if (!soportado) {
            botonAgregar.disabled = true;
            mostrar(avisoPerfil, "Este navegador no admite llaves de acceso.");
        }
        formulario.addEventListener("submit", function (evento) {
            evento.preventDefault();
            avisoPerfil.hidden = true;
            botonAgregar.disabled = true;
            var datos = { nombre: formulario.nombre.value, codigo: formulario.codigo.value };
            enviar("/perfil/llaves/opciones", datos).then(function (o) {
                var publicKey = {
                    challenge: aBytes(o.challenge), rp: o.rp, timeout: o.timeout,
                    user: { id: aBytes(o.user.id), name: o.user.name, displayName: o.user.displayName },
                    pubKeyCredParams: o.pubKeyCredParams, attestation: o.attestation,
                    authenticatorSelection: o.authenticatorSelection,
                    excludeCredentials: (o.excludeCredentials || []).map(function (c) {
                        return { type: c.type, id: aBytes(c.id), transports: c.transports };
                    })
                };
                return navigator.credentials.create({ publicKey: publicKey });
            }).then(function (cred) {
                return enviar("/perfil/llaves/registrar", serializar(cred));
            }).then(function (datos) {
                window.location.href = datos.ir;
            }).catch(function (error) {
                formulario.codigo.value = "";
                mostrar(avisoPerfil, mensajeError(error, true));
                botonAgregar.disabled = false;
            });
        });
    }
})();
