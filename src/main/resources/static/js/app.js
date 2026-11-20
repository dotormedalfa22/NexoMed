'use strict';
/* =====================================================================
   NexoMed — interfaz web (sin dependencias).
   Consume la API REST de la misma aplicación. El DOM se arma con
   textContent: ningún dato del servidor se inserta como HTML.
   ===================================================================== */

const API = 'api';
const MINUTOS_INACTIVIDAD = 15;
const SEGUNDOS_AVISO = 60;
/** El círculo de "Consultando" solo aparece si la consulta tarda más que esto (evita parpadeos). */
const MS_ANTES_DE_GIRAR = 400;

const $ = (sel) => document.querySelector(sel);

const estado = {
  sesion: null,          // { token, expira, nombre, perfil }
  perfil: null,          // /auth/yo
  menu: [],              // /menu
  ultimaActividad: Date.now(),
  renovando: false,
  consultas: 0,
};

/* ---------------------------------------------------------------------
   Utilidades
   --------------------------------------------------------------------- */
/** Crea un elemento: h('a', { href, class, text, onclick }, hijo1, [hijo2, hijo3]). */
function h(etiqueta, atributos = {}, ...hijos) {
  const el = document.createElement(etiqueta);
  for (const [k, v] of Object.entries(atributos || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'text') el.textContent = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
    else if (k === 'value') el.value = v;
    else if (k === 'checked') el.checked = true;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const hijo of hijos.flat(Infinity)) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    el.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
  }
  return el;
}

function avisar(texto) {
  const aviso = $('#aviso');
  aviso.textContent = texto;
  aviso.hidden = false;
  clearTimeout(avisar.t);
  avisar.t = setTimeout(() => { aviso.hidden = true; }, 5000);
}

function mensaje(caja, texto, tipo = 'error') {
  caja.className = `mensaje mensaje--${tipo}`;
  caja.textContent = texto;
  caja.hidden = !texto;
}

function textoError(err) {
  const lista = (err.cuerpo && err.cuerpo.errores) || [];
  return lista.length ? `${err.message}: ${lista.join('; ')}` : err.message;
}

/** La base guarda los textos sin acentos; en pantalla se muestran con acentos. */
const ACENTOS = {
  almacen: 'almacén', catalogos: 'catálogos', bitacora: 'bitácora', distribucion: 'distribución',
  administracion: 'administración', recepcion: 'recepción', ordenes: 'órdenes', menu: 'menú', menus: 'menús',
  contrasena: 'contraseña', numerico: 'numérico', preparacion: 'preparación', opcion: 'opción', opciones: 'opciones',
  catalogo: 'catálogo', regimen: 'régimen', fisica: 'física', fisicas: 'físicas', economica: 'económica',
  economicas: 'económicas', mexico: 'México', demas: 'demás', agricolas: 'agrícolas', silvicolas: 'silvícolas',
  tecnologicas: 'tecnológicas', traves: 'través', produccion: 'producción', enajenacion: 'enajenación',
  adquisicion: 'adquisición', obtencion: 'obtención', incorporacion: 'incorporación', leon: 'León',
  queretaro: 'Querétaro', potosi: 'Potosí', yucatan: 'Yucatán', michoacan: 'Michoacán',
};
function conAcentos(texto) {
  return String(texto ?? '').replace(/[A-Za-zñÑ]+/g, (palabra) => {
    const c = ACENTOS[palabra.toLowerCase()];
    if (!c) return palabra;
    if (palabra.length > 1 && palabra === palabra.toUpperCase()) return c.toUpperCase();
    return palabra[0] === palabra[0].toUpperCase() ? c[0].toUpperCase() + c.slice(1) : c;
  });
}

function fechaHora(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('es-MX', { dateStyle: 'long', timeStyle: 'short' });
}

/* ---------------------------------------------------------------------
   Consultando: círculo giratorio mientras hay consultas en curso
   --------------------------------------------------------------------- */
function empezarConsulta(texto = 'Consultando información…') {
  estado.consultas += 1;
  $('#cargando-texto').textContent = texto;
  clearTimeout(empezarConsulta.t);
  empezarConsulta.t = setTimeout(() => { if (estado.consultas > 0) $('#cargando').hidden = false; }, MS_ANTES_DE_GIRAR);
}
function terminarConsulta() {
  estado.consultas = Math.max(0, estado.consultas - 1);
  if (estado.consultas === 0) { clearTimeout(empezarConsulta.t); $('#cargando').hidden = true; }
}

/* ---------------------------------------------------------------------
   API
   --------------------------------------------------------------------- */
class ErrorApi extends Error {
  constructor(status, cuerpo) {
    super((cuerpo && cuerpo.detail) || `Error ${status}`);
    this.status = status;
    this.cuerpo = cuerpo || {};
  }
}

/**
 * Llama a la API. Con { silencioso: true } no muestra el círculo (renovación de sesión,
 * avisos en segundo plano, o cuando la pantalla ya tiene su propio indicador).
 */
async function api(metodo, ruta, cuerpo, { silencioso = false } = {}) {
  const opciones = { method: metodo, headers: {} };
  if (estado.sesion) opciones.headers.Authorization = 'Bearer ' + estado.sesion.token;
  if (cuerpo instanceof FormData) {
    opciones.body = cuerpo;          // archivo: el navegador pone multipart/form-data con su separador
  } else if (cuerpo !== undefined) {
    opciones.headers['Content-Type'] = 'application/json';
    opciones.body = JSON.stringify(cuerpo);
  }
  if (!silencioso) empezarConsulta();
  let respuesta;
  try {
    respuesta = await fetch(`${API}${ruta}`, opciones);
  } catch {
    throw new ErrorApi(0, { detail: 'No hay conexión con el servidor. Revise su red e intente de nuevo.' });
  } finally {
    if (!silencioso) terminarConsulta();
  }
  if (respuesta.status === 401 && estado.sesion && ruta !== '/auth/login') {
    cerrarSesion('Su sesión expiró. Inicie sesión de nuevo.', false);
    throw new ErrorApi(401, { detail: 'Sesión expirada' });
  }
  const contenido = await respuesta.text();
  let datos = contenido;
  if (contenido) {
    try { datos = JSON.parse(contenido); } catch { datos = { detail: contenido }; }
  }
  if (!respuesta.ok) throw new ErrorApi(respuesta.status, typeof datos === 'object' ? datos : { detail: datos });
  return datos;
}

/* ---------------------------------------------------------------------
   Descargando: barra de progreso. Real si el servidor manda Content-Length;
   si no, barra indeterminada. Uso: await descargar('/reportes/x.xlsx', 'x.xlsx')
   --------------------------------------------------------------------- */
