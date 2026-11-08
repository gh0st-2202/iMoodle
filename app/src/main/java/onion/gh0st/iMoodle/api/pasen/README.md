# Documentacion API iPasen (Pasen)

> ⚠️ **Aviso importante**
>
> Esta documentación **no es oficial**. Ha sido obtenida mediante ingeniería inversa del tráfico de la aplicación oficial de iPasen (`juntadeandalucia.ced.pasen`). La Junta de Andalucía **no publica ni documenta** estos endpoints y puede cambiarlos, cerrarlos o bloquearlos **sin previo aviso**.
>
> El uso de esta API desde aplicaciones de terceros puede infringir los términos de servicio de PASEN. **Úsala bajo tu responsabilidad y solo para uso personal o educativo.** No la utilices para distribuir una app en tiendas ni para automatizar acciones masivas.

---

## Índice

1. [Información general](#información-general)
2. [Autenticación](#autenticación)
3. [Cabeceras comunes](#cabeceras-comunes)
4. [Formato de respuesta](#formato-de-respuesta)
5. [Endpoints](#endpoints)
   - [Generales](#generales)
     - [`GET /generales/version`](#get-generalesversion)
     - [`GET /generales/info-session`](#get-generalesinfo-session)
     - [`GET /generales/home`](#get-generaleshome)
     - [`GET /generales/get-agenda`](#get-generalesget-agenda)
   - [Horarios](#horarios)
     - [`GET /horarios/get-horario`](#get-horariosget-horario)
     - [`GET /horarios/tutoria`](#get-horariostutoria)
     - [`GET /horarios/libros-texto`](#get-horarioslibros-texto)
     - [`GET /horarios/ipasen/tutorias`](#get-horariosipasentutorias)
   - [Faltas](#faltas)
     - [`GET /faltas/get-faltas`](#get-faltasget-faltas)
     - [`GET /faltas/get-notificaciones`](#get-faltasget-notificaciones)
   - [Mensajería](#mensajería)
     - [`GET /mensajeria/avisos`](#get-mensajeriaavisos)
     - [`GET /mensajeria/get-comunicaciones`](#get-mensajeriaget-comunicaciones)
     - [`GET /mensajeria/get-mensajes-salida`](#get-mensajeriaget-mensajes-salida)
   - [Centros](#centros)
     - [`GET /centros/get-resumen-tablon-anuncios`](#get-centrosget-resumen-tablon-anuncios)
     - [`GET /centros/get-tablon-anuncios`](#get-centrosget-tablon-anuncios)
     - [`GET /centros/servicios-centro`](#get-centrosservicios-centro)
     - [`GET /centros/calendario-centro`](#get-centroscalendario-centro)
     - [`GET /centros/get-imagen-centro`](#get-centrosget-imagen-centro)
   - [Seguimiento](#seguimiento)
     - [`GET /seguimiento/imagen-alumno`](#get-seguimientoimagen-alumno)
     - [`GET /seguimiento/datos-alumno`](#get-seguimientodatos-alumno)
     - [`GET /seguimiento/get-notas-convocatorias`](#get-seguimientoget-notas-convocatorias)
     - [`GET /seguimiento/observaciones`](#get-seguimientoobservaciones)
     - [`GET /seguimiento/get-aut-act`](#get-seguimientoget-aut-act)
     - [`GET /seguimiento/get-per-aut-rec-alu`](#get-seguimientoget-per-aut-rec-alu)
6. [Modelos de datos](#modelos-de-datos)
7. [Notificaciones push (FCM)](#notificaciones-push-fcm)
8. [Errores conocidos](#errores-conocidos)

---

## Información general

| Propiedad | Valor |
|-----------|-------|
| **Base URL** | `https://seneca.juntadeandalucia.es/api/public` |
| **Protocolo** | HTTPS |
| **Formato** | JSON (`application/vnd.piomin.app-v1.0+json`) |
| **Autenticación** | OAuth2 `password` grant + JWT Bearer |
| **Cliente OAuth** | `client_id=ipasen` |
| **Scope** | `PASENDROID` |
| **Versión documentada** | App iPasen 12.9.2 (Android), noviembre 2026 |
| **Caducidad del token** | 1799 s (~30 min) |

---

## Autenticación

### `POST /security/oauth/token`

Autentica al usuario y devuelve un JWT (`access_token`). Es el único endpoint público que no requiere `Authorization`.

**Cabeceras**

```
Content-Type: application/x-www-form-urlencoded
User-Agent: iPasen/12.9.2(Android;Scale/2.8125)
Accept-Charset: utf-8
```

**Body (form-urlencoded)**

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `username` | string | ✅ | Usuario (NIF/NIE del alumno o tutor). |
| `password` | string | ✅ | Contraseña. |
| `grant_type` | string | ✅ | Siempre `password`. |
| `scope` | string | ✅ | Siempre `PASENDROID`. |
| `client_id` | string | ✅ | Siempre `ipasen`. |

**Respuesta 200**

```json
{
  "access_token": "eyJraWQiOi...",
  "token_type": "Bearer",
  "expires_in": 1799
}
```

No hay `refresh_token`. Cuando el token caduca (~30 min) hay que volver a autenticarse con usuario/contraseña.

**Respuesta de error (401)**

```json
{
  "error": "invalid_grant",
  "error_description": "Bad credentials"
}
```

---

## Cabeceras comunes

Todas las peticiones autenticadas deben llevar:

```
Authorization: Bearer <access_token>
User-Agent: iPasen/12.9.2(Android;Scale/2.8125)
Accept-Charset: utf-8
```

El servidor valida que el `User-Agent` empiece por `iPasen/` seguido de una versión reciente. Un `User-Agent` genérico puede ser rechazado con 403 o devolver `success=false`.

La cookie `SenecaP` rota en cada respuesta. No es necesario reenviarla: el `Bearer` es la autenticación real.

---

## Formato de respuesta

Todas las respuestas siguen el mismo envelope:

```json
{
  "@class": "es.juntadeandalucia.ced.seneca.commons.json.impl.JSONResponseImpl",
  "success": true,
  "message": "OK",
  "data": {},
  "errors": [],
  "meta": {
    "@class": "es.juntadeandalucia.ced.seneca.commons.json.impl.JSONMetaImpl",
    "version": "1.0"
  }
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `success` | boolean | `true` si la petición fue correcta. |
| `message` | string | `"OK"` en éxito, mensaje de error en fallo. |
| `data` | object \| array | Payload. Puede ser `[]` cuando no hay resultados. |
| `errors` | array | Lista de errores detallados. |
| `@class` | string | Nombre de la clase Java. Ignorar al parsear. |

---

## Endpoints

### Generales

#### `GET /generales/version`

Comprueba que la versión de la app está soportada.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `scope` | `PASENDROID` | Siempre este valor. |
| `version` | `12.9.2` | Versión actual de la app. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {},
  "errors": [],
  "meta": {"version": "1.0"}
}
```

---

#### `GET /generales/info-session`

Devuelve los datos del usuario autenticado y sus matrículas (`enrollments`). El `enrollment` es el ID que se usa en casi todos los endpoints de datos.

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "isPasenLite": false,
    "typeProfile": "ALU",
    "descriptionProfile": "Alumno/a",
    "name": "Apellido1 Apellido2, Nombre",
    "fullname": "Nombre Apellido1 Apellido2",
    "simpleName": "Nombre",
    "url_enrol": "https://secretariavirtual.juntadeandalucia.es/...",
    "urlPr": "https://seneca.juntadeandalucia.es/seneca/puntoderecogida/...",
    "enrollments": [
      {
        "course": "2º G.D.C.F.G.S.(Desarrollo de Aplicaciones Mu",
        "description": "I.E.S. - Nombre del Centro",
        "tutor": "Apellido1 Apellido2, Nombre",
        "unity": "2º CFGS A",
        "centerCode": "00000000",
        "step": "2911",
        "enrollment": "12345678",
        "offerEnrollment": "999999",
        "codeUnity": "1234567",
        "autRec": false,
        "centerImage": "https://seneca.juntadeandalucia.es/api/public/centros/get-imagen-centro?code=XXXX",
        "avatar": "https://seneca.juntadeandalucia.es/api/public/seguimiento/imagen-alumno?enrollment=12345678",
        "urlMat": "https://secretariavirtual.juntadeandalucia.es/..."
      }
    ],
    "sons": []
  },
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave**

| Campo | Descripción |
|-------|-------------|
| `typeProfile` | `ALU` (alumno), `PAD` (padre/madre), `TUT` (tutor legal). |
| `enrollments[]` | Una entrada por cada hijo/a, o una sola si el perfil es alumno. |
| `enrollments[].enrollment` | ID de matrícula. Se usa en todos los endpoints de datos. |
| `enrollments[].unity` | Grupo/clase. |
| `enrollments[].description` | Nombre del centro. |
| `enrollments[].centerCode` | Código del centro. |
| `sons` | Lista de hijos si el perfil es padre/madre. |

---

#### `GET /generales/home`

Dashboard de inicio: resumen de usuario, eventos del calendario, notificaciones pendientes y acciones.

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "session": [{
        "user": "Apellido1 Apellido2, Nombre",
        "fullname": "Nombre Apellido1 Apellido2",
        "simpleName": "Nombre",
        "profile": "ALU",
        "descrptionprofile": "Alumno/a",
        "enrollment": [{
          "enrollment": "12345678",
          "codeunit": "1234567",
          "enrollmentofer": "999999",
          "center": "00000000",
          "step": "2911",
          "denomination": "I.E.S. - Nombre del Centro",
          "unit": "2º CFGS A",
          "course": "2º G.D.C.F.G.S.(Desarrollo de Aplicaciones Mu",
          "tutor": "Apellido1 Apellido2, Nombre",
          "autrec": false,
          "phone_centro": "000000000",
          "email_centro": "00000000.edu@juntadeandalucia.es",
          "instagram": "ies_nombre",
          "web": "www.ies-nombre.es",
          "image_centro": "https://seneca.juntadeandalucia.es/api/public/centros/get-imagen-centro?code=XXXX",
          "logo_centro": ""
        }],
        "sons": []
      }],
      "event": [{
        "code": null,
        "initdate": "07/12/2026",
        "initHour": "00:01",
        "endHour": "23:59",
        "endDate": "07/12/2026",
        "manual": false,
        "title": "Festivo",
        "description": "Lunes siguiente al Día de la Constitución",
        "priority": 1,
        "annotation": "",
        "enrollment": "",
        "category": "Festivo"
      }],
      "actions": [],
      "notifications": [
        {"type": "MENSAJES", "count": 0},
        {"type": "FIRMAS", "count": 0}
      ],
      "tutor_meetings": [],
      "processes": []
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave**

| Campo | Descripción |
|-------|-------------|
| `data[].session[].enrollment` | Información de matrícula. |
| `data[].event[]` | Eventos del calendario. |
| `data[].event[].title` | Tipo (`Festivo`, `Examen`, `Entrega`...). |
| `data[].notifications[].type` | `MENSAJES`, `FIRMAS`. |
| `data[].notifications[].count` | Número sin leer. |

---

#### `GET /generales/get-agenda`

Agenda completa desde una fecha de inicio.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `initdate` | `2026-11-06` | Fecha de inicio en formato ISO (`yyyy-MM-dd`). |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "code": null,
      "initdate": "07/12/2026",
      "initHour": "00:01",
      "endHour": "23:59",
      "endDate": "07/12/2026",
      "manual": false,
      "title": "Festivo",
      "description": "Lunes siguiente al Día de la Constitución",
      "priority": 1,
      "annotation": "",
      "enrollment": "",
      "category": "Festivo"
    },
    {
      "code": null,
      "initdate": "08/12/2026",
      "initHour": "00:01",
      "endHour": "23:59",
      "endDate": "08/12/2026",
      "manual": false,
      "title": "Festivo",
      "description": "Día de la Inmaculada Concepción",
      "priority": 1,
      "annotation": "",
      "enrollment": "",
      "category": "Festivo"
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave del `AgendaDTO`**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `initdate` | string | Fecha de inicio `dd/MM/yyyy`. |
| `initHour` | string | Hora de inicio `HH:mm`. |
| `endDate` | string | Fecha de fin `dd/MM/yyyy`. |
| `endHour` | string | Hora de fin `HH:mm`. |
| `title` | string | Tipo (`Festivo`, `Examen`, `Entrega`...). |
| `description` | string | Descripción del evento. |
| `category` | string | Categoría (`Festivo`, `Examen`...). |
| `priority` | int | Prioridad. |
| `annotation` | string | Anotación libre. |
| `manual` | boolean | Si fue creado manualmente. |

---

### Horarios

#### `GET /horarios/get-horario`

Devuelve el horario semanal del alumno.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula obtenido en `info-session`. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "day": "Lunes",
      "codesection": 1000001,
      "hoursection": "8:30 - 9:30",
      "section": 1,
      "prof": "Apellido1 Apellido2, Nombre",
      "subject": "Desarrollo de interfaces"
    },
    {
      "day": "Lunes",
      "codesection": 1000002,
      "hoursection": "13:00 - 14:00",
      "section": 6,
      "prof": "Apellido3 Apellido4, Nombre",
      "subject": "Acceso a datos"
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `day` | string | `"Lunes"`, `"Martes"`, …, `"Viernes"`. |
| `hoursection` | string | Rango `"HH:mm - HH:mm"`. |
| `section` | int | Slot del día (1..7). |
| `subject` | string | Nombre de la asignatura. |
| `prof` | string | Profesor. |
| `codesection` | int | ID único de la asignatura. |

**Casos especiales**

- Asignaturas compartidas: la misma franja (`day` + `hoursection` + `codesection`) puede aparecer varias veces con distinto `prof`. Hay que agrupar y unir la lista de profesores.
- Slots vacíos: no vienen en el JSON.
- Sin fechas: es un horario recurrente.

---

#### `GET /horarios/tutoria`

Información de la tutoría del grupo: tutor/a, delegado/a, equipo docente y libros de texto.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "delegate": "Unidad sin delegado/a",
    "matriculaDescription": "2º G.D.C.F.G.S. (Desarrollo de Aplicaciones Multiplataforma)",
    "unity": "2º CFGS A",
    "centerName": "I.E.S. - Nombre del Centro",
    "tutorName": "Apellido1 Apellido2, Nombre",
    "activitys": [
      {
        "day": "martes",
        "activity": "Tutoría con alumnado (Bachillerato/Formac. Profes.)",
        "time_started": "13:00",
        "time_end": "14:00"
      },
      {
        "day": "martes",
        "activity": "Tutoría de Atención a Padres y Madres",
        "time_started": "16:00",
        "time_end": "17:00"
      }
    ],
    "teachingTeam": [
      {
        "shortName": "ACDAT",
        "description": "Acceso a datos",
        "teachers": "Apellido1 Apellido2, Nombre;  Apellido3 Apellido4, Nombre"
      },
      {
        "shortName": "DEINT",
        "description": "Desarrollo de interfaces",
        "teachers": "Apellido5 Apellido6, Nombre"
      }
    ],
    "books": []
  },
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave del `DatosTutoriaDTO`**

| Campo | Descripción |
|-------|-------------|
| `delegate` | Nombre del delegado/a del grupo. |
| `unity` | Grupo/clase. |
| `centerName` | Nombre del centro. |
| `tutorName` | Tutor/a del grupo. |
| `activitys[]` | Tutorías programadas. |
| `teachingTeam[]` | Equipo docente. |
| `books[]` | Libros de texto. |

---

#### `GET /horarios/libros-texto`

Lista de libros de texto del curso.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /horarios/ipasen/tutorias`

Tutorías específicas de iPasen.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 401**

```json
{
  "success": false,
  "message": "Access Denied",
  "data": null,
  "errors": [{
    "status": 401,
    "detail": "Access Denied"
  }]
}
```

---

### Faltas

#### `GET /faltas/get-faltas`

Lista de ausencias del alumno.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /faltas/get-notificaciones`

Notificaciones de faltas de asistencia.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 401**

```json
{
  "success": false,
  "message": "No tiene acceso a esta operación.",
  "data": null,
  "errors": [{
    "status": 401,
    "detail": "No tiene acceso a esta operación.",
    "locatorId": "#XXXXXXXXXX"
  }]
}
```

---

### Mensajería

#### `GET /mensajeria/avisos`

Lista de avisos/notificaciones del sistema.

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /mensajeria/get-comunicaciones`

Lista paginada de comunicaciones del centro. Devuelve el cuerpo HTML completo de cada mensaje.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `page` | `1` | Número de página (empieza en 1). |
| `isread` | `false` | `false` = solo no leídas; `true` = solo leídas; omitido = todas. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": 100000001,
      "id_message": 200000001,
      "id_notification": "",
      "type": "MENSAJE",
      "has_adj": true,
      "isdrawingpin": false,
      "isread": true,
      "subject": "Asunto del mensaje",
      "body_message": "<p>HTML del cuerpo del mensaje...</p>",
      "date": "13/10/2026 14:21",
      "sender": "Apellido1 Apellido2, Nombre (Dirección)",
      "enrollment": 12345678,
      "student": "Nombre Alumno",
      "course": "2º G.D.C.F.G.S. (Desarrollo de Aplicaciones Multiplataforma)",
      "isFavorite": false,
      "tags": []
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave del `ComunicacionesDTO`**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | long | ID interno de la comunicación. |
| `id_message` | long | ID del mensaje asociado. |
| `type` | string | `MENSAJE`, etc. |
| `has_adj` | boolean | Si tiene adjuntos. |
| `isdrawingpin` | boolean | Si está fijada. |
| `isread` | boolean | Si está leída. |
| `subject` | string | Asunto. |
| `body_message` | string | Cuerpo en HTML. |
| `date` | string | Fecha `dd/MM/yyyy HH:mm`. |
| `sender` | string | Remitente. |
| `enrollment` | int | ID de matrícula asociado. `0` = comunicación general de la Consejería. |
| `student` | string | Nombre del alumno destinatario. |
| `course` | string | Curso. |
| `isFavorite` | boolean | Marcado como favorito. |
| `tags` | array | Etiquetas. |

`body_message` es HTML con entidades. `has_adj = true` indica adjuntos, pero los detalles no vienen en este JSON.

---

#### `GET /mensajeria/get-mensajes-salida`

Mensajes enviados por el usuario.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `page` | `1` | Número de página. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

### Centros

#### `GET /centros/get-resumen-tablon-anuncios`

Resumen del tablón de anuncios del centro.

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /centros/get-tablon-anuncios`

Tablón de anuncios completo del centro.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |
| `centercode` | `00000000` | Código del centro. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /centros/servicios-centro`

Servicios del centro.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /centros/calendario-centro`

Calendario escolar del centro.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `centercode` | `00000000` | Código del centro. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "section": "septiembre 2026",
      "items": [
        {
          "dateholiday": "03/09/2026",
          "description": "Primer día lectivo:Primer Ciclo de Educación Infantil",
          "ambit": "Provincial"
        }
      ]
    },
    {
      "section": "octubre 2026",
      "items": [
        {
          "dateholiday": "12/10/2026",
          "description": "Día de la Hispanidad",
          "ambit": "Andalucía"
        }
      ]
    },
    {
      "section": "diciembre 2026",
      "items": [
        {
          "dateholiday": "07/12/2026",
          "description": "Lunes siguiente al Día de la Constitución",
          "ambit": "Andalucía"
        },
        {
          "dateholiday": "23/12/2026",
          "description": "Vacaciones de Navidad - provincial",
          "ambit": "Localidad"
        }
      ]
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave del `SeccionDatosDTO`**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `section` | string | Mes/año. |
| `items[]` | array | Lista de festivos/eventos del mes. |
| `items[].dateholiday` | string | Fecha `dd/MM/yyyy`. |
| `items[].description` | string | Descripción. |
| `items[].ambit` | string | Ámbito: `Andalucía`, `Provincial`, `Localidad`, etc. |

---

#### `GET /centros/get-imagen-centro`

Devuelve la imagen del centro como recurso binario.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `code` | `00000000` | Código del centro. |

**Respuesta**: imagen (JPEG/PNG). No devuelve el envelope JSON.

---

### Seguimiento

#### `GET /seguimiento/imagen-alumno`

Devuelve la foto del alumno como recurso binario.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta**: imagen (JPEG/PNG). No devuelve el envelope JSON.

---

#### `GET /seguimiento/datos-alumno`

Datos personales del alumno y de sus tutores legales.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "section": "Datos Personales",
      "items": [
        ["Nombre", "Apellido1 Apellido2, Nombre"],
        ["Edad", "21 años"],
        ["Fecha de nacimiento", "01/01/2005"],
        ["Sexo", "Hombre"]
      ]
    },
    {
      "section": "Tutores Legales",
      "items": [
        ["Tutor 1", ""],
        ["Teléfono", ""],
        ["Tfno. urgencias", ""],
        ["Correo electrónico", ""],
        ["Tutor 2", ""],
        ["Teléfono", ""],
        ["Tfno. urgencias", ""],
        ["Correo electrónico", ""]
      ]
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

`items` es un array de arrays de 2 elementos `[clave, valor]`.

---

#### `GET /seguimiento/get-notas-convocatorias`

Lista de convocatorias de evaluación.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "course": "1ª Evaluación",
      "isFinished": false,
      "dateStart": "01/10/2026",
      "dateFinish": "22/12/2026",
      "isActive": true,
      "idCenter": 9000001,
      "subjects": []
    },
    {
      "course": "2ª Evaluación",
      "isFinished": false,
      "dateStart": "01/10/2026",
      "dateFinish": "15/04/2027",
      "isActive": true,
      "idCenter": 9000002,
      "subjects": []
    },
    {
      "course": "Final 1ªFP",
      "isFinished": true,
      "dateStart": "03/06/2027",
      "dateFinish": "21/06/2027",
      "isActive": false,
      "idCenter": 9000003,
      "subjects": []
    },
    {
      "course": "Evaluación Inicial",
      "isFinished": false,
      "dateStart": "07/10/2026",
      "dateFinish": "08/10/2026",
      "isActive": false,
      "idCenter": 9000004,
      "subjects": []
    }
  ],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

**Campos clave del `ConvocatoriaDTO`**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `course` | string | Nombre de la convocatoria. |
| `isFinished` | boolean | Si ya ha finalizado. |
| `dateStart` | string | Fecha de inicio `dd/MM/yyyy`. |
| `dateFinish` | string | Fecha de fin `dd/MM/yyyy`. |
| `isActive` | boolean | Si está activa ahora. |
| `idCenter` | int | ID interno de la convocatoria. |
| `subjects[]` | array | Asignaturas con notas. |

---

#### `GET /seguimiento/observaciones`

Observaciones del profesorado/tutor sobre el alumno.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 200**

```json
{
  "success": true,
  "message": "OK",
  "data": [],
  "errors": [],
  "meta": {"version": "1.0"}
}
```

Estructura de `data[]` no verificada.

---

#### `GET /seguimiento/get-aut-act`

Autorizaciones de actividades extraescolares.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 401**

```json
{
  "success": false,
  "message": "No tiene acceso a esta operación.",
  "data": null,
  "errors": [{
    "status": 401,
    "detail": "No tiene acceso a esta operación.",
    "locatorId": "#XXXXXXXXXX"
  }]
}
```

---

#### `GET /seguimiento/get-per-aut-rec-alu`

Autorizaciones de recogida del alumno.

**Query params**

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `enrollment` | `12345678` | ID de matrícula. |

**Respuesta 404** (HTML, no JSON)

```
HTTP 404 Not Found
Content-Type: text/html
Server: BigIP

<!DOCTYPE html>
<html>...Error 404 - Página no encontrada...</html>
```

---

## Modelos de datos

### Envelope

```kotlin
data class PasenResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
    val errors: List<String>,
)
```

### Token OAuth

```kotlin
data class PasenToken(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
)
```

### Usuario / matrícula

```kotlin
data class PasenSessionInfo(
    val fullname: String,
    val typeProfile: String,
    val enrollments: List<PasenEnrollment>,
)

data class PasenEnrollment(
    val enrollment: String,
    val unity: String,
    val description: String,
    val course: String,
    val tutor: String,
    val centerCode: String,
)
```

### Horario

```kotlin
data class PasenScheduleSlot(
    val day: String,
    val hoursection: String,
    val section: Int,
    val subject: String,
    val prof: String,
    val codesection: Int,
)
```

### Tutoría

```kotlin
data class PasenTutoria(
    val delegate: String,
    val unity: String,
    val centerName: String,
    val tutorName: String,
    val activitys: List<PasenTutoriaActivity>,
    val teachingTeam: List<PasenTeachingTeam>,
)

data class PasenTutoriaActivity(
    val day: String,
    val activity: String,
    val time_started: String,
    val time_end: String,
)

data class PasenTeachingTeam(
    val shortName: String,
    val description: String,
    val teachers: String,
)
```

### Agenda / Calendario

```kotlin
data class PasenAgendaEvent(
    val initdate: String,
    val initHour: String,
    val endDate: String,
    val endHour: String,
    val title: String,
    val description: String,
    val category: String,
    val priority: Int,
    val annotation: String,
    val enrollment: String,
    val manual: Boolean,
)

data class PasenCalendarSection(
    val section: String,
    val items: List<PasenCalendarItem>,
)

data class PasenCalendarItem(
    val dateholiday: String,
    val description: String,
    val ambit: String,
)
```

### Comunicaciones

```kotlin
data class PasenComunicacion(
    val id: Long,
    val id_message: Long,
    val type: String,
    val has_adj: Boolean,
    val isdrawingpin: Boolean,
    val isread: Boolean,
    val subject: String,
    val body_message: String,
    val date: String,
    val sender: String,
    val enrollment: Int,
    val student: String,
    val course: String,
    val isFavorite: Boolean,
)
```

### Datos personales

```kotlin
data class PasenDataSection(
    val section: String,
    val items: List<List<String>>,
)
```

### Convocatorias

```kotlin
data class PasenConvocatoria(
    val course: String,
    val isFinished: Boolean,
    val dateStart: String,
    val dateFinish: String,
    val isActive: Boolean,
    val idCenter: Int,
    val subjects: List<Any>,
)
```

---

## Notificaciones push (FCM)

```
GET http://www.juntadeandalucia.es/educacion/centronotificaciones/gcm/checkRegistroIdUsrApl.ced
    ?id=<FCM_TOKEN>
    &aplicacion=1
    &usuarioaplicacion=<USUARIO>
```

**Respuesta 200**

```json
{"texto": "Dispositivo registrado", "resultado": "TRUE"}
```

**Cookies**

```
Set-Cookie: JSESSIONID_CN=<id>; Path=/educacion/centronotificaciones; HttpOnly
```

Esta llamada no lleva `Authorization: Bearer`.

---

## Errores conocidos

| Código HTTP | Causa | Comportamiento |
|-------------|-------|----------------|
| `401` | Token caducado o inválido. | Reautenticar. |
| `401` | Permisos insuficientes para ese recurso. | Recurso no disponible para el perfil. |
| `403` | `User-Agent` no reconocido o IP bloqueada. | Usar `User-Agent` que empiece por `iPasen/<versión>`. |
| `404` | Endpoint inexistente o cambiado de ruta. | Devuelve HTML de la Junta, no JSON. |
| `500` | Error interno del servidor. | Error interno. |
| `success: false` (HTTP 200) | Error de negocio. | Leer `message`. |

