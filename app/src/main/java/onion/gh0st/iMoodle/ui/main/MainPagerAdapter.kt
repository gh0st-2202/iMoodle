package onion.gh0st.iMoodle.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import onion.gh0st.iMoodle.ui.calendar.CalendarFragment
import onion.gh0st.iMoodle.ui.courses.list.CoursesFragment
import onion.gh0st.iMoodle.ui.profile.ProfileFragment
import onion.gh0st.iMoodle.ui.schedule.ScheduleFragment

/**
 * Adaptador del carrusel de pantallas principales (ViewPager2).
 *
 * ## Qué hace
 * Conecta el deslizamiento horizontal con las cuatro pantallas
 * principales de la aplicación. Cuando el usuario desliza, este
 * adaptador crea y muestra el Fragment correspondiente.
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    /**
     * Indica cuántas pantallas hay en el carrusel.
     *
     * Su propósito es decirle al sistema que hay 4 pestañas.
     */
    override fun getItemCount(): Int = 4

    /**
     * Devuelve la pantalla que corresponde a una posición.
     *
     * Su propósito es elegir qué Fragment mostrar dependiendo de
     * qué pestaña haya seleccionada el usuario.
     */
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> CoursesFragment()
        1 -> CalendarFragment()
        2 -> ScheduleFragment()
        3 -> ProfileFragment()
        else -> throw IllegalArgumentException("Posición no válida: $position")
    }
}