async function descargar(ruta, nombreArchivo, { metodo = 'GET', cuerpo } = {}) {
  const capa = $('#descargando');
  const barra = capa.querySelector('.barra-progreso');
  const avance = capa.querySelector('.barra-progreso__avance');
  const detalle = $('#descarga-detalle');
  barra.classList.add('indeterminada');
  barra.removeAttribute('aria-valuenow');
  avance.style.width = '';
  detalle.textContent = '';
  capa.hidden = false;
  try {
    const opciones = { method: metodo, headers: { Authorization: 'Bearer ' + estado.sesion.token } };
    if (cuerpo !== undefined) { opciones.headers['Content-Type'] = 'application/json'; opciones.body = JSON.stringify(cuerpo); }
    const r = await fetch(`${API}${ruta}`, opciones);
    if (!r.ok) {
      let det = {}; try { det = await r.json(); } catch { /* sin detalle */ }
      throw new ErrorApi(r.status, det);
    }
    const total = Number(r.headers.get('Content-Length')) || 0;
    const lector = r.body.getReader();
    const partes = [];
    let recibido = 0;
    if (total) barra.classList.remove('indeterminada');
    for (;;) {
      const { done, value } = await lector.read();
      if (done) break;
      partes.push(value);
      recibido += value.length;
      if (total) {
        const pct = Math.min(100, Math.round((recibido / total) * 100));
        avance.style.width = pct + '%';
        barra.setAttribute('aria-valuenow', String(pct));
        detalle.textContent = `${pct} % · ${tamano(recibido)} de ${tamano(total)}`;
      } else {
        detalle.textContent = `${tamano(recibido)} recibidos`;
      }
    }
    const url = URL.createObjectURL(new Blob(partes, { type: r.headers.get('Content-Type') || 'application/octet-stream' }));
    const a = h('a', { href: url, download: nombreArchivo });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  } finally {
    capa.hidden = true;
  }
}
function tamano(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/* ---------------------------------------------------------------------
   Sesión y cierre por inactividad (15 minutos)
   --------------------------------------------------------------------- */
function guardarSesion(token) {
  estado.sesion = {
    token: token.accessToken, expira: Date.now() + token.expiraEnSegundos * 1000, nombre: token.nombre, perfil: token.perfil,
  };
  try { sessionStorage.setItem('nexomed.sesion', JSON.stringify(estado.sesion)); } catch { /* sin almacenamiento */ }
}

function cerrarSesion(motivo, avisarServidor = true) {
  if (estado.sesion && avisarServidor) {
    api('POST', '/auth/salir', { motivo: motivo || 'Cierre manual' }, { silencioso: true }).catch(() => {});
  }
  estado.sesion = null;
  estado.perfil = null;
  estado.menu = [];
  try { sessionStorage.removeItem('nexomed.sesion'); } catch { /* nada */ }
  const di = $('#dialogo-inactividad'); if (di.open) di.close();
  alternarMenuUsuario(false);
  $('#pantalla-app').hidden = true;
  $('#contenido').replaceChildren();
  $('#menu-opciones').replaceChildren();
  $('#pantalla-login').hidden = false;
  $('#form-login').reset();
  const err = $('#login-error');
  mensaje(err, motivo || '', motivo && motivo.includes('correctamente') ? 'exito' : 'alerta');
  document.title = 'NexoMed · Iniciar sesión';
  history.replaceState(null, '', location.pathname);
  $('#login-usuario').focus();
}

function registrarActividad() {
  if (!estado.sesion) return;
  if ($('#dialogo-inactividad').open) return; // durante el aviso solo cuenta el botón
  estado.ultimaActividad = Date.now();
}
['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll'].forEach((ev) =>
  document.addEventListener(ev, registrarActividad, { passive: true, capture: true }));
let ultimoMovimiento = 0;
document.addEventListener('pointermove', () => {
  const ahora = Date.now();
  if (ahora - ultimoMovimiento > 5000) { ultimoMovimiento = ahora; registrarActividad(); }
}, { passive: true });

async function vigilarSesion() {
  if (!estado.sesion) return;
  const inactivo = Date.now() - estado.ultimaActividad;
  const restante = MINUTOS_INACTIVIDAD * 60000 - inactivo;
  const dialogo = $('#dialogo-inactividad');

  if (restante <= 0 || Date.now() >= estado.sesion.expira) {
    cerrarSesion('La sesión se cerró tras 15 minutos sin actividad.');
    return;
  }
  if (restante <= SEGUNDOS_AVISO * 1000) {
    $('#segundos-inactividad').textContent = Math.ceil(restante / 1000);
    if (!dialogo.open) dialogo.showModal();
  } else if (dialogo.open) {
    dialogo.close();
  }
  // Renueva el token solo si hubo actividad reciente: sin actividad, el token vence solo.
  const tokenRestante = estado.sesion.expira - Date.now();
  if (tokenRestante < 11 * 60000 && inactivo < 60000 && !estado.renovando) {
    estado.renovando = true;
    try { guardarSesion(await api('POST', '/auth/renovar', undefined, { silencioso: true })); } catch { /* reintenta */ }
    estado.renovando = false;
  }
  const min = Math.floor(restante / 60000);
  const seg = Math.floor((restante % 60000) / 1000);
  const etiqueta = $('#sesion-restante');
  etiqueta.textContent = `Sesión: se cierra en ${min}:${String(seg).padStart(2, '0')} sin actividad`;
  etiqueta.classList.toggle('urgente', restante < 2 * 60000);
}
setInterval(vigilarSesion, 1000);

$('#seguir-trabajando').addEventListener('click', () => {
  estado.ultimaActividad = Date.now();
  $('#dialogo-inactividad').close();
  vigilarSesion();
});
$('#salir-ahora').addEventListener('click', () => cerrarSesion('Cerró sesión correctamente.'));
$('#dialogo-inactividad').addEventListener('cancel', (e) => {
  e.preventDefault();
  estado.ultimaActividad = Date.now();
  $('#dialogo-inactividad').close();
});

/* ---------------------------------------------------------------------
   Inicio de sesión
   --------------------------------------------------------------------- */
/** Muestra u oculta el texto de un campo de contraseña; el botón dice qué hará el siguiente clic. */
function alternarContrasena(input, boton) {
  const ver = input.type === 'password';
  input.type = ver ? 'text' : 'password';
  boton.textContent = ver ? 'Ocultar' : 'Mostrar';
  boton.setAttribute('aria-pressed', String(ver));
}
$('#ver-contrasena').addEventListener('click', (e) => alternarContrasena($('#login-contrasena'), e.currentTarget));

$('#form-login').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  const error = $('#login-error');
  const btn = form.querySelector('button[type=submit]');
  const usuario = form.usuario.value.trim();
  const contrasena = form.contrasena.value;
  error.hidden = true;
  if (!usuario || !contrasena) {
    mensaje(error, 'Escriba su usuario y su contraseña.');
    (usuario ? form.contrasena : form.usuario).focus();
    return;
  }
  // El botón tiene su propio círculo giratorio: la consulta va en silencio.
  btn.disabled = true;
  btn.querySelector('.giro').hidden = false;
  btn.querySelector('.boton__texto').textContent = 'Verificando…';
  try {
    guardarSesion(await api('POST', '/auth/login', { usuario, contrasena }, { silencioso: true }));
    estado.ultimaActividad = Date.now();
    await iniciarApp();
  } catch (err) {
    estado.sesion = null;
    mensaje(error, err.status === 423
      ? 'Cuenta bloqueada temporalmente por intentos fallidos. Espere 15 minutos o avise al administrador.'
      : err.message);
    form.contrasena.select();
  } finally {
    btn.disabled = false;
    btn.querySelector('.giro').hidden = true;
    btn.querySelector('.boton__texto').textContent = 'Entrar';
  }
});

/* ---------------------------------------------------------------------
   Menú lateral (como en SALPE): sale de /api/menu y se configura desde
   Usuario › Configurar menú. Menús y opciones en orden alfabético.
   --------------------------------------------------------------------- */
const I = {
  inicio: 'M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  almacen: 'M3 21V8l9-5 9 5v13M7 21v-8h10v8M7 17h10',
  carga: 'M12 15V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  catalogos: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  consultas: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
  dashboard: 'M3 3h8v10H3zM13 3h8v6h-8zM13 11h8v10h-8zM3 15h8v6H3z',
  entradas: 'M12 3v12M7 10l5 5 5-5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  impresos: 'M6 9V3h12v6M6 18H4a1 1 0 0 1-1-1v-6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v6a1 1 0 0 1-1 1h-2M6 14h12v7H6z',
  inventario: 'M4 7l8-4 8 4-8 4zM4 7v10l8 4 8-4V7M12 11v10',
  reportes: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  salidas: 'M1 4h14v12H1zM15 9h4l3 3v4h-7M5.5 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18.5 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  usuario: 'M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0zM4 21a8 8 0 0 1 16 0',
  carpeta: 'M3 6a1 1 0 0 1 1-1h5l2 2h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z',
};
const NOMBRE_ICONO = {
  almacen: 'Almacén', carga: 'Carga de archivos', catalogos: 'Catálogos', consultas: 'Consultas (lupa)',
  dashboard: 'Dashboard (tablero)', entradas: 'Entradas', impresos: 'Impresos (impresora)', inventario: 'Inventario (caja)',
  reportes: 'Reportes (gráfica)', salidas: 'Salidas (camión)', usuario: 'Usuario', carpeta: 'Carpeta',
};

function icono(nombre) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('width', '20'); svg.setAttribute('height', '20');
  svg.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', I[nombre] || I.carpeta); p.setAttribute('fill', 'none'); p.setAttribute('stroke', 'currentColor');
  p.setAttribute('stroke-width', '2'); p.setAttribute('stroke-linecap', 'round'); p.setAttribute('stroke-linejoin', 'round');
  svg.append(p);
  return svg;
}

const preferenciaMenu = {
  leer(clave) { try { return localStorage.getItem(`nexomed.menu.${clave}`); } catch { return null; } },
  guardar(clave, valor) { try { localStorage.setItem(`nexomed.menu.${clave}`, valor); } catch { /* sin almacenamiento */ } },
};
const esMovil = () => window.matchMedia('(max-width: 900px)').matches;

function pintarMenu() {
  const items = [h('li', {}, h('a', { class: 'menu__enlace', href: '#inicio', 'data-ruta': 'inicio', title: 'Inicio' },
    icono('inicio'), h('span', { class: 'menu__texto', text: 'Inicio' })))];
  items.push(h('li', { class: 'menu__separador', role: 'presentation' }));
  estado.menu.forEach((m, i) => {
    const nombre = conAcentos(m.nombre);
    const grupo = `g${i}`;
    const idSub = `menu-${grupo}`;
    const titulo = h('button', { type: 'button', class: 'menu__titulo', 'aria-expanded': 'false', 'aria-controls': idSub, title: nombre },
      icono(m.icono), h('span', { class: 'menu__texto', text: nombre }), h('span', { class: 'menu__flecha', 'aria-hidden': 'true' }));
    const hijos = m.opciones.length
      ? m.opciones.map((o) => h('li', {}, h('a', { class: 'menu__enlace', href: `#${o.ruta}`, 'data-ruta': o.ruta },
        h('span', { class: 'menu__texto', text: conAcentos(o.nombre) }))))
      : [h('li', { class: 'menu__vacio', text: 'En preparación' })];
    const li = h('li', { class: 'menu__grupo', 'data-grupo': grupo }, titulo,
      h('ul', { class: 'menu__sub', id: idSub }, h('li', { class: 'menu__sub-titulo', 'aria-hidden': 'true', text: nombre }), hijos));
    titulo.addEventListener('click', () => abrirGrupo(grupo, !li.classList.contains('abierto')));
    items.push(li);
  });
  $('#menu-opciones').replaceChildren(...items);
  marcarMenu();
}

