package com.sorrowmist.useless.stretcher.content.catalog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogSyncGateTest {
    @Test void waitsForBothPacketsRuntimeAndQuietWindow() {
        var g = new CatalogSyncGate(5); Object m = new Object(), c = new Object();
        g.recipes(m, c, 0);
        assertFalse(g.ready(m,c,20,true));
        g.tags(21);
        assertFalse(g.ready(m,c,25,true));
        assertFalse(g.ready(m,c,26,false));
        assertTrue(g.ready(m,c,26,true));
    }
    @Test void duplicateSyncNotificationsExtendBarrierWithoutClaimingContentEquality() {
        var g = new CatalogSyncGate(5); Object m = new Object(), c = new Object();
        g.recipes(m,c,0); g.tags(1); long revision = g.revision();
        g.recipes(m,c,3); g.tags(4);
        assertTrue(g.revision()>revision);
        assertFalse(g.ready(m,c,8,true));
        assertTrue(g.ready(m,c,9,true));
    }
    @Test void sameManagerDimensionChangePreservesPacketsButNewConnectionDoesNot() {
        var g = new CatalogSyncGate(5); Object m = new Object(), c = new Object();
        g.recipes(m,c,0); g.tags(1);
        assertFalse(g.observe(m,c,20));
        assertTrue(g.ready(m,c,20,true));
        Object next = new Object(); assertTrue(g.observe(m,next,21));
        assertFalse(g.ready(m,next,40,true));
    }
    @Test void firstLevelCanAttachPacketsOfTheSamePreLevelManager() {
        var g = new CatalogSyncGate(5); Object m = new Object(), c = new Object();
        g.recipes(m,null,0); g.tags(1);
        g.observe(m,c,2);
        assertTrue(g.ready(m,c,7,true));
        assertFalse(g.ready(new Object(),c,7,true));
    }
    @Test void disconnectAndNewManagerNeverReuseOldSessionReadiness() {
        var g = new CatalogSyncGate(5); Object m = new Object(), c = new Object();
        g.recipes(m,c,0); g.tags(1); g.reset(2);
        assertFalse(g.ready(m,c,100,true));
        g.observe(new Object(),c,3);
        assertFalse(g.ready(m,c,100,true));
    }
    @Test void versionGateIsExact() {
        assertTrue(CatalogPatchSupport.verifiedVersion("1.21.1-2.4.6.2"));
        assertTrue(CatalogPatchSupport.verifiedVersion("1.21.1-2.4.6"));
        assertFalse(CatalogPatchSupport.verifiedVersion("1.21.1-2.4.7"));
        assertFalse(CatalogPatchSupport.verifiedVersion(null));
    }
}
