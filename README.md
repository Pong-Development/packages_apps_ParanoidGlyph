# Setting up Paranoid Glyph


To build Paranoid Glyph you have to build the respective package in your device tree.
```bash
    # Paranoid Glyph
    PRODUCT_SOONG_NAMESPACES += packages/apps/ParanoidGlyph
    PRODUCT_PACKAGES += \ 
        ParanoidGlyphPhone1 # Phone (1)
        ParanoidGlyphPhone2 # Phone (2)
        ParanoidGlyphPhone2a # Phone (2a) and (2a) Plus
        ParanoidGlyphPhone3a # Phone (3a) and (3a) Pro
```