/** Abre una sección y cierra las demás, como en SALPE. */
function abrirGrupo(grupo, abrir = true) {
  document.querySelectorAll('#menu-opciones .menu__grupo').forEach((li) => {
    const si = abrir && li.dataset.grupo === grupo;
    li.classList.toggle('abierto', si);
    li.querySelector('.menu__titulo').setAttribute('aria-expanded', String(si));
  });
}

function marcarMenu() {
  const ruta = rutaActual();
  let grupoActual = null;
  document.querySelectorAll('#menu-opciones a[data-ruta]').forEach((a) => {
    if (a.dataset.ruta === ruta) {
      a.setAttribute('aria-current', 'page');
      grupoActual = a.closest('.menu__grupo')?.dataset.grupo || null;
    } else a.removeAttribute('aria-current');
  });
  document.querySelectorAll('#menu-opciones .menu__grupo').forEach((li) => li.classList.toggle('activo', li.dataset.grupo === grupoActual));
  if (grupoActual) abrirGrupo(grupoActual, true);
}

/** Escritorio: menú completo o solo iconos (se recuerda). Móvil: se muestra u oculta sobre el contenido. */
function alternarMenu(mostrar) {
  const app = $('#pantalla-app');
  if (esMovil()) {
    const abierto = mostrar ?? !app.classList.contains('menu-abierto');
    app.classList.toggle('menu-abierto', abierto);
    $('#menu-velo').hidden = !abierto;
    $('#alternar-menu').setAttribute('aria-expanded', String(abierto));
    $('#alternar-menu-texto').textContent = abierto ? 'Ocultar menú' : 'Mostrar menú';
    if (abierto) $('#menu-opciones a, #menu-opciones button')?.focus();
    return;
  }
  const completo = mostrar ?? app.classList.contains('menu-mini');
  app.classList.toggle('menu-mini', !completo);
  preferenciaMenu.guardar('mini', completo ? '0' : '1');
  $('#alternar-menu').setAttribute('aria-expanded', String(completo));
  $('#alternar-menu-texto').textContent = completo ? 'Reducir menú a iconos' : 'Mostrar menú completo';
}
function prepararMenu() {
  const app = $('#pantalla-app');
  app.classList.toggle('menu-mini', !esMovil() && preferenciaMenu.leer('mini') === '1');
  app.classList.remove('menu-abierto');
  $('#menu-velo').hidden = true;
  const completo = !app.classList.contains('menu-mini');
  $('#alternar-menu').setAttribute('aria-expanded', String(esMovil() ? false : completo));
  $('#alternar-menu-texto').textContent = esMovil() ? 'Mostrar menú' : (completo ? 'Reducir menú a iconos' : 'Mostrar menú completo');
}
$('#alternar-menu').addEventListener('click', () => alternarMenu());
$('#menu-velo').addEventListener('click', () => alternarMenu(false));
window.matchMedia('(max-width: 900px)').addEventListener('change', prepararMenu);

/* Menú del usuario en la barra superior */
function alternarMenuUsuario(abrir) {
  const boton = $('#boton-usuario');
  const abierto = abrir ?? boton.getAttribute('aria-expanded') !== 'true';
  boton.setAttribute('aria-expanded', String(abierto));
  $('#menu-usuario').hidden = !abierto;
  if (abierto) $('#cerrar-sesion').focus();
}
$('#boton-usuario').addEventListener('click', () => alternarMenuUsuario());
$('#cerrar-sesion').addEventListener('click', () => cerrarSesion('Cerró sesión correctamente.'));
document.addEventListener('click', (e) => {
  if (!e.target.closest('.barra__usuario') && $('#boton-usuario').getAttribute('aria-expanded') === 'true') alternarMenuUsuario(false);
});
document.addEventListener('keydown', (e) => {
  if (e.key !== 'Escape') return;
  if ($('#boton-usuario').getAttribute('aria-expanded') === 'true') { alternarMenuUsuario(false); $('#boton-usuario').focus(); }
  if ($('#pantalla-app').classList.contains('menu-abierto')) { alternarMenu(false); $('#alternar-menu').focus(); }
});

function pintarUsuario() {
  const p = estado.perfil;
  const partes = (p.nombre || '').trim().split(/\s+/).filter(Boolean);
  $('#usuario-iniciales').textContent = ((partes[0]?.[0] || '') + (partes[1]?.[0] || '')).toUpperCase() || '?';
  $('#usuario-nombre-corto').textContent = partes.slice(0, 2).join(' ');
  $('#usuario-nombre').textContent = `${p.nombre} (${p.usuario})`;
  $('#usuario-perfil').textContent = `Perfil: ${conAcentos(p.perfilNombre)}`;
}

/* ---------------------------------------------------------------------
   Enrutador: #ruta. Solo se abren Inicio y las rutas del menú del usuario.
   --------------------------------------------------------------------- */
const VISTAS = {};
const rutaActual = () => decodeURIComponent(location.hash.slice(1)) || 'inicio';
const opcionesMenu = () => estado.menu.flatMap((m) => m.opciones.map((o) => ({ ...o, menu: m.nombre })));

async function navegar() {
  if (!estado.sesion || !estado.perfil) return;
  let ruta = rutaActual();
  if (estado.perfil.cambiarContrasena && ruta !== 'contrasena') { location.hash = '#contrasena'; return; }
  const opcion = opcionesMenu().find((o) => o.ruta === ruta);
  if (ruta !== 'inicio' && (!opcion || !VISTAS[ruta])) {
    avisar('Esa pantalla no está en su menú.');
    history.replaceState(null, '', '#inicio');
    ruta = 'inicio';
  }
  const cont = $('#contenido');
  cont.replaceChildren();
  document.title = `NexoMed · ${opcion ? conAcentos(opcion.nombre) : 'Inicio'}`;
  marcarMenu();
  if (esMovil()) alternarMenu(false);
  try {
    await VISTAS[ruta](cont);
  } catch (err) {
    cont.append(h('p', { class: 'mensaje mensaje--error', role: 'alert', text: textoError(err) }));
  }
  cont.focus({ preventScroll: true });
}
window.addEventListener('hashchange', navegar);

async function iniciarApp() {
  [estado.perfil, estado.menu] = await Promise.all([api('GET', '/auth/yo'), api('GET', '/menu')]);
  pintarUsuario();
  $('#pantalla-login').hidden = true;
  $('#pantalla-app').hidden = false;
  prepararMenu();
  pintarMenu();
  vigilarSesion();
  await navegar();
}

async function recargarMenu() {
  estado.menu = await api('GET', '/menu', undefined, { silencioso: true });
  pintarMenu();
}

/* Campo con etiqueta: campo('Usuario', 'usuario', { requerido: true, ayuda: '…' }) */
let contadorCampos = 0;
function campo(etiqueta, nombre, { tipo = 'text', requerido = false, ayuda = null, clase = '', ...extra } = {}) {
  const id = `c${++contadorCampos}`;
  const idAyuda = ayuda ? `${id}-ayuda` : null;
  return h('div', { class: clase },
    h('label', { for: id }, etiqueta, requerido ? null : h('span', { class: 'texto-secundario', text: ' (opcional)' })),
    h('input', { id, name: nombre, type: tipo, required: requerido, 'aria-describedby': idAyuda, ...extra }),
    ayuda ? h('p', { class: 'ayuda', id: idAyuda, text: ayuda }) : null);
}
/** Campo de contraseña con botón Mostrar. */
function campoSecreto(etiqueta, nombre, extra = {}) {
  const id = `c${++contadorCampos}`;
  const input = h('input', { id, name: nombre, type: 'password', required: true, ...extra });
  return h('div', {},
    h('label', { for: id, text: etiqueta }),
    h('div', { class: 'campo-contrasena' }, input,
      h('button', { type: 'button', class: 'boton-texto', 'aria-pressed': 'false', text: 'Mostrar',
        onclick: (e) => alternarContrasena(input, e.currentTarget) })));
}
function botonEnviar(texto) {
  return h('button', { type: 'submit', class: 'boton boton--primario' },
    h('span', { class: 'giro', 'aria-hidden': 'true', hidden: true }), h('span', { class: 'boton__texto', text: texto }));
}
/** Desactiva el botón y muestra su círculo mientras corre la operación. */
async function conBoton(boton, texto, fn) {
  const original = boton.querySelector('.boton__texto').textContent;
  boton.disabled = true;
  boton.querySelector('.giro').hidden = false;
  boton.querySelector('.boton__texto').textContent = texto;
  try { return await fn(); } finally {
    boton.disabled = false;
    boton.querySelector('.giro').hidden = true;
    boton.querySelector('.boton__texto').textContent = original;
  }
}

/* ---------------------------------------------------------------------
   Inicio
   --------------------------------------------------------------------- */
