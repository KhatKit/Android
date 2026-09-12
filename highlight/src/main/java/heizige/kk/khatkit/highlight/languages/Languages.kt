package heizige.kk.khatkit.highlight.languages

import heizige.kk.khatkit.highlight.core.Language
import heizige.kk.khatkit.highlight.languages.bash.bash
import heizige.kk.khatkit.highlight.languages.c.c
import heizige.kk.khatkit.highlight.languages.cmake.cmake
import heizige.kk.khatkit.highlight.languages.cpp.cpp
import heizige.kk.khatkit.highlight.languages.csharp.csharp
import heizige.kk.khatkit.highlight.languages.css.css
import heizige.kk.khatkit.highlight.languages.dart.dart
import heizige.kk.khatkit.highlight.languages.diff.diff
import heizige.kk.khatkit.highlight.languages.dockerfile.dockerfile
import heizige.kk.khatkit.highlight.languages.go.go
import heizige.kk.khatkit.highlight.languages.glsl.glsl
import heizige.kk.khatkit.highlight.languages.ini.ini
import heizige.kk.khatkit.highlight.languages.java.java
import heizige.kk.khatkit.highlight.languages.javascript.javascript
import heizige.kk.khatkit.highlight.languages.json.json
import heizige.kk.khatkit.highlight.languages.kotlin.kotlin
import heizige.kk.khatkit.highlight.languages.latex.latex
import heizige.kk.khatkit.highlight.languages.lua.lua
import heizige.kk.khatkit.highlight.languages.markdown.markdown
import heizige.kk.khatkit.highlight.languages.php.php
import heizige.kk.khatkit.highlight.languages.powershell.powershell
import heizige.kk.khatkit.highlight.languages.properties.properties
import heizige.kk.khatkit.highlight.languages.python.python
import heizige.kk.khatkit.highlight.languages.rust.rust
import heizige.kk.khatkit.highlight.languages.ruby.ruby
import heizige.kk.khatkit.highlight.languages.sql.sql
import heizige.kk.khatkit.highlight.languages.swift.swift
import heizige.kk.khatkit.highlight.languages.typescript.typescript
import heizige.kk.khatkit.highlight.languages.xml.xml
import heizige.kk.khatkit.highlight.languages.yaml.yaml

/**
 * Every grammar bundled with the highlighter.
 *
 * Each entry builds a fresh mode tree: compilation mutates modes in place, mirroring `highlight.js`.
 */
internal fun builtinLanguages(): List<Language> = listOf(
    json(),
    ini(),
    cmake(),
    go(),
    glsl(),
    yaml(),
    bash(),
    dockerfile(),
    javascript(),
    typescript(),
    xml(),
    css(),
    dart(),
    java(),
    kotlin(),
    latex(),
    lua(),
    powershell(),
    properties(),
    python(),
    c(),
    cpp(),
    csharp(),
    sql(),
    diff(),
    markdown(),
    rust(),
    ruby(),
    php(),
    swift(),
)
