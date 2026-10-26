# 🗺️ Roadmap - Intelligent Relation Analyzer

## Estado Actual ✅
- Análisis estructural de clases Java (campos, colecciones, tipos)
- Análisis semántico (similitud de nombres Jaro-Winkler, Levenshtein)
- Sistema ML con regresión logística
- Memoria persistente (JSON) para aprendizaje
- UI JavaFX con feedback Aceptar/Rechazar
- Exportación de anotaciones JPA
- Indicador de estado ML

---

## 🎯 Próximas Mejoras (Prioridad Alta)

### 1. ✅ Análisis de Repositorios Git
**Valor:** Analizar proyectos externos sin copiar archivos manualmente
- ✅ Clonar repos desde URL (botón "🌍 Git Clone")
- ✅ Detectar automáticamente src/main/java
- ✅ Ignorar carpetas (test, resources, target, build)

### 2. ✅ Detección de Relaciones Bidireccionales
**Valor:** Identificar automáticamente el lado "mappedBy"
- ✅ Detecta si A↔B es bidireccional
- ✅ Identifica owner (🔑) vs inverse (🔗)
- ✅ Genera código JPA correcto con mappedBy, cascade, orphanRemoval
- ✅ Columna "Bidir" en la tabla con iconos

### 3. ✅ Soporte para Anotaciones Existentes
**Valor:** Leer @OneToMany, @ManyToOne ya existentes en el código
- ✅ Escanea @OneToOne, @OneToMany, @ManyToOne, @ManyToMany
- ✅ Soporta javax.persistence y jakarta.persistence
- ✅ Detecta mappedBy existente
- ✅ Marca relaciones ya anotadas (fondo verde claro)
- ✅ Detecta inconsistencias (tipo diferente al sugerido)
- ✅ Muestra resumen en log

### 4. ✅ Generación de Código Real
**Valor:** Modificar las clases Java directamente
- ✅ Botón "⚡ Aplicar Código" con diálogo de preview
- ✅ Inserta anotaciones JPA en los archivos .java
- ✅ Agrega imports jakarta.persistence.* automáticamente
- ✅ Crea backups (.bak) antes de modificar
- ✅ Re-analiza después de aplicar cambios

---

## 🔧 Mejoras de UX (Prioridad Media)

### 5. ✅ Filtros y Búsqueda en Tabla
- ✅ Filtrar por tipo de relación
- ✅ Filtrar por nivel de confianza (>80%, >50%, etc.)
- ✅ Filtrar por fuente (ML, Estructural, Semántico)
- ✅ Búsqueda por nombre de clase

### 6. ✅ Visualización de Grafo
- ✅ Botón "📊 Grafo" abre ventana con diagrama
- ✅ Nodos arrastrables (drag & drop)
- ✅ Colores por tipo de relación (1:1, 1:N, N:1, N:M)
- ✅ Flechas con etiquetas de tipo
- ✅ Tooltips con detalles de cada relación
- ✅ Leyenda de colores
- ✅ Soporte para relaciones bidireccionales

### 7. ✅ Historial de Análisis
- ✅ Guardar análisis anteriores (snapshots)
- ✅ Comparar cambios entre versiones
- ✅ Revertir decisiones
- **Spec:** `.kiro/specs/analysis-history/`

### 8. ✅ Bulk Actions
- ✅ Aceptar/Rechazar múltiples relaciones a la vez
- ✅ Selección con checkboxes
- ✅ "Aceptar todas con confianza >90%"
- ✅ Atajos de teclado (Ctrl+A, Ctrl+Enter, etc.)
- **Spec:** `.kiro/specs/bulk-actions/`

---

## 🧠 Mejoras de ML (Prioridad Media)

### 9. 📋 Features Adicionales para ML
- Cardinalidad inferida (List vs Set vs single)
- Nombres de campos (no solo clases)
- Patrones de naming (xxxId, xxxList, etc.)
- Herencia entre clases
- **Spec:** `.kiro/specs/enhanced-ml-features/`

### 10. Modelo Más Sofisticado
- Gradient Boosting o Random Forest
- Cross-validation para evaluar precisión
- Mostrar métricas (accuracy, precision, recall)

### 11. Transfer Learning
- Importar/exportar modelos entrenados
- Compartir aprendizaje entre proyectos similares

---

## 🌐 Fábrica de backends

### M-1.1 ✅ API REST
- ✅ `org.example.api.RelationsApiServer` (Javalin) en `127.0.0.1:8090`, contrato 3.2 de `docs/FABRICA.md`
- ✅ `POST /relations/suggest`, `POST /relations/feedback` (reentreno en segundo plano), `GET /health`
- ✅ Jar ejecutable `-api.jar`; arranque y variables en el README

---

## 🔌 Integraciones (Prioridad Baja)

### 12. Plugin para IDEs
- IntelliJ IDEA plugin
- VS Code extension
- Análisis en tiempo real mientras codificas

### 13. Soporte Multi-ORM
- Hibernate (actual)
- Spring Data JPA
- MyBatis
- Jakarta Persistence

### 14. Análisis de Bases de Datos
- Conectar a DB existente
- Inferir relaciones desde foreign keys
- Comparar modelo Java vs esquema DB

### 15. 📋 Documentación Automática
- Generar diagrama ER
- Exportar a PlantUML/Mermaid
- README con descripción del modelo
- **Spec:** `.kiro/specs/diagram-export/`

---

## 🐛 Deuda Técnica

### 16. 📋 Tests de Integración
- Tests E2E de la UI
- Tests del flujo completo de análisis
- Mocks para MemoryService
- **Spec:** `.kiro/specs/integration-tests/`

### 17. 📋 Logging Estructurado
- Reemplazar System.out por Logger
- Niveles de log configurables
- Archivo de log rotativo
- **Spec:** `.kiro/specs/structured-logging/`

### 18. 📋 Configuración Externa
- Archivo de configuración (YAML/properties)
- Umbrales de confianza configurables
- Pesos de ML ajustables
- **Spec:** `.kiro/specs/external-configuration/`

### 19. 📋 Internacionalización
- Soporte para inglés/español
- Archivos de recursos i18n
- **Spec:** `.kiro/specs/internationalization/`

---

## 📊 Métricas de Éxito

| Métrica | Actual | Objetivo |
|---------|--------|----------|
| Precisión ML | ~70% | >85% |
| Tiempo análisis 200 clases (JMH, en memoria) | 0,16 s (antes 3,7 s) | <2s |
| Decisiones para ML activo | 5 | 3 |
| Tipos de relación soportados | 4 | 6+ |

---

## 🚀 Quick Wins ✅ COMPLETADOS

1. ✅ **Filtro por confianza** - Slider 0-100%
2. ✅ **Contador de relaciones por tipo** - Stats en tiempo real
3. ✅ **Tooltip con detalles en tabla** - En cada fila
4. ✅ **Recordar última carpeta** - Carga automática
5. ✅ **Atajos de teclado** - Enter=Aceptar, Delete=Rechazar, F5=Analizar
6. ✅ **Filtro por tipo y fuente** - ComboBoxes
7. ✅ **Búsqueda por nombre** - Campo de texto

---

## Contribuir

¿Tienes ideas? Abre un issue o PR en el repositorio.