VISTAS.inicio = (c) => {
  const p = estado.perfil;
  const opciones = opcionesMenu();
  c.append(
    h('h1', { text: `Hola, ${p.nombre || p.usuario}` }),
    h('p', { class: 'subtitulo', text: p.ultimoAcceso ? `Su acceso anterior fue el ${fechaHora(p.ultimoAcceso)}.` : 'Este es su primer acceso.' }),
    h('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-accesos' },
      h('h2', { id: 'titulo-accesos', text: 'Accesos' }),
      h('p', { class: 'texto-secundario', text: 'Las pantallas que su perfil puede usar. Los menús «En preparación» se habilitarán conforme se agreguen sus módulos.' }),
      h('div', { class: 'accesos' }, opciones.map((o) => h('a', { class: 'acceso', href: `#${o.ruta}` },
        h('small', { text: conAcentos(o.menu).toUpperCase() }), h('b', { text: conAcentos(o.nombre) }),
        o.descripcion ? h('small', { text: conAcentos(o.descripcion) }) : null)))));
};

/* ---------------------------------------------------------------------
   Usuario › Mi perfil
   --------------------------------------------------------------------- */
VISTAS.perfil = async (c) => {
  const p = estado.perfil = await api('GET', '/auth/yo');
  const fila = (dt, dd) => [h('dt', { text: dt }), h('dd', {}, dd)];
  c.append(
    h('h1', { text: 'Mi perfil' }),
    h('p', { class: 'subtitulo', text: 'Sus datos de acceso y los permisos que incluye su perfil.' }),
    h('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-datos' },
      h('h2', { id: 'titulo-datos', text: 'Datos de la cuenta' }),
      h('dl', { class: 'ficha' },
        fila('Usuario', p.usuario),
        fila('Nombre', p.nombre),
        fila('Correo', p.correo || h('span', { class: 'texto-secundario', text: 'Sin correo registrado' })),
        fila('Perfil', conAcentos(p.perfilNombre)),
        fila('Alta', fechaHora(p.creadoEn)),
        fila('Contraseña', p.contrasenaActualizada ? `Cambiada el ${fechaHora(p.contrasenaActualizada)}` : 'Sin cambios desde el alta'),
        fila('PIN de acceso', p.tienePin
          ? h('span', { class: 'insignia insignia--si', text: `Creado · ${fechaHora(p.pinActualizado)}` })
          : [h('span', { class: 'insignia insignia--no', text: 'Sin PIN' }), ' ', opcionesMenu().some((o) => o.ruta === 'pin') ? h('a', { href: '#pin', text: 'Crear PIN' }) : null]),
        fila('Acceso anterior', p.ultimoAcceso ? fechaHora(p.ultimoAcceso) : 'Este es su primer acceso'))),
    h('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-permisos' },
      h('h2', { id: 'titulo-permisos', text: `Permisos del perfil ${conAcentos(p.perfilNombre)}` }),
      h('ul', { class: 'roles' }, p.roles.map((r) => h('li', {}, h('small', { text: conAcentos(r.modulo) }), conAcentos(r.nombre))))));
};

/* ---------------------------------------------------------------------
   Usuario › Modificar contraseña
   --------------------------------------------------------------------- */
const REGLAS_CONTRASENA = [
  ['12 o más caracteres', (v) => v.length >= 12],
  ['Una mayúscula', (v) => /[A-Z]/.test(v)],
  ['Una minúscula', (v) => /[a-z]/.test(v)],
  ['Un número', (v) => /\d/.test(v)],
];

VISTAS.contrasena = (c) => {
  const obligado = estado.perfil.cambiarContrasena;
  const msg = h('p', { class: 'mensaje', role: 'alert', hidden: true });
  const reglas = h('ul', { class: 'reglas', 'aria-label': 'Requisitos de la contraseña nueva' },
    REGLAS_CONTRASENA.map(([t]) => h('li', { text: t })), h('li', { text: 'La confirmación coincide' }));
  const form = h('form', { novalidate: true },
    campoSecreto('Contraseña actual', 'actual', { autocomplete: 'current-password' }),
    campoSecreto('Contraseña nueva', 'nueva', { autocomplete: 'new-password' }),
    campoSecreto('Confirme la contraseña nueva', 'confirmar', { autocomplete: 'new-password' }),
    reglas, msg, h('div', { class: 'acciones' }, botonEnviar('Cambiar contraseña')));
  const revisar = () => {
    const v = form.nueva.value;
    const ok = REGLAS_CONTRASENA.map(([, f]) => f(v));
    ok.push(v !== '' && v === form.confirmar.value);
    reglas.querySelectorAll('li').forEach((li, i) => li.classList.toggle('cumple', ok[i]));
    return ok.every(Boolean);
  };
  form.addEventListener('input', revisar);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    msg.hidden = true;
    if (!form.actual.value) { mensaje(msg, 'Escriba su contraseña actual.'); form.actual.focus(); return; }
    if (!revisar()) { mensaje(msg, 'La contraseña nueva no cumple los requisitos o no coincide con la confirmación.'); form.nueva.focus(); return; }
    try {
      await conBoton(form.querySelector('[type=submit]'), 'Guardando…',
        () => api('PUT', '/cuenta/contrasena', { actual: form.actual.value, nueva: form.nueva.value }, { silencioso: true }));
      form.reset(); revisar();
      mensaje(msg, 'Su contraseña se cambió. Úsela la próxima vez que inicie sesión.', 'exito');
      if (obligado) {
        estado.perfil.cambiarContrasena = false;
        avisar('Contraseña cambiada. Ya puede usar NexoMed.');
        location.hash = '#inicio';
      }
    } catch (err) { mensaje(msg, textoError(err)); }
  });
  c.append(
    h('h1', { text: 'Modificar contraseña' }),
    h('p', { class: 'subtitulo', text: 'Por seguridad se pide su contraseña actual.' }),
    obligado ? h('p', { class: 'mensaje mensaje--alerta', role: 'alert', text: 'Su usuario se creó con una contraseña inicial. Cámbiela para continuar.' }) : null,
    h('section', { class: 'tarjeta tarjeta--forma' }, form));
  form.actual.focus();
};

/* ---------------------------------------------------------------------
   Usuario › Crear PIN de acceso
   --------------------------------------------------------------------- */
function pinDebil(pin) {
  const d = [...pin].map(Number);
  const pasos = d.slice(1).map((x, i) => x - d[i]);
  return pasos.every((p) => p === 0) || pasos.every((p) => p === 1) || pasos.every((p) => p === -1);
}

VISTAS.pin = (c) => {
  const tiene = estado.perfil.tienePin;
  const msg = h('p', { class: 'mensaje', role: 'alert', hidden: true });
  const extraPin = { inputmode: 'numeric', maxlength: '6', autocomplete: 'off', pattern: '[0-9]*' };
  const form = h('form', { novalidate: true },
    campoSecreto('Contraseña actual', 'contrasena', { autocomplete: 'current-password' }),
    campoSecreto(tiene ? 'PIN nuevo (4 a 6 dígitos)' : 'PIN (4 a 6 dígitos)', 'pin', extraPin),
    campoSecreto('Confirme el PIN', 'confirmar', extraPin),
    h('p', { class: 'ayuda', text: 'No use números iguales (1111) ni consecutivos (1234, 9876).' }),
    msg, h('div', { class: 'acciones' }, botonEnviar(tiene ? 'Cambiar PIN' : 'Crear PIN')));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    msg.hidden = true;
    const pin = form.pin.value.trim();
    if (!form.contrasena.value) { mensaje(msg, 'Escriba su contraseña actual.'); form.contrasena.focus(); return; }
    if (!/^\d{4,6}$/.test(pin)) { mensaje(msg, 'El PIN debe tener de 4 a 6 dígitos.'); form.pin.focus(); return; }
    if (pinDebil(pin)) { mensaje(msg, 'El PIN es fácil de adivinar (números iguales o consecutivos). Elija otro.'); form.pin.focus(); return; }
    if (pin !== form.confirmar.value.trim()) { mensaje(msg, 'La confirmación no coincide con el PIN.'); form.confirmar.focus(); return; }
    try {
      await conBoton(form.querySelector('[type=submit]'), 'Guardando…',
        () => api('PUT', '/cuenta/pin', { contrasena: form.contrasena.value, pin }, { silencioso: true }));
      estado.perfil.tienePin = true;
      estado.perfil.pinActualizado = new Date().toISOString();
      c.replaceChildren();
      VISTAS.pin(c);
      mensaje(c.querySelector('form .mensaje'), tiene ? 'Su PIN se cambió.' : 'Su PIN se creó.', 'exito');
    } catch (err) { mensaje(msg, textoError(err)); }
  });
  c.append(
    h('h1', { text: tiene ? 'Cambiar PIN de acceso' : 'Crear PIN de acceso' }),
    h('p', { class: 'subtitulo', text: 'Un PIN personal de 4 a 6 dígitos para confirmar operaciones en los módulos que lo pidan.' }),
    tiene ? h('p', { class: 'mensaje mensaje--exito', text: `Ya tiene un PIN, creado o cambiado el ${fechaHora(estado.perfil.pinActualizado)}. Puede cambiarlo aquí.` }) : null,
    h('section', { class: 'tarjeta tarjeta--forma' }, form));
  form.contrasena.focus();
};

