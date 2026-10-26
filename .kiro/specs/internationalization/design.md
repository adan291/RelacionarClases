# Design: Internationalization (i18n)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    i18n System                               │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  I18n (Singleton)                     │   │
│  │                                                       │   │
│  │  - currentLocale: Locale                             │   │
│  │  - bundle: ResourceBundle                            │   │
│  │  - listeners: List<LocaleChangeListener>             │   │
│  │                                                       │   │
│  │  + get(key): String                                  │   │
│  │  + get(key, args...): String                         │   │
│  │  + setLocale(locale): void                           │   │
│  │  + formatDate(date): String                          │   │
│  │  + formatNumber(number): String                      │   │
│  └──────────────────────────────────────────────────────┘   │
│                            │                                 │
│         ┌──────────────────┼──────────────────┐             │
│         ▼                  ▼                  ▼             │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐       │
│  │ messages    │   │ messages_en │   │ messages_XX │       │
│  │ .properties │   │ .properties │   │ .properties │       │
│  │ (Spanish)   │   │ (English)   │   │ (Future)    │       │
│  └─────────────┘   └─────────────┘   └─────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

## Components

### 1. I18n Singleton

```java
public class I18n {
    private static I18n instance;
    private Locale currentLocale;
    private ResourceBundle bundle;
    private List<LocaleChangeListener> listeners;
    
    public static I18n getInstance();
    public void initialize();
    public void setLocale(Locale locale);
    public Locale getLocale();
    
    // String retrieval
    public String get(String key);
    public String get(String key, Object... args);
    
    // Formatting
    public String formatDate(LocalDateTime date);
    public String formatNumber(double number);
    public String formatPercent(double value);
    
    // Listener support
    public void addLocaleChangeListener(LocaleChangeListener listener);
    public void removeLocaleChangeListener(LocaleChangeListener listener);
}

@FunctionalInterface
public interface LocaleChangeListener {
    void onLocaleChanged(Locale newLocale);
}
```

### 2. Resource Bundle Keys Structure

```properties
# messages.properties (Spanish - Default)

# === UI Labels ===
ui.button.analyze=Analizar
ui.button.accept=✓ Aceptar
ui.button.reject=✗ Rechazar
ui.button.export=📄 Exportar
ui.button.history=📜 Historial
ui.button.save=💾 Guardar
ui.button.graph=📊 Grafo
ui.button.apply.code=⚡ Aplicar Código
ui.button.git.clone=🌍 Git Clone

# === Table Headers ===
ui.table.source=Origen
ui.table.target=Destino
ui.table.type=Tipo
ui.table.confidence=Confianza
ui.table.source.prediction=Fuente
ui.table.bidirectional=Bidir
ui.table.actions=Acciones

# === Dialogs ===
ui.dialog.confirm.title=Confirmar
ui.dialog.confirm.bulk.accept=¿Aceptar {0} relaciones?
ui.dialog.confirm.bulk.reject=¿Rechazar {0} relaciones?
ui.dialog.error.title=Error
ui.dialog.success.title=Éxito

# === Status Messages ===
ui.status.analyzing=Analizando...
ui.status.complete=Análisis completado: {0} relaciones detectadas
ui.status.ml.active=ML activo ({0} decisiones)
ui.status.ml.inactive=ML inactivo (necesita {0} decisiones)

# === Filters ===
ui.filter.confidence=Confianza mínima
ui.filter.type=Tipo
ui.filter.source=Fuente
ui.filter.search=Buscar...

# === ML ===
ml.source.ml=ML
ml.source.structural=Estructural
ml.source.semantic=Semántico

# === Relation Types ===
relation.onetoone=OneToOne
relation.onetomany=OneToMany
relation.manytoone=ManyToOne
relation.manytomany=ManyToMany

# === Log Messages ===
log.memory.loaded=Memoria cargada: {0} relaciones
log.memory.saved=Memoria guardada
log.analysis.start=Iniciando análisis de {0}
log.analysis.complete=Análisis completado
log.model.trained=Modelo entrenado con {0} ejemplos
```

### 3. English Translation

```properties
# messages_en.properties (English)

# === UI Labels ===
ui.button.analyze=Analyze
ui.button.accept=✓ Accept
ui.button.reject=✗ Reject
ui.button.export=📄 Export
ui.button.history=📜 History
ui.button.save=💾 Save
ui.button.graph=📊 Graph
ui.button.apply.code=⚡ Apply Code
ui.button.git.clone=🌍 Git Clone

# === Table Headers ===
ui.table.source=Source
ui.table.target=Target
ui.table.type=Type
ui.table.confidence=Confidence
ui.table.source.prediction=Source
ui.table.bidirectional=Bidir
ui.table.actions=Actions

# ... (rest of translations)
```

### 4. Usage Pattern

```java
// Before
button.setText("Analizar");

// After
button.setText(I18n.getInstance().get("ui.button.analyze"));

// With parameters
String msg = I18n.getInstance().get("ui.status.complete", relationCount);
// Spanish: "Análisis completado: 42 relaciones detectadas"
// English: "Analysis complete: 42 relations detected"
```

### 5. Language Selector UI

```
┌─────────────────────────────────────┐
│ Idioma/Language: [Español ▼]        │
│                   ├─ Español        │
│                   └─ English        │
└─────────────────────────────────────┘
```

## Correctness Properties

### Property 1: All Keys Have Values
Every key used in code should exist in all resource bundles.
- **Validates**: Requirement 1.1, 1.2

### Property 2: Parameterized Messages Work
Messages with {0}, {1} placeholders should format correctly.
- **Validates**: Requirement 1.4

### Property 3: Locale Change Propagates
Changing locale should notify all listeners.
- **Validates**: Requirement 4.4

### Property 4: Fallback Works
Missing English key should fall back to Spanish.
- **Validates**: Non-functional: Fallback
