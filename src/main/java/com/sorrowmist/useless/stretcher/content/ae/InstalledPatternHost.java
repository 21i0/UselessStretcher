package com.sorrowmist.useless.stretcher.content.ae;

/** A loaded provider which must republish when its referenced library changes. */
public interface InstalledPatternHost {
    void uselessStretcher$refreshInstalledPatterns();
}