/* ---------------------------------------------------------------------
   Usuario › Crear usuario
   --------------------------------------------------------------------- */
function contrasenaAlAzar() {
  const grupos = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnopqrstuvwxyz', '23456789'];
  const todos = grupos.join('');
  const azar = (n) => crypto.getRandomValues(new Uint32Array(1))[0] % n;
  const letras = grupos.map((g) => g[azar(g.length)]);
  while (letras.length < 14) letras.push(todos[azar(todos.length)]);
  for (let i = letras.length - 1; i > 0; i--) { const j = azar(i + 1); [letras[i], letras[j]] = [letras[j], letras[i]]; }
  return letras.join('');
}

VISTAS['usuarios/crear'] = async (c) => {
  const [perfiles, usuarios] = await Promise.all([api('GET', '/usuarios/perfiles'), api('GET', '/usuarios')]);
  const msg = h('p', { class: 'mensaje', role: 'alert', hidden: true });
  const ayudaPerfil = h('p', { class: 'ayuda', id: 'ayuda-perfil' });
  const selPerfil = h('select', { id: 'nuevo-perfil', name: 'perfil', required: true, 'aria-describedby': 'ayuda-perfil' },
    h('option', { value: '', text: 'Seleccione…' }),
    perfiles.map((p) => h('option', { value: p.id, text: conAcentos(p.nombre) })));
  selPerfil.addEventListener('change', () => {
    const p = perfiles.find((x) => String(x.id) === selPerfil.value);
    ayudaPerfil.textContent = p ? conAcentos(p.descripcion || '') : '';
  });
  const campoContrasena = campo('Contraseña inicial', 'contrasena', {
    requerido: true, autocomplete: 'new-password', spellcheck: 'false',
    ayuda: 'Entréguela al usuario: al entrar por primera vez deberá cambiarla. 12 o más caracteres con mayúscula, minúscula y número.',
  });
  const form = h('form', { novalidate: true },
    h('div', { class: 'rejilla' },
      campo('Usuario', 'usuario', { requerido: true, autocomplete: 'off', autocapitalize: 'none', spellcheck: 'false', maxlength: '50',
        ayuda: 'Minúsculas, números, punto o guion (3 a 50). Ej.: jperez' }),
      campo('Nombre completo', 'nombre', { requerido: true, maxlength: '150', autocomplete: 'off' }),
      campo('Correo', 'correo', { tipo: 'email', maxlength: '150', autocomplete: 'off' }),
      h('div', {}, h('label', { for: 'nuevo-perfil', text: 'Perfil' }), selPerfil, ayudaPerfil),
      h('div', { class: 'ancho' }, campoContrasena)),
    h('div', { class: 'acciones acciones--izq' },
      h('button', { type: 'button', class: 'boton', text: 'Generar contraseña', onclick: () => {
        form.contrasena.value = contrasenaAlAzar(); form.contrasena.focus(); form.contrasena.select();
      } })),
    msg, h('div', { class: 'acciones' }, botonEnviar('Crear usuario')));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    msg.hidden = true;
    const datos = {
      usuario: form.usuario.value.trim().toLowerCase(), nombreCompleto: form.nombre.value.trim(),
      correo: form.correo.value.trim() || null, perfilId: selPerfil.value ? Number(selPerfil.value) : null,
      contrasena: form.contrasena.value,
    };
    const faltan = [];
    if (!/^[a-z0-9._-]{3,50}$/.test(datos.usuario)) faltan.push('usuario (minúsculas, números, punto o guion; 3 a 50)');
    if (!datos.nombreCompleto) faltan.push('nombre completo');
    if (datos.correo && !form.correo.checkValidity()) faltan.push('un correo válido');
    if (!datos.perfilId) faltan.push('perfil');
    if (!REGLAS_CONTRASENA.every(([, f]) => f(datos.contrasena))) faltan.push('contraseña inicial de 12 o más caracteres con mayúscula, minúscula y número');
    if (faltan.length) { mensaje(msg, `Revise: ${faltan.join(', ')}.`); return; }
    try {
      await conBoton(form.querySelector('[type=submit]'), 'Creando…', () => api('POST', '/usuarios', datos, { silencioso: true }));
      avisar(`Se creó el usuario ${datos.usuario}.`);
      c.replaceChildren();
      await VISTAS['usuarios/crear'](c);
      mensaje(c.querySelector('.mensaje'), `Se creó el usuario ${datos.usuario} con perfil ${conAcentos(selPerfil.selectedOptions[0].text)}. Al entrar por primera vez deberá cambiar la contraseña.`, 'exito');
    } catch (err) { mensaje(msg, textoError(err)); }
  });
  c.append(
    h('h1', { text: 'Crear usuario' }),
    h('p', { class: 'subtitulo', text: 'Alta de una cuenta con su perfil. El perfil define qué menús y permisos tendrá.' }),
    h('section', { class: 'tarjeta' }, form),
    h('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-usuarios' },
      h('h2', { id: 'titulo-usuarios', text: `Usuarios registrados (${usuarios.length})` }),
      h('div', { class: 'tabla-caja' }, h('table', { class: 'tabla' },
        h('thead', {}, h('tr', {}, ['Usuario', 'Nombre', 'Perfil', 'Estado', 'Contraseña', 'Último acceso'].map((t) => h('th', { scope: 'col', text: t })))),
        h('tbody', {}, usuarios.map((u) => h('tr', {},
          h('td', { text: u.nombre_usuario }), h('td', { text: u.nombre_completo }), h('td', { text: conAcentos(u.perfil) }),
          h('td', {}, h('span', { class: `insignia ${u.activo ? 'insignia--si' : 'insignia--no'}`, text: u.activo ? 'Activo' : 'Inactivo' })),
          h('td', { text: u.cambiar_contrasena ? 'Inicial (debe cambiarla)' : 'Propia' }),
          h('td', { text: u.ultimo_acceso ? fechaHora(u.ultimo_acceso) : 'Sin accesos' }))))))));
  form.usuario.focus();
};

/* ---------------------------------------------------------------------
   Usuario › Configurar menú
   --------------------------------------------------------------------- */
