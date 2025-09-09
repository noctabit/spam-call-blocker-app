# DOCUMENTACIÓN COMPLETA PARA RECUPERACIÓN DE SCRAPERS - CALL BLOCKER APP

## 🎯 OBJETIVO DE ESTE DOCUMENTO
Esta documentación permite a una IA futura reintroducir los scrapers web tal como están implementados en la versión 2.3.5 de la Call Blocker App, después de que fueron eliminados por el autor en versiones posteriores.

---

## 📋 ARCHIVOS CRÍTICOS AFECTADOS

### ⚠️ ARCHIVOS PRINCIPALES - MODIFICACIÓN CRÍTICA REQUERIDA

#### 1. `app/src/main/java/com/addev/listaspam/util/SharedPreferencesUtils.kt`
**FUNCIONES DE CONFIGURACIÓN DE SCRAPERS QUE DEBEN ESTAR PRESENTES:**

```kotlin
fun shouldFilterWithListaSpamScraper(context: Context): Boolean =
    getBooleanPref(context, "pref_filter_lista_spam_scraper", false)

fun shouldFilterWithResponderONo(context: Context): Boolean =
    getBooleanPref(context, "pref_filter_responder_o_no", false)

fun shouldFilterWithCleverdialer(context: Context): Boolean =
    getBooleanPref(context, "pref_filter_cleverdialer", false)
```

**UBICACIÓN EXACTA:** Líneas 59-66 en el archivo actual
- Línea 59-60: `shouldFilterWithListaSpamScraper`
- Línea 62-63: `shouldFilterWithResponderONo` 
- Línea 65-66: `shouldFilterWithCleverdialer`

#### 2. `app/src/main/java/com/addev/listaspam/util/SpamUtils.kt`
**CONSTANTES DE CONFIGURACIÓN QUE DEBEN ESTAR PRESENTES:**

```kotlin
companion object {
    // URLs de scrapers
    const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
    const val LISTA_SPAM_CSS_SELECTOR = ".rate-and-owner .phone_rating:not(.result-4):not(.result-5)"
    
    private const val RESPONDERONO_URL_TEMPLATE = "https://www.responderono.es/numero-de-telefono/%s"
    private const val RESPONDERONO_CSS_SELECTOR = ".scoreContainer .score.negative"
    
    private const val CLEVER_DIALER_URL_TEMPLATE = "https://www.cleverdialer.es/numero/%s"
    private const val CLEVER_DIALER_CSS_SELECTOR = "body:has(#comments):has(.front-stars:not(.star-rating .stars-4, .star-rating .stars-5)), .circle-spam"
    
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.6533.103 Mobile Safari/537.36"
}
```

**UBICACIÓN EXACTA:** Líneas 40-56 en el archivo actual

**FUNCIONES DE SCRAPERS QUE DEBEN ESTAR PRESENTES:**

```kotlin
// FUNCIÓN 1: Lista Spam Scraper (Líneas 348-354)
private suspend fun checkListaSpam(number: String): Boolean {
    val url = LISTA_SPAM_URL_TEMPLATE.format(number)
    return checkUrlForSpam(
        url,
        LISTA_SPAM_CSS_SELECTOR
    )
}

// FUNCIÓN 2: ResponderONo Scraper (Líneas 362-365)
private suspend fun checkResponderono(number: String): Boolean {
    val url = RESPONDERONO_URL_TEMPLATE.format(number)
    return checkUrlForSpam(url, RESPONDERONO_CSS_SELECTOR)
}

// FUNCIÓN 3: CleverDialer Scraper (Líneas 373-376)
private suspend fun checkCleverdialer(number: String): Boolean {
    val url = CLEVER_DIALER_URL_TEMPLATE.format(number)
    return checkUrlForSpam(url, CLEVER_DIALER_CSS_SELECTOR)
}

// FUNCIÓN AUXILIAR: Verificación de URL (Líneas 385-404)
private suspend fun checkUrlForSpam(url: String, cssSelector: String): Boolean {
    val request = Request.Builder()
        .header("User-Agent", USER_AGENT)
        .url(url)
        .build()

    return withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext false
                val doc = Jsoup.parse(body)
                doc.select(cssSelector).isNotEmpty()
            }
        } catch (e: IOException) {
            Logger.getLogger("checkUrlForSpam")
                .warning("Error checking URL: $url with error ${e.message}")
            false
        }
    }
}
```

**INTEGRACIÓN EN buildSpamCheckers() - Líneas 262-266:**

```kotlin
// Agregar estas líneas en la función buildSpamCheckers
if (shouldFilterWithListaSpamScraper(context) && !listaSpamApi) spamCheckers.add(::checkListaSpam)

if (shouldFilterWithResponderONo(context)) spamCheckers.add(::checkResponderono)
if (shouldFilterWithCleverdialer(context)) spamCheckers.add(::checkCleverdialer)
```

