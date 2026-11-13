// Animación de entrada de la app instalada (PWA). Solo al abrir la app desde el ícono del teléfono o del escritorio
// (modo "standalone"), una vez por apertura, entre 1,1 y 1,6 segundos. Nunca bloquea: si algo falla no se muestra.
//
// Mientras se ve, las animaciones de entrada de la app (barra de vidrio, bloques, cifras y gráficas) quedan en
// pausa (clase "arrancando" en <html>) y empiezan cuando la animación se desvanece; si no, terminarían debajo
// sin que nadie las viera. window.alArrancar(fn) corre fn en ese momento (o de inmediato si no hay animación).
(function () {
  var raiz = document.documentElement;
  var pendientes = [];
  window.alArrancar = function (fn) {
    if (raiz.classList.contains("arrancando")) pendientes.push(fn);
    else fn();
  };
  function soltar() {
    raiz.classList.remove("arrancando");
    var lista = pendientes;
    pendientes = [];
    lista.forEach(function (fn) { try { fn(); } catch (e) { /* una animación no debe romper la app */ } });
  }
  var el = document.getElementById("arranque");
  if (!el) return;
  var instalada = false;
  var visto = false;
  try {
    instalada = window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone === true;
    visto = sessionStorage.getItem("arranque-visto") === "1";
  } catch (e) {
    instalada = false;
  }
  if (!instalada || visto) {
    el.parentNode.removeChild(el);
    return;
  }
  try {
    sessionStorage.setItem("arranque-visto", "1");
  } catch (e) {
    // Sin almacenamiento: se mostraría en cada carga, así que no se muestra.
    el.parentNode.removeChild(el);
    return;
  }

  var reducir = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var inicio = Date.now();
  var minimo = reducir ? 300 : 1100;
  var maximo = 1600;
  var cerrado = false;
  el.hidden = false;
  raiz.classList.add("arrancando");

  function cerrar() {
    if (cerrado) return;
    cerrado = true;
    el.className += " arranque-salida";
    soltar();
    window.setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 320);
  }

  function listo() {
    window.setTimeout(cerrar, Math.max(0, minimo - (Date.now() - inicio)));
  }

  if (document.readyState === "complete") listo();
  else window.addEventListener("load", listo, { once: true });
  window.setTimeout(cerrar, maximo);
})();