VISTAS.menu = async (c) => {
  const cfg = await api('GET', '/menu/configuracion');
  const perfilesConRol = {};
  cfg.perfilesConRol.forEach((x) => { (perfilesConRol[x.rol] ||= new Set()).add(x.perfil_id); });
  const selectorIcono = (valor, id) => h('select', { id, name: 'icono' },
    cfg.iconos.map((i) => h('option', { value: i, text: NOMBRE_ICONO[i] || i, selected: i === valor })));

  /** Guarda, recarga el menú lateral y vuelve a pintar esta pantalla en el mismo lugar. */
  const guardar = async (boton, caja, fn, exito) => {
    try {
      await conBoton(boton, 'Guardando…', fn);
      const y = window.scrollY;
      await recargarMenu();
      c.replaceChildren();
      await VISTAS.menu(c);
      window.scrollTo(0, y);
      avisar(exito);
    } catch (err) { mensaje(caja, textoError(err)); }
  };

  // Nuevo menú
  const msgNuevo = h('p', { class: 'mensaje', role: 'alert', hidden: true });
  const formNuevo = h('form', { novalidate: true, class: 'menu-config__cabeza' },
    h('div', {}, h('label', { for: 'nuevo-menu', text: 'Nombre' }), h('input', { id: 'nuevo-menu', name: 'nombre', maxlength: '60', required: true })),
    h('div', {}, h('label', { for: 'nuevo-icono', text: 'Icono' }), selectorIcono('carpeta', 'nuevo-icono')),
    h('label', { class: 'casilla' }, h('input', { type: 'checkbox', name: 'activo', checked: true }), 'Activo'),
    botonEnviar('Crear menú'));
  formNuevo.addEventListener('submit', (e) => {
    e.preventDefault();
    const nombre = formNuevo.nombre.value.trim();
    if (!nombre) { mensaje(msgNuevo, 'Escriba el nombre del menú.'); formNuevo.nombre.focus(); return; }
    guardar(formNuevo.querySelector('[type=submit]'), msgNuevo,
      () => api('POST', '/menu/grupos', { nombre, icono: formNuevo.icono.value, activo: formNuevo.activo.checked }, { silencioso: true }),
      `Se creó el menú ${nombre}.`);
  });

  const tarjetaMenu = (m) => {
    const opciones = cfg.opciones.filter((o) => o.menu_id === m.id);
    const msg = h('p', { class: 'mensaje', role: 'alert', hidden: true });
    const idNombre = `menu-${m.id}-nombre`;
    const cabeza = h('form', { novalidate: true, class: 'menu-config__cabeza' },
      h('div', {}, h('label', { for: idNombre, text: 'Nombre del menú' }), h('input', { id: idNombre, name: 'nombre', value: conAcentos(m.nombre), maxlength: '60' })),
      h('div', {}, h('label', { for: `menu-${m.id}-icono`, text: 'Icono' }), selectorIcono(m.icono, `menu-${m.id}-icono`)),
      h('label', { class: 'casilla' }, h('input', { type: 'checkbox', name: 'activo', checked: !!m.activo }), 'Activo'),
      botonEnviar('Guardar menú'));
    cabeza.addEventListener('submit', (e) => {
      e.preventDefault();
      const nombre = cabeza.nombre.value.trim();
      if (!nombre) { mensaje(msg, 'Escriba el nombre del menú.'); return; }
      guardar(cabeza.querySelector('[type=submit]'), msg,
        () => api('PUT', `/menu/grupos/${m.id}`, { nombre, icono: cabeza.icono.value, activo: cabeza.activo.checked }, { silencioso: true }),
        `Se guardó el menú ${nombre}.`);
    });

    const filaOpcion = (o) => {
      const marcados = new Set(String(o.perfiles || '').split(',').filter(Boolean).map(Number));
      const conRol = o.rol_requerido ? (perfilesConRol[o.rol_requerido] || new Set()) : null;
      const idNom = `op-${o.id}-nombre`;
      const nombre = h('input', { id: idNom, value: conAcentos(o.nombre), maxlength: '60', 'aria-label': 'Nombre de la opción' });
      const menuSel = h('select', { 'aria-label': `Menú de ${conAcentos(o.nombre)}` },
        cfg.menus.map((x) => h('option', { value: x.id, text: conAcentos(x.nombre), selected: x.id === o.menu_id })));
      const activa = h('input', { type: 'checkbox', checked: !!o.activo, 'aria-label': `${conAcentos(o.nombre)} activa` });
      const casillas = cfg.perfiles.map((p) => {
        const sinRol = conRol && !conRol.has(p.id);
        return h('label', { class: sinRol ? 'sin-rol' : null, title: sinRol ? `Este perfil no tiene el rol ${conAcentos(o.rol_nombre)}: no verá la opción aunque se marque.` : null },
          h('input', { type: 'checkbox', value: p.id, checked: marcados.has(p.id) }), conAcentos(p.nombre));
      });
      const boton = botonEnviar('Guardar');
      boton.classList.remove('boton--primario');
      boton.type = 'button';
      boton.addEventListener('click', () => {
        if (!nombre.value.trim()) { mensaje(msg, 'Escriba el nombre de la opción.'); nombre.focus(); return; }
        const perfiles = casillas.map((l) => l.querySelector('input')).filter((i) => i.checked).map((i) => Number(i.value));
        guardar(boton, msg, () => api('PUT', `/menu/opciones/${o.id}`, {
          menuId: Number(menuSel.value), nombre: nombre.value.trim(), descripcion: o.descripcion, activo: activa.checked, perfiles,
        }, { silencioso: true }), `Se guardó la opción ${nombre.value.trim()}.`);
      });
      return h('tr', {},
        h('td', {}, nombre, h('div', { class: 'ayuda', text: `#${o.ruta}` })),
        h('td', {}, menuSel),
        h('td', {}, activa),
        h('td', {}, h('div', { class: 'perfiles-marca', role: 'group', 'aria-label': `Perfiles que ven ${conAcentos(o.nombre)}` }, casillas)),
        h('td', { text: o.rol_nombre ? conAcentos(o.rol_nombre) : 'Ninguno' }),
        h('td', {}, boton));
    };

    return h('section', { class: 'tarjeta', 'aria-label': `Menú ${conAcentos(m.nombre)}` },
      cabeza, msg,
      opciones.length
        ? h('div', { class: 'tabla-caja', style: 'margin-top:14px' }, h('table', { class: 'tabla' },
          h('thead', {}, h('tr', {}, ['Opción', 'Menú', 'Activa', 'Perfiles que la ven', 'Rol que exige', ''].map((t) => h('th', { scope: 'col', text: t })))),
          h('tbody', {}, opciones.map(filaOpcion))))
        : h('p', { class: 'ayuda', text: 'Sin opciones todavía: en el menú lateral aparece como «En preparación».' }));
  };

  c.append(
    h('h1', { text: 'Configurar menú' }),
    h('p', { class: 'subtitulo', text: 'Menús y opciones se muestran en orden alfabético. Las pantallas llegan con cada módulo del sistema; aquí se decide su nombre, en qué menú aparecen, si están activas y qué perfiles las ven.' }),
    h('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-nuevo-menu' }, h('h2', { id: 'titulo-nuevo-menu', text: 'Nuevo menú' }), formNuevo, msgNuevo),
    ...cfg.menus.map(tarjetaMenu));
};

/* ---------------------------------------------------------------------
   Catálogos › Catálogo de proveedores
   Datos de la Constancia de Situación Fiscal (CSF) más el contacto.
   --------------------------------------------------------------------- */
const CAMPOS_PROVEEDOR = {
  fiscales: [
    ['rfc', 'RFC', { requerido: true, maxlength: '13', autocapitalize: 'characters', spellcheck: 'false', ayuda: '12 caracteres persona moral, 13 persona física' }],
    ['razonSocial', 'Razón social', { requerido: true, maxlength: '250', ayuda: 'Denominación o razón social, como en la CSF' }],
    ['regimenCapital', 'Régimen capital', { maxlength: '150', ayuda: 'Ej. SOCIEDAD ANÓNIMA DE CAPITAL VARIABLE' }],
    ['nombreComercial', 'Nombre comercial', { maxlength: '250' }],
    ['actividadEconomica', 'Actividad económica principal', { maxlength: '250' }],
    ['fechaInicioOperaciones', 'Fecha de inicio de operaciones', { tipo: 'date' }],
    ['idCif', 'idCIF', { maxlength: '20', inputmode: 'numeric' }],
  ],
  domicilio: [
    ['codigoPostal', 'Código postal', { requerido: true, maxlength: '5', inputmode: 'numeric' }],
    ['tipoVialidad', 'Tipo de vialidad', { maxlength: '50', ayuda: 'Ej. AVENIDA, CALLE' }],
    ['vialidad', 'Nombre de vialidad', { maxlength: '150' }],
    ['numeroExterior', 'Número exterior', { maxlength: '30' }],
    ['numeroInterior', 'Número interior', { maxlength: '30' }],
    ['colonia', 'Colonia', { maxlength: '150' }],
    ['localidad', 'Localidad', { maxlength: '150' }],
    ['municipio', 'Municipio o demarcación territorial', { maxlength: '150' }],
    ['entreCalle', 'Entre calle', { maxlength: '150' }],
    ['yCalle', 'Y calle', { maxlength: '150' }],
  ],
  contacto: [
    ['contacto', 'Persona de contacto', { maxlength: '150' }],
    ['telefono', 'Teléfono', { tipo: 'tel', maxlength: '20', ayuda: '10 dígitos' }],
    ['correo', 'Correo', { tipo: 'email', maxlength: '150' }],
  ],
};
/** Columna de la base (snake_case) de cada campo del formulario. */
const columnaProveedor = (campo) => (campo === 'entidad' ? 'entidad_clave' : campo.replace(/[A-Z]/g, (l) => `_${l.toLowerCase()}`));