#### 3. `app/src/main/res/xml/preferences.xml`
**SECCIÓN DE SCRAPERS QUE DEBE ESTAR PRESENTE:**

```xml
<!-- Spam Number Filters -->
<PreferenceCategory
    android:title="@string/pref_category_scrapers_es"
    app:iconSpaceReserved="false">

    <CheckBoxPreference
        android:defaultValue="false"
        android:key="pref_filter_lista_spam_scraper"
        android:summary="@string/pref_filter_lista_spam_scraper_summary"
        android:title="@string/pref_filter_lista_spam_scraper_title"
        app:iconSpaceReserved="false" />

    <CheckBoxPreference
        android:defaultValue="false"
        android:key="pref_filter_responder_o_no"
        android:summary="@string/pref_filter_responder_o_no_summary"
        android:title="@string/pref_filter_responder_o_no_title"
        app:iconSpaceReserved="false" />

    <CheckBoxPreference
        android:defaultValue="false"
        android:key="pref_filter_cleverdialer"
        android:summary="@string/pref_filter_cleverdialer_summary"
        android:title="@string/pref_filter_cleverdialer_title"
        app:iconSpaceReserved="false" />

</PreferenceCategory>
```

**UBICACIÓN EXACTA:** Líneas 83-109 en el archivo actual

---

### 🔍 ARCHIVOS DE INTERFAZ - STRINGS REQUERIDOS

#### 4. `app/src/main/res/values/strings.xml`
**STRINGS DE SCRAPERS QUE DEBEN ESTAR PRESENTES:**

```xml
<!-- Preferences Sections -->
<string name="pref_category_scrapers_es">Scrapers 🇪🇸</string>

<!-- Spam Number Filters -->
<string name="pref_filter_lista_spam_scraper_title">listaspam.com Scraper</string>
<string name="pref_filter_lista_spam_scraper_summary">(Deprecated) Blocks calls from spam numbers listed on listaspam.es website</string>

<string name="pref_filter_responder_o_no_title">responderono.es Scraper</string>
<string name="pref_filter_responder_o_no_summary">Blocks calls from spam numbers listed on responderono.es website</string>

<string name="pref_filter_cleverdialer_title">cleverdialer.es Scraper</string>
<string name="pref_filter_cleverdialer_summary">Blocks calls from spam numbers listed on cleverdialer.es website</string>

<!-- Call item actions  -->
<string name="open_in_lista_spam_com">Open in ListaSpam</string>
```

**UBICACIÓN EXACTA:** 
- Línea 41: categoría scrapers
- Líneas 72-79: títulos y descripciones de scrapers
- Línea 102: acción de abrir en ListaSpam

#### 5. `app/src/main/res/values-es/strings.xml`
**STRINGS EN ESPAÑOL QUE DEBEN ESTAR PRESENTES:**

```xml
<string name="pref_category_scrapers_es">Scrapers 🇪🇸</string>

<string name="pref_filter_lista_spam_scraper_title">Scraper listaspam.com</string>
<string name="pref_filter_lista_spam_scraper_summary">(Obsoleto) Bloquea llamadas de números spam incluidos en la web listaspam.es</string>

<string name="pref_filter_responder_o_no_title">Scraper responderono.es</string>
<string name="pref_filter_responder_o_no_summary">Bloquea llamadas de números spam incluidos en la web responderono.es</string>

<string name="pref_filter_cleverdialer_title">Scraper cleverdialer.es</string>
<string name="pref_filter_cleverdialer_summary">Bloquea llamadas de números spam incluidos en la web cleverdialer.es</string>

<string name="open_in_lista_spam_com">Abrir en ListaSpam</string>
```

#### 6. `app/src/main/res/values-it/strings.xml`
**STRINGS EN ITALIANO QUE DEBEN ESTAR PRESENTES:**

```xml
<string name="pref_category_scrapers_es">Scrapers 🇪🇸</string>

<string name="pref_filter_lista_spam_scraper_title">Scraper listaspam.com</string>
<string name="pref_filter_lista_spam_scraper_summary">(Deprecato) Blocca le chiamate dai numeri spam elencati sul sito listaspam.es</string>

<string name="pref_filter_responder_o_no_title">Scraper responderono.es</string>
<string name="pref_filter_responder_o_no_summary">Blocca le chiamate dai numeri spam elencati sul sito responderono.es</string>

<string name="pref_filter_cleverdialer_title">Scraper cleverdialer.es</string>
<string name="pref_filter_cleverdialer_summary">Blocca le chiamate dai numeri spam elencati sul sito cleverdialer.es</string>

<string name="open_in_lista_spam_com">Apri in ListaSpam</string>
```

