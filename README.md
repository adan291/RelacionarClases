🧠 Detection Relations – Sistema de Análisis y Aprendizaje de Relaciones entre Entidades
📋 Descripción

Esta aplicación JavaFX analiza automáticamente clases Java para detectar relaciones entre entidades (OneToOne, OneToMany, etc.), combinando dos tipos de análisis:

🧩 Análisis estructural: detecta relaciones reales a partir del código fuente (campos, colecciones, tipos).

🤖 Análisis semántico (IA): sugiere relaciones basadas en similitud de nombres y memoria de aprendizajes previos.

El sistema aprende de las decisiones del usuario (aceptar o rechazar relaciones) y las guarda en un archivo JSON (memoria_relaciones.json) para mejorar futuras predicciones.

🚀 Ejecución del Proyecto
1️⃣ Requisitos

Java 21

Maven 3.9+

Conexión inicial a internet (solo para descargar dependencias una vez)

2️⃣ Compilar y ejecutar

Desde la raíz del proyecto, ejecuta:

mvn clean javafx:run


Si usas IntelliJ IDEA o VS Code, también puedes ejecutar directamente la clase:

org.example.App

🧱 Estructura del Proyecto
src/main/java/org/example/
│
├── App.java                          # Punto de entrada (JavaFX)
│
├── analysis/
│   ├── RelationDetector.java         # Detección estructural de relaciones
│   └── SemanticSimilarity.java       # Comparación semántica y léxica de nombres
│
├── logic/
│   └── SemanticRelationDetector.java # IA que infiere relaciones con confianza
│
├── controller/
│   └── RelationAnalysisOrchestrator.java # Orquestador general
│
├── model/
│   └── RelacionDetectada.java        # Modelo de relación detectada
│
├── service/
│   ├── MemoryService.java            # Memoria persistente (JSON)
│   └── DetectorRelacionesService.java# Interfaz con IA
│
└── ui/
└── RelationSuggestionView.java   # Interfaz visual (aceptar/rechazar relaciones)

🧠 Cómo Funciona

Analiza las clases dentro de la carpeta src/main/java/org/example/ejemplos/

Detecta campos, colecciones (List<>, Set<>) y tipos.

Usa RelationDetector para encontrar relaciones directas.

Inferencia semántica

SemanticRelationDetector compara nombres de clases y campos usando:

🧩 Distancia de Levenshtein

🔤 Similitud Jaro–Winkler

🧠 Diccionario de sinónimos

Confianza y aprendizaje

Cada relación obtiene una puntuación de confianza (0–1).

Si el usuario acepta una relación, la IA la recordará y reforzará futuras inferencias similares.

Si se rechaza, el modelo reducirá la confianza en casos parecidos.

🪄 Interfaz Interactiva

Al ejecutar la aplicación se abre una ventana con:

📋 Lista de relaciones sugeridas.

Verde → alta confianza

Amarillo → media

Rojo → baja

✅ Aceptar → guarda la decisión como “aceptada”.

❌ Rechazar → guarda la decisión como “rechazada”.

🧠 Ver memoria → muestra el historial de decisiones guardadas.

🗑️ Borrar memoria → limpia el archivo JSON.

💾 Memoria Persistente

Todas las decisiones se almacenan en un archivo local:

memoria_relaciones.json


Ejemplo de contenido:

{
"Oficina -> Ciudad : ManyToOne": "aceptada",
"Empleado -> Empresa : ManyToOne": "rechazada"
}


La memoria se carga automáticamente al iniciar el programa y se actualiza al aceptar o rechazar relaciones.

🧩 Entrenamiento Manual

Añade tus propias clases en src/main/java/org/example/ejemplos/.

Ejecuta la aplicación.

Acepta o rechaza relaciones sugeridas.

Repite — la IA ajustará automáticamente las sugerencias según la experiencia previa.

🔮 Próximas Fases (Roadmap)
Etapa	Descripción	Estado
🧱 1	Base estructural + similitud léxica	✅ Completo
🧠 2	Memoria persistente y aprendizaje básico	✅ Completo
💡 3	Entrenamiento visual + confianza por color	✅ Completo
🌍 4	Integrar análisis de código de repositorios externos (Git)	⏳ Próximo
🧬 5	Modelo ML ligero para inferencia real basada en ejemplos	🔜 Planeado
⚙️ Créditos Técnicos