VISTAS['catalogos/proveedores'] = async (c) => {
  const cat = await api('GET', '/proveedores/catalogos');
  const filtro = { q: '', estado: 'activos' };
  const puedeCargar = opcionesMenu().some((o) => o.ruta === 'carga/proveedores');

  const caja = h('div');                    // formulario de alta o cambios
  const msgLista = h('p', { class: 'mensaje', role: 'alert', hidden: true });
  const conteo = h('p', { class: 'conteo', 'aria-live': 'polite' });
  const cuerpo = h('tbody');

  const buscar = h('input', { id: 'prov-buscar', type: 'search', placeholder: 'RFC, razón social, nombre comercial o municipio', autocomplete: 'off' });
  const estadoSel = h('select', { id: 'prov-estado' },
    h('option', { value: 'activos', text: 'Activos' }), h('option', { value: 'inactivos', text: 'Inactivos' }), h('option', { value: 'todos', text: 'Todos' }));
  const formBuscar = h('form', { class: 'barra-busqueda', role: 'search' },
    h('div', {}, h('label', { for: 'prov-buscar', text: 'Buscar' }), buscar),
    h('div', {}, h('label', { for: 'prov-estado', text: 'Estado' }), estadoSel),
    h('button', { type: 'submit', class: 'boton', text: 'Buscar' }),
    h('button', { type: 'button', class: 'boton boton--primario', text: 'Nuevo proveedor', onclick: () => abrirForma(null) }));
  formBuscar.addEventListener('submit', (e) => { e.preventDefault(); filtro.q = buscar.value.trim(); filtro.estado = estadoSel.value; listar(); });

  async function listar() {
    msgLista.hidden = true;
    try {
      const r = await api('GET', `/proveedores?q=${encodeURIComponent(filtro.q)}&estado=${filtro.estado}`);
      conteo.textContent = (r.total === 1 ? '1 proveedor encontrado' : `${r.total} proveedores encontrados`)
        + (r.total > r.proveedores.length ? ` (se muestran ${r.proveedores.length}; afine la búsqueda)` : '')
        + ` · En el catálogo: ${r.activos} activos y ${r.inactivos} inactivos.`;
      const vacio = filtro.q ? 'Ningún proveedor coincide con la búsqueda.'
        : `Aún no hay proveedores. Use «Nuevo proveedor»${puedeCargar ? ' o «Cargar proveedores» desde Excel' : ''}.`;
      cuerpo.replaceChildren(...(r.proveedores.length ? r.proveedores.map(fila)
        : [h('tr', {}, h('td', { colspan: '7', class: 'vacio', text: vacio }))]));
    } catch (err) { mensaje(msgLista, textoError(err)); }
  }

  const fila = (p) => h('tr', { class: p.activo ? null : 'inactivo' },
    h('td', {}, h('b', { text: p.rfc }), h('small', { text: p.tipo_persona === 'M' ? 'Persona moral' : 'Persona física' })),
    h('td', {}, p.razon_social, p.nombre_comercial && p.nombre_comercial !== p.razon_social ? h('small', { text: p.nombre_comercial }) : null),
    h('td', {}, p.regimen_fiscal, h('small', { text: conAcentos(p.regimen_descripcion) })),
    h('td', { text: p.codigo_postal }),
    h('td', {}, conAcentos(p.municipio || ''), p.entidad ? h('small', { text: conAcentos(p.entidad) }) : null),
    h('td', {}, p.contacto || '', p.telefono ? h('small', { text: p.telefono }) : null, p.correo ? h('small', { text: p.correo }) : null),
    h('td', { class: 'acciones-fila' },
      h('span', { class: `insignia ${p.activo ? 'insignia--si' : 'insignia--no'}`, text: p.activo ? 'Activo' : 'Inactivo' }), ' ',
      h('button', { type: 'button', class: 'boton boton--chico', text: 'Editar', 'aria-label': `Editar ${p.razon_social}`, onclick: () => abrirForma(p) }), ' ',
      h('button', { type: 'button', class: 'boton boton--chico', text: p.activo ? 'Desactivar' : 'Activar',
        'aria-label': `${p.activo ? 'Desactivar' : 'Activar'} ${p.razon_social}`, onclick: (e) => cambiarActivo(p, e.currentTarget) })));

  async function cambiarActivo(p, boton) {
    if (p.activo && !confirm(`¿Desactivar a ${p.razon_social} (${p.rfc})? No se borra: deja de ofrecerse en los módulos y puede activarse después.`)) return;
    boton.disabled = true;
    try {
      await api('PUT', `/proveedores/${p.id}/activo`, { activo: !p.activo });
      avisar(`${p.razon_social} quedó ${p.activo ? 'inactivo' : 'activo'}.`);
      await listar();
    } catch (err) { mensaje(msgLista, textoError(err)); boton.disabled = false; }
  }

  function abrirForma(p) {
    const editar = !!p;
    const msg = h('p', { class: 'mensaje', role: 'alert', hidden: true });
    const valor = (nombre) => {
      if (!p) return nombre === 'estatusPadron' ? 'ACTIVO' : null;
      const v = p[columnaProveedor(nombre)];
      return v === null || v === undefined ? null : String(v);
    };
    const campos = (lista) => lista.map(([nombre, etiqueta, opc]) => campo(etiqueta, nombre, { ...opc, value: valor(nombre) }));

    const regimen = h('select', { id: 'prov-regimen', name: 'regimenFiscal', required: true, 'aria-describedby': 'prov-regimen-ayuda' });
    const entidad = h('select', { id: 'prov-entidad', name: 'entidad' }, h('option', { value: '', text: 'Sin dato' }),
      cat.entidades.map((x) => h('option', { value: x.clave, text: conAcentos(x.nombre), selected: x.clave === valor('entidad') })));
    const estatus = h('select', { id: 'prov-estatus', name: 'estatusPadron' },
      cat.estatus.map((x) => h('option', { value: x, text: x, selected: x === valor('estatusPadron') })));
    const parecido = h('label', { class: 'casilla', hidden: true },
      h('input', { type: 'checkbox', name: 'aunqueParecido' }), 'Es un proveedor distinto: guardarlo de todos modos');

    const fiscales = campos(CAMPOS_PROVEEDOR.fiscales);
    const domicilio = campos(CAMPOS_PROVEEDOR.domicilio);
    const forma = h('form', { novalidate: true, 'aria-labelledby': 'titulo-forma-prov' },
      h('h2', { id: 'titulo-forma-prov', text: editar ? `Editar proveedor ${p.rfc}` : 'Nuevo proveedor' }),
      h('p', { class: 'ayuda', text: 'Copie los datos de la Constancia de Situación Fiscal del proveedor. Obligatorios: RFC, razón social, régimen fiscal y código postal.' }),
      h('fieldset', { class: 'seccion-forma' }, h('legend', { text: 'Datos de identificación (CSF)' }),
        h('div', { class: 'rejilla' }, fiscales.slice(0, 4),
          h('div', {}, h('label', { for: 'prov-regimen', text: 'Régimen fiscal' }), regimen,
            h('p', { class: 'ayuda', id: 'prov-regimen-ayuda', text: 'Se filtra según el RFC: persona moral o física.' })),
          fiscales.slice(4),
          h('div', {}, h('label', { for: 'prov-estatus', text: 'Estatus en el padrón' }), estatus))),
      h('fieldset', { class: 'seccion-forma' }, h('legend', { text: 'Domicilio fiscal' }),
        h('div', { class: 'rejilla' }, domicilio.slice(0, 8),
          h('div', {}, h('label', { for: 'prov-entidad', text: 'Entidad federativa' }), entidad),
          domicilio.slice(8))),
      h('fieldset', { class: 'seccion-forma' }, h('legend', { text: 'Contacto' }),
        h('div', { class: 'rejilla' }, campos(CAMPOS_PROVEEDOR.contacto))),
      msg, parecido,
      h('div', { class: 'acciones' },
        h('button', { type: 'button', class: 'boton', text: 'Cancelar', onclick: () => { caja.replaceChildren(); buscar.focus(); } }),
        botonEnviar(editar ? 'Guardar cambios' : 'Dar de alta')));

    const pintarRegimenes = () => {
      const rfc = (forma.rfc.value || '').trim();
      const tipo = rfc.length === 12 ? 'moral' : rfc.length === 13 ? 'fisica' : null;
      const actual = regimen.value || valor('regimenFiscal');
      regimen.replaceChildren(h('option', { value: '', text: 'Seleccione…' }),
        ...cat.regimenes.filter((r) => !tipo || (tipo === 'moral' ? r.persona_moral : r.persona_fisica))
          .map((r) => h('option', { value: r.clave, text: `${r.clave} · ${conAcentos(r.descripcion)}`, selected: r.clave === actual })));
    };
    pintarRegimenes();
    forma.rfc.addEventListener('input', () => { forma.rfc.value = forma.rfc.value.toUpperCase(); pintarRegimenes(); });

    forma.addEventListener('submit', async (e) => {
      e.preventDefault();
      msg.hidden = true;
      const datos = {};
      for (const lista of Object.values(CAMPOS_PROVEEDOR)) for (const [n] of lista) datos[n] = forma[n].value.trim() || null;
      datos.regimenFiscal = regimen.value || null;
      datos.entidad = entidad.value || null;
      datos.estatusPadron = estatus.value;
      const faltan = [];
      if (!datos.rfc) faltan.push('RFC');
      if (!datos.razonSocial) faltan.push('razón social');
      if (!datos.regimenFiscal) faltan.push('régimen fiscal');
      if (!datos.codigoPostal) faltan.push('código postal');
      if (faltan.length) { mensaje(msg, `Falta: ${faltan.join(', ')}.`); return; }
      const aunque = !parecido.hidden && forma.aunqueParecido.checked;
      const ruta = `/proveedores${editar ? `/${p.id}` : ''}${aunque ? '?aunqueParecido=true' : ''}`;
      try {
        await conBoton(forma.querySelector('[type=submit]'), 'Guardando…', () => api(editar ? 'PUT' : 'POST', ruta, datos, { silencioso: true }));
        const rfc = datos.rfc.toUpperCase();
        caja.replaceChildren();
        avisar(editar ? `Se guardaron los cambios de ${rfc}.` : `Se dio de alta el proveedor ${rfc}.`);
        if (!editar) { filtro.q = rfc; buscar.value = rfc; filtro.estado = 'activos'; estadoSel.value = 'activos'; }
        await listar();
      } catch (err) {
        if (err.status === 409 && err.cuerpo.parecido) {
          parecido.hidden = false;
          mensaje(msg, `${err.message} Si de verdad es otro proveedor, marque la casilla y vuelva a guardar.`, 'alerta');
        } else {
          mensaje(msg, textoError(err));
        }
      }
    });
    caja.replaceChildren(h('section', { class: 'tarjeta' }, forma));
    caja.scrollIntoView({ block: 'start' });
    forma.rfc.focus();
  }

  c.append(
    h('h1', { text: 'Catálogo de proveedores' }),
    h('p', { class: 'subtitulo' }, 'Proveedores con los datos de su Constancia de Situación Fiscal. Un RFC solo se registra una vez.',
      puedeCargar ? [' Para dar de alta muchos a la vez use ', h('a', { href: '#carga/proveedores', text: 'Cargar proveedores' }), '.'] : null),
    h('section', { class: 'tarjeta' }, formBuscar, conteo),
    caja,
    msgLista,
    h('div', { class: 'tabla-caja' }, h('table', { class: 'tabla' },
      h('caption', { class: 'solo-lector', text: 'Proveedores' }),
      h('thead', {}, h('tr', {}, ['RFC', 'Razón social', 'Régimen fiscal', 'C.P.', 'Municipio', 'Contacto', ''].map((t) => h('th', { scope: 'col', text: t })))),
      cuerpo)));
  await listar();
  buscar.focus();
};

