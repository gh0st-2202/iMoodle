# iMoodle

> Cliente Android **no oficial** para consultar **Moodle Centros** e **iPasen** con una sola sesión, pensado para alumnado de la Junta de Andalucía.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2028%2B-green?logo=android)](https://developer.android.com)
[![Versión](https://img.shields.io/badge/versi%C3%B3n-2.1-blue)](./app/build.gradle.kts)
[![Estado](https://img.shields.io/badge/estado-funcional-brightgreen)]()

---

## 📱 ¿Qué es iMoodle?

iMoodle es una aplicación Android **personal y no oficial** que unifica en una sola app los dos servicios que más usa el alumnado andaluz:

- **Moodle Centros**: cursos, tareas, foros, archivos y calendario.
- **iPasen**: horario semanal y datos personales del alumno.

Una sola sesión de la Junta de Andalucía (IdEA / CAS) autentica contra ambos servicios. La app está pensada para **uso personal**, no se distribuye en Play Store y no está afiliada a la Junta de Andalucía.

> ⚠️ **Aviso**: la app usa las APIs de Moodle e iPasen en nombre del propio usuario. Algunas de esas APIs no están documentadas oficialmente (Pasen) y podrían cambiar sin previo aviso. Úsala bajo tu responsabilidad.

---

## ✨ Funcionalidades

### Moodle
- 🔐 Login con credenciales de la Junta (CAS + Moodle).
- 📚 Listado de cursos con progreso y portada (o patrón generado si no hay).
- 📂 Detalle de curso con secciones, módulos y archivos descargables.
- 📝 Detalle de tarea con fechas, estado de entrega, nota y adjuntos.
- 💬 Foros con discusiones y mensajes en HTML (enlaces clicables).
- 📅 Agenda de los próximos 30 días con eventos del calendario.

### iPasen
- 🗓️ **Horario semanal** con colores únicos por asignatura y filtro por día.
- 👤 **Perfil del alumno** enriquecido con datos personales (centro, fecha de nacimiento, edad, sexo).

### General
- 🔔 **Notificaciones locales** de tareas próximas a entregar.
- 🔄 **Refresco silencioso de tokens**: la sesión se renueva sin pedir la contraseña.
- 💾 **Offline-first**: cursos, tareas y calendario se cachean en local.
- ⚡ **Caché por usuario** de horario y perfil: solo se llama a Pasen la primera vez.
- 🌓 **Tema claro/oscuro** (Material 3 DayNight).

---

## 📸 Capturas

> Descubre la interfaz por ti mismo. No te lo voy a dar todo en bandeja.

---

## 🏗️ Arquitectura

El proyecto sigue **Clean Architecture** con **MVVM** en la capa de UI.

```mermaid
flowchart TD
    UI["<b>UI (Android)</b><br/>Fragments · Activities · ViewModels · Adapters"]
    DOMAIN["<b>domain/</b><br/>model · repository (contratos) · usecase · rules"]
    DATA["<b>data/</b><br/>Room · caches · sesión"]
    API["<b>api/</b><br/>CAS · Moodle · Pasen"]

    UI -->|"contratos de domain"| DOMAIN
    DOMAIN -.->|"implementaciones"| DATA
    DOMAIN -.->|"implementaciones"| API
```

- **`domain/`**: contratos de negocio (modelos, repositorios, casos de uso). No depende de Android.
- **`data/`**: implementaciones de persistencia local (Room, `SharedPreferences`) y gestión de sesión.
- **`api/`**: clientes HTTP de los proveedores (CAS, Moodle, Pasen). Un paquete por proveedor.
- **`ui/`**: pantallas, ViewModels, adapters y utilidades de UI.
- **`inyectores/`**: módulos de Hilt.
- **`notifications/`**: programación de recordatorios con `AlarmManager`.
- **`core/`**: utilidades transversales.

---

## 🛠️ Stack técnico

| Categoría | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.x |
| UI | Material 3 · ViewBinding · Navigation |
| Arquitectura | MVVM + Clean Architecture |
| Inyección de dependencias | Hilt |
| Persistencia local | Room + DataStore/SharedPreferences cifradas |
| Red | OkHttp 4.12 |
| Concurrencia | Coroutines + Flow |
| Imágenes | Coil (con `SvgDecoder` para patrones generados) |
| Seguridad | Jetpack Security (`EncryptedSharedPreferences` + Android Keystore) |
| Tests | JUnit 4 · MockK · Turbine |
| Build | Gradle 9.6 · AGP 9.4.1 |

---

## ✅ Requisitos

- **Android Studio** reciente (Ladybug o superior recomendado).
- **JDK 17**.
- Dispositivo o emulador con **Android 9 (API 28)** o superior.
- **Cuenta de la Junta de Andalucía (IdEA)** válida: la app no crea cuentas, solo usa las del usuario.

---

## 🚀 Compilar y ejecutar

```bash
# 1. Clona el repositorio
git clone https://github.com/<tu-usuario>/iMoodle.git
cd iMoodle

# 2. Compila
./gradlew :app:assembleDebug

# 3. Ejecuta los tests unitarios
./gradlew :app:testDebugUnitTest
```

También puedes abrir el proyecto en Android Studio y usar **Run ▶︎** como con cualquier proyecto Android.

> La app no se publica en Play Store. El APK generado es para uso personal.

---

## 🔒 Privacidad y seguridad

La app maneja datos sensibles, así que conviene explicar cómo se tratan:

- **Credenciales de la Junta**: se guardan **cifradas** en el dispositivo con `EncryptedSharedPreferences` + **Android Keystore**. Se borran al hacer logout o si la Junta las rechaza.
- **Tokens de Moodle**: se guardan cifrados. Son revocables y de corta duración.
- **Cachés de horario y perfil**: se indexan por `username` de la Junta, en `SharedPreferences`. No contienen credenciales.
- **Datos de cursos, tareas y calendario**: se guardan en Room y se borran al hacer logout.
- **Sin telemetría**: la app no envía datos a ningún servidor propio. Todas las peticiones van directamente a los servidores de la Junta.
- **Sin publicidad**: no hay SDKs de terceros que rastreen al usuario.

Si detectas una vulnerabilidad, abre un *issue* describiéndola sin incluir datos reales.

---

## 📁 Estructura del proyecto (resumen)

```
app/src/main/java/onion/gh0st/iMoodle/
├── api/              Clientes de CAS, Moodle y Pasen
│   ├── cas/
│   ├── moodle/
│   └── pasen/
├── core/             Utilidades transversales (logging, await, defaults…)
├── data/             Persistencia y gestión de sesión
│   ├── auth/         Almacenes cifrados (tokens, credenciales)
│   ├── local/        Room (DAOs, entidades, migraciones)
│   ├── schedule/     Caché de horario
│   └── session/      Sesión, caches de usuario y perfil
├── domain/           Contratos de negocio
│   ├── model/
│   ├── repository/
│   ├── rules/
│   └── usecase/
├── inyectores/       Módulos de Hilt
├── notifications/    Recordatorios con AlarmManager
└── ui/               Pantallas, ViewModels y adapters
```

---

## 🤝 Contribuir

Este es un proyecto personal, pero los *issues* y *pull requests* son bienvenidos si:

- 🐛 Encuentras un bug reproducible.
- 💡 Propones una mejora de accesibilidad, rendimiento o UX.
- 📝 Detectas un error en la documentación.

Antes de abrir un PR:

1. Abre un *issue* describiendo qué vas a cambiar y por qué.
2. Respeta el estilo del proyecto: **KDoc completo en clases y métodos**, nombres de variables descriptivos, sin comentarios dentro de los métodos.
3. Ejecuta `./gradlew :app:testDebugUnitTest` antes de enviar.

---

## ⚠️ Disclaimer

Este proyecto **no está afiliado, asociado, autorizado ni respaldado** por la Junta de Andalucía.

- **Moodle Centros** e **iPasen** son servicios oficiales de la Junta de Andalucía.
- La app usa la **API pública de Moodle** (documentada oficialmente) y la **API privada de iPasen** (obtenida mediante ingeniería inversa del tráfico de la app oficial).
- El uso de estas APIs desde aplicaciones de terceros **puede infringir los términos de servicio** de los respectivos servicios. Úsala bajo tu responsabilidad y solo con tu propia cuenta.
- La Junta puede cambiar, cerrar o bloquear estas APIs en cualquier momento. Si eso ocurre, la app podría dejar de funcionar hasta que se adapte.

Este proyecto tiene **fines educativos y personales**: está desarrollado por un estudiante como proyecto de aprendizaje.

---

## 📄 Licencia

Este proyecto se distribuye con fines educativos y de uso personal.

Si quieres reutilizar parte del código en tus propios proyectos, eres libre de hacerlo citando la fuente. Si el repositorio no incluye un archivo `LICENSE`, considera el código bajo la licencia [MIT](https://opensource.org/licenses/MIT) para uso no comercial.

Las marcas **Moodle**, **Moodle Centros**, **iPasen** y **Pasen** pertenecen a sus respectivos titulares.

---

## 🙏 Créditos

- **Moodle** por su API pública de Web Services.
- **Junit**, **MockK**, **Turbine**, **Hilt**, **Room**, **OkHttp**, **Coil** y **Jetpack** por las librerías que hacen posible el proyecto.
- La comunidad de desarrolladores Android por la documentación y los ejemplos públicos.

---

<p align="center">
  Hecho con curiosidad, demasiadas horas y amor a la ingeniería inversa por un estudiante de 2º DAM.
</p>
