// Formulario de registro: muestra solo las categorías del tipo elegido
// (ingreso o gasto), igual que el escritorio.
(function () {
    "use strict";
    var lista = document.getElementById("categoria_id");
    if (!lista) return;

    function filtrar() {
        var elegido = document.querySelector('input[name="tipo"]:checked');
        var tipo = elegido ? elegido.value : "GASTO";
        Array.prototype.forEach.call(lista.options, function (op) {
            var visible = op.dataset.tipo === "TODOS" || op.dataset.tipo === tipo;
            op.hidden = !visible;
            op.disabled = !visible;
            if (!visible && op.selected) lista.value = "";
        });
    }

    document.querySelectorAll('input[name="tipo"]').forEach(function (r) {
        r.addEventListener("change", filtrar);
    });
    filtrar();
})();