/* ---------------------------------------------------------------------
   Carga de archivos › Cargar proveedores (modelo de las cargas de SIALSS MDF)
   1. Archivo → Revisar (todo el archivo, sin guardar) → 2. Confirmar con la contraseña (3 intentos).
   Lo repetido (mismo RFC, razón social igual o parecida) se omite con aviso y se carga lo demás.
   --------------------------------------------------------------------- */
VISTAS['carga/proveedores'] = (c) => {
  const URL_CARGA = '/cargas/proveedores';
  const COLUMNAS = ['Renglón', 'RFC', 'Razón social', 'Régimen fiscal', 'C.P.', 'Municipio', 'Entidad', 'Teléfono', 'Correo'];
  let token = null;
  const cuantos = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;

  const archivo = h('input', { type: 'file', id: 'ca-archivo', accept: '.xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel' });
  const estadoCarga = h('p', { class: 'carga__estado', role: 'status', 'aria-live': 'polite' });
  const errores = h('ul', { class: 'carga__errores', hidden: true, 'aria-label': 'Errores del archivo' });
  const omitidosLista = h('ul');
  const omitidosTitulo = h('summary');
  const omitidos = h('details', { class: 'carga__omitidos', hidden: true }, omitidosTitulo, omitidosLista);
  const resumen = h('span', { class: 'carga__resumen' });
  const revisar = botonEnviar('Revisar archivo');
  revisar.type = 'button';
  const cancelar = h('button', { type: 'button', class: 'boton', text: 'Cancelar carga', hidden: true });
  const aplicar = botonEnviar('Confirmar carga');
  const confirmar = h('form', { class: 'carga__confirmar', hidden: true, novalidate: true },
    h('fieldset', {}, h('legend', { text: '2. Para confirmar, escriba su contraseña' }),
      h('div', { class: 'carga__confirmar-fila' },
        campoSecreto('Contraseña', 'contrasena', { autocomplete: 'current-password' }), aplicar, resumen)));
  const muestra = h('tbody');

  function mostrarEstado(texto, tipo) {
    estadoCarga.textContent = texto || '';
    estadoCarga.className = `carga__estado${tipo ? ` carga__estado--${tipo}` : ''}`;
  }
  function pintarLista(ul, lista, total, mas) {
    ul.replaceChildren(...lista.map((t) => h('li', { text: t })),
      ...(total > lista.length ? [h('li', { text: `… y ${cuantos(total - lista.length, mas[0], mas[1])}.` })] : []));
  }
  function pintarMuestra(filas) {
    muestra.replaceChildren(...(filas.length
      ? filas.map((f) => h('tr', {}, f.map((v, i) => h('td', { text: v == null ? '' : (i === 3 || i === 6 ? conAcentos(v) : String(v)) }))))
      : [h('tr', {}, h('td', { colspan: String(COLUMNAS.length), class: 'vacio', text: 'Suba un archivo y pulse «Revisar archivo».' }))]));
  }
  function reiniciar() {
    token = null;
    confirmar.hidden = true;
    cancelar.hidden = true;
    confirmar.contrasena.value = '';
  }

  revisar.addEventListener('click', async () => {
    const f = archivo.files[0];
    if (!f) { mostrarEstado('Elija el archivo de Excel.', 'error'); archivo.focus(); return; }
    if (f.size === 0) { mostrarEstado(`El archivo ${f.name} está vacío (0 bytes). No se cargó nada.`, 'error'); return; }
    reiniciar();
    errores.hidden = true;
    omitidos.hidden = true;
    pintarMuestra([]);
    mostrarEstado('Revisando el archivo…');
    const datos = new FormData();
    datos.append('archivo', f);
    try {
      const r = await conBoton(revisar, 'Revisando…', () => api('POST', `${URL_CARGA}/revisar`, datos));
      pintarLista(errores, r.errores, r.totalErrores, ['error más', 'errores más']);
      errores.hidden = !r.errores.length;
      omitidosTitulo.textContent = `${cuantos(r.totalOmitidos, 'renglón repetido se omite', 'renglones repetidos se omiten')}: ya están en el catálogo o se repiten en el archivo (no se cargan).`;
      pintarLista(omitidosLista, r.omitidos, r.totalOmitidos, ['repetido más', 'repetidos más']);
      omitidos.hidden = !r.totalOmitidos;
      pintarMuestra(r.muestra);
      if (r.valida) {
        token = r.token;
        resumen.textContent = r.resumen;
        confirmar.hidden = false;
        cancelar.hidden = false;
        mostrarEstado(`El archivo está correcto: ${r.resumen} Confirme con su contraseña para guardar.`, 'ok');
        confirmar.contrasena.focus();
      } else if (r.sinNuevos) {
        mostrarEstado(`No hay proveedores nuevos: ${r.leidos === 1 ? 'el renglón ya está registrado o se repite' : `los ${r.leidos} renglones ya están registrados o se repiten`}. No hay nada que guardar.`, 'aviso');
      } else {
        mostrarEstado(`El archivo tiene ${cuantos(r.totalErrores, 'error', 'errores')}. Corríjalo y vuelva a subirlo; no se guardó nada.`, 'error');
      }
    } catch (err) { mostrarEstado(textoError(err), 'error'); }
  });

  confirmar.addEventListener('submit', async (e) => {
    e.preventDefault();
    const contrasena = confirmar.contrasena.value;
    if (!contrasena) { mostrarEstado('Escriba su contraseña para confirmar.', 'error'); confirmar.contrasena.focus(); return; }
    confirmar.contrasena.value = '';
    mostrarEstado('Guardando la carga…');
    try {
      const r = await conBoton(aplicar, 'Guardando…', () => api('POST', `${URL_CARGA}/confirmar`, { token, contrasena }));
      reiniciar();
      archivo.value = '';
      mostrarEstado(r.mensaje, 'ok');
      avisar(r.mensaje);
    } catch (err) {
      if (err.cuerpo && err.cuerpo.cancelada) reiniciar();
      mostrarEstado(textoError(err), 'error');
      if (!confirmar.hidden) confirmar.contrasena.focus();
    }
  });

  cancelar.addEventListener('click', () => {
    api('POST', `${URL_CARGA}/cancelar`, undefined, { silencioso: true }).catch(() => { /* nada que deshacer */ });
    reiniciar();
    mostrarEstado('Carga cancelada; no se guardó nada.');
  });

  pintarMuestra([]);
  c.append(
    h('h1', { text: 'Cargar proveedores' }),
    h('p', { class: 'subtitulo' }, 'Alta de proveedores desde un Excel con los datos de su Constancia de Situación Fiscal. Primero se revisa todo el archivo sin guardar nada; si no hay errores, confirma con su contraseña y se guarda a nombre de ',
      h('b', { text: estado.perfil.usuario }), '.'),
    h('section', { class: 'tarjeta carga' },
      h('fieldset', {}, h('legend', { text: '1. Archivo' }),
        h('div', { class: 'carga__fila' },
          h('div', {}, h('label', { for: 'ca-archivo', text: 'Excel (.xlsx o .xls)' }), archivo),
          h('div', { class: 'carga__plantilla' },
            h('p', { class: 'ayuda', text: 'La plantilla trae las columnas de la CSF, un renglón de ejemplo, los regímenes fiscales del SAT, las entidades e instrucciones.' }),
            h('button', { type: 'button', class: 'boton', text: 'Descargar plantilla', onclick: async (e) => {
              const b = e.currentTarget;
              b.disabled = true;
              try { await descargar(`${URL_CARGA}/plantilla`, 'plantilla_proveedores.xlsx'); } catch (err) { mostrarEstado(textoError(err), 'error'); } finally { b.disabled = false; }
            } })))),
      h('div', { class: 'acciones acciones--izq' }, revisar, cancelar),
      estadoCarga, errores, omitidos, confirmar),
    h('section', { class: 'tarjeta' },
      h('h2', { text: 'Vista previa' }),
      h('p', { class: 'ayuda', text: 'Los proveedores nuevos que se cargarán (se muestran hasta 200).' }),
      h('div', { class: 'tabla-caja', style: 'margin-top:10px' }, h('table', { class: 'tabla' },
        h('caption', { class: 'solo-lector', text: 'Proveedores nuevos del archivo' }),
        h('thead', {}, h('tr', {}, COLUMNAS.map((t) => h('th', { scope: 'col', text: t })))),
        muestra))));
};

/* Recarga de la página: si el token sigue vigente, continúa la sesión. */
(async function reanudar() {
  let guardada = null;
  try { guardada = JSON.parse(sessionStorage.getItem('nexomed.sesion')); } catch { /* nada */ }
  if (!guardada || Date.now() >= guardada.expira) return;
  estado.sesion = guardada;
  estado.ultimaActividad = Date.now();
  try { await iniciarApp(); } catch { cerrarSesion('', false); }
}());
