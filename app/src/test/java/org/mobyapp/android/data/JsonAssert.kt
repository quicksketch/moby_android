package org.mobyapp.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue

/**
 * Asserts two JSON values have the same contents. JSONObject's equals compares
 * identity, and the newer similar() isn't in Android's org.json, which the
 * tests compile against.
 */
fun assertJsonEquals(expected: Any?, actual: Any?) =
    assertTrue("Expected $expected, got $actual", jsonEquals(expected, actual))

private fun jsonEquals(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject ->
        a.keys().asSequence().toSet() == b.keys().asSequence().toSet() &&
            a.keys().asSequence().all { jsonEquals(a.get(it), b.get(it)) }
    a is JSONArray && b is JSONArray ->
        a.length() == b.length() && (0 until a.length()).all { jsonEquals(a.get(it), b.get(it)) }
    a is Number && b is Number -> a.toDouble() == b.toDouble()
    else -> a == b
}
