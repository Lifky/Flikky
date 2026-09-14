package com.example.flikky.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the read-time migration from the former combined favourites flag. */
class FavoriteBrowsingMigrationTest {

    private val favoriteBeta = booleanPreferencesKey("favorite_beta")
    private val favoriteBrowsing = booleanPreferencesKey("favorite_browsing")

    @Test
    fun `an upgrading user who had favourites on keeps the peer able to see them`() {
        val preferences = mutablePreferencesOf(favoriteBeta to true)
        assertEquals(
            true,
            resolveFavoriteBrowsing(preferences[favoriteBrowsing], preferences[favoriteBeta]),
        )
    }

    @Test
    fun `an upgrading user who had favourites off stays off`() {
        val preferences = mutablePreferencesOf(favoriteBeta to false)
        assertEquals(
            false,
            resolveFavoriteBrowsing(preferences[favoriteBrowsing], preferences[favoriteBeta]),
        )
    }

    @Test
    fun `a fresh install defaults to closed`() {
        val preferences = mutablePreferencesOf()
        assertEquals(
            false,
            resolveFavoriteBrowsing(preferences[favoriteBrowsing], preferences[favoriteBeta]),
        )
    }

    @Test
    fun `once the new key exists it wins over the old one`() {
        val preferences = mutablePreferencesOf(favoriteBeta to true, favoriteBrowsing to false)
        assertEquals(
            false,
            resolveFavoriteBrowsing(preferences[favoriteBrowsing], preferences[favoriteBeta]),
        )
    }

    @Test
    fun `the new key also wins when it is the more open value`() {
        val preferences = mutablePreferencesOf(favoriteBeta to false, favoriteBrowsing to true)
        assertEquals(
            true,
            resolveFavoriteBrowsing(preferences[favoriteBrowsing], preferences[favoriteBeta]),
        )
    }
}
