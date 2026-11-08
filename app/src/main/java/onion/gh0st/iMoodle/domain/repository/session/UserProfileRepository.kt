package onion.gh0st.iMoodle.domain.repository.session

import onion.gh0st.iMoodle.domain.model.session.User

/**
 * Contrato para enriquecer el `User` con datos personales de Pasen.
 *
 * ## Qué es "enriquecer"
 *
 * El `User` que viene de Moodle tiene la identidad básica (nombre,
 * email, avatar), pero le faltan datos personales que la Junta conoce
 * a través de Pasen: el centro, la fecha de nacimiento, la edad y el
 * sexo.
 *
 * Este contrato expone una operación para **rellenar** esos campos
 * en el `User`. No crea un modelo nuevo: modifica el mismo `User`,
 * porque es la misma persona.
 *
 * ## Qué NO hace
 *
 * - **No cambia la identidad**: el nombre, el email y el avatar no
 *   se tocan. Solo los campos de Pasen.
 * - **No devuelve un modelo distinto**: devuelve una copia del
 *   `User` con los campos rellenados.
 *
 * ## Estrategia cache-first
 *
 * La implementación real (`PasenUserProfileRepository`) es
 * cache-first en tres niveles:
 *
 * 1. Si el `User` ya trae los cuatro campos, no hace nada.
 * 2. Si hay caché en disco para el usuario, la usa.
 * 3. Si no, hace login a Pasen, pide los datos, los guarda en caché
 *    y los devuelve.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `PasenUserProfileRepository` (con caché).
 * - **Tests**: `FakeUserProfileRepository` (devuelve datos
 *   controlados).
 *
 * ## Quién la consume
 *
 * - `ProfileViewModel`.
 */
interface UserProfileRepository {

    /**
     * Devuelve una copia del `User` con los datos personales
     * rellenados.
     *
     * ## Qué rellena
     *
     * - `centerName`: nombre del centro.
     * - `birthDate`: fecha de nacimiento.
     * - `age`: edad.
     * - `gender`: sexo.
     *
     * ## Qué pasa si el proveedor no tiene algún dato
     *
     * Se rellena con `"No disponible"` para que la UI muestre un
     * mensaje claro en lugar de dejar el campo vacío.
     *
     * ## Qué pasa si el `User` ya trae los cuatro campos
     *
     * Se devuelve tal cual, sin hacer red. Esto es lo que permite
     * que las siguientes aperturas del perfil sean instantáneas.
     *
     * @param user Usuario a enriquecer.
     * @return Copia del usuario con los campos actualizados.
     */
    suspend fun enrich(user: User): User
}