JavaFX – UI moderna en Java

JavaParser – Análisis de código fuente

Apache Commons Text – Similitud Jaro–Winkler

Gson – Persistencia en JSON

💬 Consejos

Si cambias el nombre de carpetas o clases, actualiza el path en App.java.

Si algo “aprendido” no te convence, pulsa 🗑️ Borrar memoria para empezar de cero.

La IA mejora cuanto más la uses: más relaciones = más precisión.


## API REST de relaciones

Expone el análisis como servicio HTTP según el contrato 3.2 de `docs/FABRICA.md` del workspace (`http://127.0.0.1:8090/api/v1`). No necesita JavaFX.

Arranque:

```powershell
mvn -B package -DskipTests
java -jar target\detection-relations-1.0-SNAPSHOT-api.jar
```

También se puede lanzar la clase `org.example.api.RelationsApiServer` desde el IDE.

| Variable | Por defecto | Uso |
|---|---|---|
| `RELATIONS_PORT` | `8090` | Puerto. Siempre escucha en `127.0.0.1` |
| `RELATIONS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Orígenes CORS, separados por comas |
| `RELATIONS_DATA_DIR` | `~/.relacionar-clases` | Carpeta de `memoria_relaciones.json` y `ml_model.json` (nunca el directorio de trabajo) |
| `RELATIONS_MIN_DECISIONS` | `10` | Decisiones necesarias para activar ML |
| `RELATIONS_RETRAIN_DEBOUNCE_MS` | `2000` | Espera tras el último feedback antes de reentrenar |

Rutas:
- `GET /health` → `{"status":"ok","mlAvailable":false}`.
- `POST /relations/suggest` con `entities` y `options`. Las `relations` que llegan se convierten en campos para el análisis y no se devuelven como sugerencia (tampoco otro tipo para el mismo par en el mismo sentido). Además del análisis de clases se aplican dos reglas: clave ajena por nombre (`customerId: Long` → `ManyToOne Customer`) y lado inverso de cada relación existente (si `User` tiene `OneToMany Post`, se sugiere `Post ManyToOne User`, con el nombre sacado de un campo `xxxId` sin entidad, como `authorId` → `author`). Con `useMemory`, una decisión aceptada exacta aparece con `origin: MEMORY` y una rechazada exacta no se sugiere.
- `POST /relations/feedback` con un objeto o una lista `{source, target, type, decision: accepted|rejected}` → `202`. Guarda en la memoria y reentrena en segundo plano con debounce.

Límites: 500 entidades, 200 campos (y 200 relaciones) por entidad y 1 MB de cuerpo; si se superan, `413`. Los errores de validación son `400`, un `Content-Type` que no sea JSON es `415`, y todos siguen RFC 7807 (`application/problem+json` con `type`, `title`, `status`, `detail`).

## 📝 Sistema de Logging

La aplicación utiliza un sistema de logging estructurado basado en `java.util.logging`.

**Ubicación de logs:**
- Consola: mensajes INFO y superiores
- Archivos: `logs/app_0.log`, `logs/app_1.log`, etc.

**Configuración:**
- Archivo de configuración: `logging.properties`
- Rotación automática: 5MB por archivo, máximo 10 archivos
- Formato: `[YYYY-MM-DD HH:mm:ss.SSS] [LEVEL] [Class.method] - Message`

**Niveles de log por paquete:**
- `org.example.service` - INFO (operaciones de negocio)
- `org.example.ml` - INFO (comportamiento del modelo ML)
- `org.example.ui` - INFO (interacciones de usuario)
- `org.example.analyzer` - INFO (operaciones de análisis)

**Cambiar nivel en runtime:**
```java
import org.example.logging.LoggingConfig;
import java.util.logging.Level;

// Cambiar nivel de un paquete específico
LoggingConfig.setLevel("org.example.ml", Level.FINE);

// Cambiar nivel global
LoggingConfig.setGlobalLevel(Level.ALL);
```
