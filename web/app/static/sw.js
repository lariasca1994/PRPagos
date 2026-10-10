// Service worker de PRPagos: hace instalable la app (PWA) y muestra una
// página propia cuando no hay conexión.
//
// Solo se guardan en caché los archivos estáticos (estilos, scripts,
// íconos) y la página sin conexión. Las páginas con pagos, la sesión y las
// exportaciones a CSV siempre van a la red: nada con datos del usuario
// queda guardado en el dispositivo.
const CACHE = "prpagos-v1";
const OFFLINE = "/static/offline.html";
const PRECARGA = [
  OFFLINE,
  "/static/estilos.css",
  "/static/tema.js",
  "/static/iconos/icono.svg",
  "/static/iconos/icono-192.png",
];

self.addEventListener("install", (evento) => {
  evento.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(PRECARGA)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((claves) => Promise.all(claves.filter((c) => c !== CACHE).map((c) => caches.delete(c))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (evento) => {
  const pedido = evento.request;
  if (pedido.method !== "GET") return;
  const url = new URL(pedido.url);
  if (url.origin !== self.location.origin) return;

  // Navegación: siempre a la red; sin conexión, la página propia.
  if (pedido.mode === "navigate") {
    evento.respondWith(fetch(pedido).catch(() => caches.match(OFFLINE)));
    return;
  }

  // Estáticos: red primero (para tomar siempre la versión desplegada) y
  // caché como respaldo sin conexión.
  if (url.pathname.startsWith("/static/")) {
    evento.respondWith(
      fetch(pedido)
        .then((respuesta) => {
          if (respuesta.ok) {
            const copia = respuesta.clone();
            caches.open(CACHE).then((cache) => cache.put(pedido, copia));
          }
          return respuesta;
        })
        .catch(() => caches.match(pedido))
    );
  }
});
