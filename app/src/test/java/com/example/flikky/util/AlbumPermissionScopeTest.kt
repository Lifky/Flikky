package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPermissionScopeTest {
    @Test fun `manage all files alone is full access`() { assertEquals(AlbumAccess.Full,albumAccess(true,false,false,false)) }
    @Test fun `both media reads are full access`() { assertEquals(AlbumAccess.Full,albumAccess(false,true,true,false)) }
    @Test fun `only images is partial`() { assertEquals(AlbumAccess.Partial,albumAccess(false,true,false,false)) }
    @Test fun `only video is partial`() { assertEquals(AlbumAccess.Partial,albumAccess(false,false,true,false)) }
    @Test fun `user selected only is partial`() { assertEquals(AlbumAccess.Partial,albumAccess(false,false,false,true)) }
    @Test fun `nothing granted is no access`() { assertEquals(AlbumAccess.None,albumAccess(false,false,false,false)) }
    @Test fun `manage all files wins over every weaker combination`() { listOf(true,false).forEach { b -> listOf(true,false).forEach { c -> listOf(true,false).forEach { d -> assertEquals(AlbumAccess.Full,albumAccess(true,b,c,d)) } } } }
    @Test fun `full media grant wins over the user selected flag`() { assertEquals(AlbumAccess.Full,albumAccess(false,true,true,true)) }
    @Test fun `every one of the sixteen combinations resolves to something`() { var n=0; listOf(true,false).forEach { a->listOf(true,false).forEach{b->listOf(true,false).forEach{c->listOf(true,false).forEach{d->albumAccess(a,b,c,d);n++}}}}; assertEquals(16,n) }
}
