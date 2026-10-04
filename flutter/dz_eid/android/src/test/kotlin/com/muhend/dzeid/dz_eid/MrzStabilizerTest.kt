package com.muhend.dzeid.dz_eid

import com.muhend.dzeid.android.MrzStabilizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * Tests unitaires de la partie Kotlin. Exécution : `./gradlew testDebugUnitTest` dans `example/android/`.
 */
internal class MrzStabilizerTest {

    private val a = "IDDZA123456789<<<<<<<<<<<<<<<"
    private val b = "IDDZA987654321<<<<<<<<<<<<<<<"

    @Test
    fun confirmeApresDeuxLecturesIdentiques() {
        val s = MrzStabilizer()
        assertNull(s.offer(a))
        assertEquals(a, s.offer(a))
    }

    @Test
    fun uneLectureDifferenteRelanceLeCompte() {
        val s = MrzStabilizer()
        assertNull(s.offer(a))
        assertNull(s.offer(b))
        assertNull(s.offer(a))
        assertEquals(a, s.offer(a))
    }

    @Test
    fun lesImagesSansMrzNInterrompentPasLaConfirmation() {
        val s = MrzStabilizer()
        assertNull(s.offer(a))
        assertNull(s.offer(null))
        assertEquals(a, s.offer(a))
    }

    @Test
    fun resetEfface() {
        val s = MrzStabilizer()
        s.offer(a)
        s.reset()
        assertNull(s.offer(a))
    }
}