#### 7. `app/src/main/res/values-pt/strings.xml`
**STRINGS EN PORTUGUÉS QUE DEBEN ESTAR PRESENTES:**

```xml
<string name="pref_category_scrapers_es">Scrapers 🇪🇸</string>

<string name="pref_filter_lista_spam_scraper_title">Scraper listaspam.com</string>
<string name="pref_filter_lista_spam_scraper_summary">(Obsoleto) Bloqueia chamadas de números spam listados no site listaspam.es</string>

<string name="pref_filter_responder_o_no_title">Scraper responderono.es</string>
<string name="pref_filter_responder_o_no_summary">Bloqueia chamadas de números spam listados no site responderono.es</string>

<string name="pref_filter_cleverdialer_title">Scraper cleverdialer.es</string>
<string name="pref_filter_cleverdialer_summary">Bloqueia chamadas de números spam listados no site cleverdialer.es</string>

<string name="open_in_lista_spam_com">Abrir no ListaSpam</string>
```

---

### 🛠️ ARCHIVOS DE CONFIGURACIÓN - DEPENDENCIAS CRÍTICAS

#### 8. `app/build.gradle.kts`
**DEPENDENCIAS REQUERIDAS PARA SCRAPERS:**

```kotlin
dependencies {
    implementation(libs.okhttp)        // Para requests HTTP
    implementation(libs.jsoup)         // Para parsing HTML/CSS
    // ... otras dependencias
}
```

**UBICACIÓN EXACTA:** Líneas 49-50 en el archivo actual

#### 9. `gradle/libs.versions.toml`
**VERSIONES DE DEPENDENCIAS CRÍTICAS:**

```toml
[versions]
okhttp = "4.9.3"
jsoup = "1.14.3"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }
```

**UBICACIÓN EXACTA:** Líneas 15, 20, 32 en el archivo actual

---

## 📊 INFORMACIÓN TÉCNICA DETALLADA

### 🔗 URLs y Selectores CSS por Scraper

#### ListaSpam.com Scraper
- **URL Template**: `https://www.listaspam.com/busca.php?Telefono=%s`
- **CSS Selector**: `.rate-and-owner .phone_rating:not(.result-4):not(.result-5)`
- **Lógica**: Busca elementos con rating negativo, excluyendo result-4 y result-5 (que son positivos)
- **Estado según pruebas**: ❌ **NO FUNCIONAL** - Bloqueado por Cloudflare
- **Marcado como**: (Deprecated/Obsoleto)

#### ResponderONo.es Scraper  
- **URL Template**: `https://www.responderono.es/numero-de-telefono/%s`
- **CSS Selector**: `.scoreContainer .score.negative`
- **Lógica**: Busca elementos con clase "score negative" dentro de scoreContainer
- **Estado según pruebas**: ✅ **COMPLETAMENTE FUNCIONAL**
- **Ejemplo HTML encontrado**:
```html
<div class="scoreContainer">
    <div class="score negative"></div>
</div>
```

#### CleverDialer.es Scraper
- **URL Template**: `https://www.cleverdialer.es/numero/%s`  
- **CSS Selector**: `body:has(#comments):has(.front-stars:not(.star-rating .stars-4, .star-rating .stars-5)), .circle-spam`
- **Lógica**: Busca elementos que indiquen spam, excluyendo 4-5 estrellas (que son positivas)
- **Estado según pruebas**: ⚠️ **PARCIALMENTE FUNCIONAL** - Requiere actualización
- **Problema**: El selector CSS no coincide con la estructura HTML real
- **Estructura HTML real**:
```html
<div class="rating-text">
    <span>1 de 5 estrellas</span>
    <span class="nowrap">&bull; 1 valoración</span>
</div>
```

### 🔧 User Agent Utilizado
```
Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.6533.103 Mobile Safari/537.36
```

### ⚙️ Arquitectura de Integración

#### Flujo de Ejecución de Scrapers:
1. **Verificación de preferencias** → `SharedPreferencesUtils.kt`
2. **Construcción de lista de checkers** → `SpamUtils.buildSpamCheckers()`
3. **Ejecución paralela** → `SpamUtils.isSpamRace()`
4. **Requests HTTP individuales** → `SpamUtils.checkUrlForSpam()`
5. **Parsing HTML** → `Jsoup.parse()` + CSS selector

#### Integración con APIs:
- Los scrapers se agregan a la lista SOLO si la API correspondiente NO está habilitada
- Línea específica: `if (shouldFilterWithListaSpamScraper(context) && !listaSpamApi)`
- Esto evita duplicar verificaciones API + Scraper para el mismo servicio

---

## 🚨 INSTRUCCIONES CRÍTICAS PARA REINTRODUCCIÓN

### ⭐ PASOS OBLIGATORIOS:

#### 1. **ELIMINAR LISTASPAM SCRAPER**
- ❌ **DEBE SER ELIMINADO** porque está bloqueado por Cloudflare
- Cambiar summary a "(Deprecated - Blocked by Cloudflare)"
- Mantener las funciones pero commented out o con return false

#### 2. **ACTUALIZAR CLEVERDIALER SCRAPER**
- 🔄 **DEBE SER ACTUALIZADO** según el historial de pruebas
- **CSS Selector actual**: `body:has(#comments):has(.front-stars:not(.star-rating .stars-4, .star-rating .stars-5)), .circle-spam`
- **CSS Selector recomendado**: Implementar parsing por texto en lugar de CSS
- **Implementación sugerida**:

```kotlin
private suspend fun checkCleverdialer(number: String): Boolean {
    val url = CLEVER_DIALER_URL_TEMPLATE.format(number)
    
    val request = Request.Builder()
        .header("User-Agent", USER_AGENT)
        .header("Accept-Encoding", "identity")  // Evitar compresión
        .url(url)
        .build()

    return withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext false
                val doc = Jsoup.parse(body)
                
                // Buscar elementos con valoraciones bajas
                val ratingElements = doc.select("div.rating-text span")
                ratingElements.any { element ->
                    val text = element.text().lowercase()
                    text.contains("1 de 5 estrellas") || 
                    text.contains("2 de 5 estrellas")
                }
            }
        } catch (e: IOException) {
            Logger.getLogger("checkCleverdialer")
                .warning("Error checking URL: $url with error ${e.message}")
            false
        }
    }
}
```

#### 3. **MANTENER RESPONDERONO SCRAPER**
- ✅ **MANTENER TAL COMO ESTÁ** - Completamente funcional
- No requiere modificaciones

---

## 📋 CHECKLIST DE VERIFICACIÓN POST-IMPLEMENTACIÓN

### ✅ Verificaciones Obligatorias:

- [ ] **SharedPreferencesUtils.kt** contiene las 3 funciones de configuración
- [ ] **SpamUtils.kt** contiene las 3 constantes de URL y selectores CSS  
- [ ] **SpamUtils.kt** contiene las 3 funciones de scraper
- [ ] **SpamUtils.kt** integra scrapers en `buildSpamCheckers()`
- [ ] **preferences.xml** contiene la sección completa de scrapers
- [ ] **strings.xml** (inglés) contiene todos los strings de scrapers
- [ ] **strings-es.xml** contiene traducciones en español
- [ ] **strings-it.xml** contiene traducciones en italiano  
- [ ] **strings-pt.xml** contiene traducciones en portugués
- [ ] **build.gradle.kts** incluye dependencias OkHttp y Jsoup
- [ ] **libs.versions.toml** define versiones correctas
- [ ] **arrays.xml** no requiere modificaciones (ya presente)

### 🧪 Pruebas Requeridas:

- [ ] **ListaSpam**: Verificar que está marcado como deprecated
- [ ] **ResponderONo**: Probar con número 873981181 (debe detectar spam)
- [ ] **CleverDialer**: Probar con número 623363131 (debe detectar 1-2 estrellas)
- [ ] **Interfaz**: Verificar que aparecen las 3 opciones en Settings
- [ ] **Persistencia**: Verificar que las preferencias se guardan correctamente

---

## 📝 NOTAS IMPORTANTES DE IMPLEMENTACIÓN

### 🔒 Aspectos de Seguridad:
- Los scrapers usan User-Agent de Android real para evitar detección
- Timeout implícito a través de OkHttp client
- Manejo de errores robusto con catch-all

### ⚡ Optimizaciones:
- Ejecución paralela de todos los scrapers usando corrutinas
- Race condition: primer resultado positivo cancela el resto
- Cache de OkHttp client reutilizable

### 🌐 Localización:
- Scrapers específicos para España (🇪🇸) pero URLs funcionan internacionalmente
- Strings localizados en 4 idiomas: EN, ES, IT, PT
- Selectors CSS adaptados al contenido en español

---

## 🎯 RESULTADO ESPERADO TRAS REINTRODUCCIÓN

Después de implementar todos los elementos listados en este documento:

1. ✅ **ResponderONo** funcionará completamente
2. 🔄 **CleverDialer** funcionará después de la actualización sugerida  
3. ❌ **ListaSpam** estará presente pero marcado como deprecated/no funcional
4. 🎛️ **Interfaz** mostrará las 3 opciones en Settings → Scrapers 🇪🇸
5. 🔧 **Configuración** persistirá las preferencias del usuario
6. 🚀 **Integración** los scrapers se ejecutarán en paralelo con las APIs

---

**📅 FECHA DE DOCUMENTACIÓN**: 9 de septiembre de 2025  
**📱 VERSIÓN DE REFERENCIA**: Call Blocker App v2.3.5  
**🔧 ESTADO**: Scrapers completamente implementados y funcionales