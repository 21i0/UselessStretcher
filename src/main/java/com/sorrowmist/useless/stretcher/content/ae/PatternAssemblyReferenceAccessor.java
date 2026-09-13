package com.sorrowmist.useless.stretcher.content.ae;

import java.util.UUID;

/** Implemented on the upstream assembly block entity to reuse its external item-data record. */
public interface PatternAssemblyReferenceAccessor {
    UUID uselessStretcher$getPatternAssemblyReference();

    void uselessStretcher$setPatternAssemblyReference(UUID reference);
}
